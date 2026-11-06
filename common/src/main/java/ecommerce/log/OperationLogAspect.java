package ecommerce.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 通用操作日志切面。
 * <p>
 * 职责边界：只做「采集 + 分发」。组装好一条 {@link OperationLogEntry} 交给
 * {@link OperationLogRecorder}，写库的动作（同步/异步、写哪张表）由实现决定。
 * <p>
 * 顺序：{@code LOWEST_PRECEDENCE} —— 最内层。配合服务上的
 * {@code @EnableTransactionManagement(order = HIGHEST_PRECEDENCE)}，事务在最外层，
 * 日志切面在事务内层，日志与业务数据同生共死。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OperationLogAspect {

    /** error_msg 字段最大长度 */
    private static final int MAX_ERROR_LENGTH = 512;

    private final OperationLogCollector collector;
    private final OperationLogRecorder recorder;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OperationLogAspect(OperationLogCollector collector, OperationLogRecorder recorder) {
        this.collector = collector;
        this.recorder = recorder;
    }

    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            errorMsg = collector.truncate(e.getMessage(), MAX_ERROR_LENGTH);
            throw e;
        } finally {
            try {
                recorder.save(buildEntry(joinPoint, operationLog, success, errorMsg,
                        System.currentTimeMillis() - start));
            } catch (Exception e) {
                // 兜底：切面里绝不能因为写日志失败而影响业务返回值
                log.warn("操作日志组装失败: module={}, action={}, err={}",
                        operationLog.module(), operationLog.action(), e.getMessage());
            } finally {
                // 变更文案是 ThreadLocal，必须在这里清掉，否则线程复用会串到下一个请求
                LogDetail.clear();
            }
        }
    }

    /** 采集组装一条日志 */
    private OperationLogEntry buildEntry(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                                         boolean success, String errorMsg, long costMs) {
        HttpServletRequest request = currentRequest();
        Long bizId = toLong(evaluate(operationLog.bizId(), joinPoint));
        String detail = toText(evaluate(operationLog.detail(), joinPoint));
        if (detail == null || detail.isBlank()) {
            // 注解里没写 detail 时，回退取 Service 里用 LogDetail 攒的变更文案
            detail = LogDetail.join();
        }

        return OperationLogEntry.builder()
                .userId(collector.currentUserId())
                .userType(collector.currentUserType())
                .username(collector.currentUsername())
                .module(operationLog.module())
                .action(operationLog.action())
                .bizId(bizId)
                .detail(detail)
                .method(request == null ? null : request.getMethod())
                .uri(request == null ? null : request.getRequestURI())
                .ip(collector.clientIp(request))
                .params(operationLog.recordParams() ? collector.paramsJson(joinPoint.getArgs()) : null)
                .success(success)
                .errorMsg(errorMsg)
                .costMs(costMs)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** 求值 SpEL 表达式，取不到就返回 null（不能因为表达式写错而让业务失败） */
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
            log.warn("操作日志表达式求值失败: {}, err={}", expression, e.getMessage());
            return null;
        }
    }

    /** SpEL 结果转 Long */
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

    /** SpEL 结果转字符串 */
    private String toText(Object value) {
        return value == null ? null : value.toString();
    }

    /** 取当前请求，非 Web 上下文（如定时任务）返回 null */
    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
