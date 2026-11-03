package ecommerce.aftersale.aspect;

import ecommerce.aftersale.annotation.AfterSaleLogRecord;
import ecommerce.aftersale.entity.AfterSale;
import ecommerce.aftersale.entity.AfterSaleLog;
import ecommerce.aftersale.enums.AfterSaleOperateTypeEnum;
import ecommerce.aftersale.mapper.AfterSaleLogMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.support.AfterSaleTransitions;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 售后变更流水切面。
 * <p>
 * 与线 1 的通用操作日志切面不同，这里是**同步、同事务**写入的：
 * 买家看到的进度时间线不能缺条，所以状态变了日志必须在；写失败就让事务一起回滚。
 * <p>
 * 顺序：{@code LOWEST_PRECEDENCE} —— 最内层。配合启动类的
 * {@code @EnableTransactionManagement(order = HIGHEST_PRECEDENCE)}，事务在最外层，
 * 日志切面在事务内层。
 * <p>
 * 注解只打在实现类上（AfterSaleServiceImpl / AdminAfterSaleServiceImpl），
 * 不打在接口上 —— JDK 动态代理下接口上的注解会静默失效。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AfterSaleLogAspect {

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleLogMapper afterSaleLogMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AfterSaleLogAspect(AfterSaleMapper afterSaleMapper, AfterSaleLogMapper afterSaleLogMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleLogMapper = afterSaleLogMapper;
    }

    @Around("@annotation(logRecord)")
    public Object around(ProceedingJoinPoint joinPoint, AfterSaleLogRecord logRecord) throws Throwable {
        // 执行前先取一次当前状态，作为变更前状态（create 时还查不到，跳过）
        Long paramId = toLong(evaluate(logRecord.afterSaleId(), joinPoint));
        AfterSale before = paramId == null ? null : afterSaleMapper.findById(paramId);

        Object result = joinPoint.proceed();

        writeLog(joinPoint, logRecord, paramId, before, result);
        return result;
    }

    /** 采集并写入一条变更流水 */
    private void writeLog(ProceedingJoinPoint joinPoint, AfterSaleLogRecord logRecord,
                          Long paramId, AfterSale before, Object result) {
        // 售后单 ID：优先用参数（create 取不到就用工单返回值）
        Long afterSaleId = paramId != null ? paramId : extractIdFromResult(result);
        if (afterSaleId == null) {
            log.warn("售后变更流水采集失败：取不到售后单 ID");
            return;
        }
        // 执行后的最新状态（同一事务内可见未提交的数据）
        AfterSale after = afterSaleMapper.findById(afterSaleId);
        String afterStatus = after != null ? after.getStatus() : statusFromResult(result);
        String beforeStatus = before != null ? before.getStatus() : null;
        String type = after != null ? after.getAfterSaleType()
                : (before != null ? before.getAfterSaleType() : null);

        AfterSaleOperateTypeEnum operateType = resolveOperateType(logRecord, type, beforeStatus, afterStatus);
        if (operateType == null) {
            log.warn("售后变更流水采集失败：推导不出操作类型 type={}, {} → {}", type, beforeStatus, afterStatus);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        AfterSaleLog log = AfterSaleLog.builder()
                .afterSaleId(afterSaleId)
                .userId(currentUserId())
                .userType(resolveUserType(logRecord.operator()))
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .operateType(operateType.getCode())
                .content(operateType.getLogContent())
                .merchantRemark(evaluateText(logRecord.merchantRemark(), joinPoint))
                .createdAt(now)
                .updatedAt(now)
                .build();
        // 同步写、同事务：写失败事务一起回滚，状态不会变了而日志没有
        afterSaleLogMapper.insert(log);
    }

    /**
     * 解析操作类型。
     * <p>
     * 固定操作类型（create / submitReturn）直接用注解值；
     * 审核场景按 (售后类型, 前状态, 后状态) 从状态机推导。
     */
    private AfterSaleOperateTypeEnum resolveOperateType(AfterSaleLogRecord logRecord, String type,
                                                        String beforeStatus, String afterStatus) {
        if (!logRecord.dynamic()) {
            return logRecord.operateType();
        }
        AfterSaleTransitions.Action action =
                AfterSaleTransitions.find(type, beforeStatus, afterStatus, true);
        return action == null ? null : action.operateType();
    }

    /** 从方法返回值（AfterSaleVO）里取售后单 ID 和状态 */
    private Long extractIdFromResult(Object result) {
        if (result instanceof AfterSaleVO vo) {
            return vo.getId();
        }
        return null;
    }

    private String statusFromResult(Object result) {
        if (result instanceof AfterSaleVO vo) {
            return vo.getStatus();
        }
        return null;
    }

    /** 当前操作人 ID */
    private Long currentUserId() {
        return UserContext.getUserId();
    }

    /** 操作人类型：1 买家 / 2 后台管理员 */
    private Integer resolveUserType(AfterSaleLogRecord.OperatorType operator) {
        if (operator == AfterSaleLogRecord.OperatorType.BUYER) {
            return AfterSaleLog.USER_TYPE_BUYER;
        }
        if (operator == AfterSaleLogRecord.OperatorType.ADMIN) {
            return AfterSaleLog.USER_TYPE_ADMIN;
        }
        // AUTO：按当前登录态判断
        return "admin".equals(UserContext.getUserType())
                ? AfterSaleLog.USER_TYPE_ADMIN
                : AfterSaleLog.USER_TYPE_BUYER;
    }

    /** 求值 SpEL，取不到返回 null */
    private Object evaluate(String expression, ProceedingJoinPoint joinPoint) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
            return expressionParser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.warn("售后流水表达式求值失败: {}, err={}", expression, e.getMessage());
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String evaluateText(String expression, ProceedingJoinPoint joinPoint) {
        Object value = evaluate(expression, joinPoint);
        return value == null ? null : value.toString();
    }
}
