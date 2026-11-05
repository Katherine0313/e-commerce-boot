package ecommerce.auth.service;

import ecommerce.auth.mapper.AdminLoginLogMapper;
import ecommerce.auth.mapper.AdminOperationLogMapper;
import ecommerce.log.OperationLogEntry;
import ecommerce.log.OperationLogRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * 日志定时清理任务：每天 03:00 删除超过保留期的日志。
 * <p>
 * 保留 180 天：审计日志要留够时间排查，太多又会把表撑大；具体策略见方案文档 §4.7。
 * <p>
 * 为什么分批删：一次性 DELETE 几万行会长时间锁表，把线上查询接口拖住；
 * 每批 1000 行，删完一批释放一批。
 * <p>
 * 失败处理：任何异常都只记 warn。定时任务挂掉不影响业务，但也别让异常冒到调度线程。
 */
@Slf4j
@Component
public class LogCleanupTask {

    /** 日志保留天数 */
    private static final int RETAIN_DAYS = 180;
    /** 每批删除行数 */
    private static final int BATCH_SIZE = 1000;
    /** 单次任务最多删多少批（20 万行），剩下的留给下一次，避免任务跑太久 */
    private static final int MAX_BATCHES = 200;

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final AdminLoginLogMapper adminLoginLogMapper;
    private final OperationLogRecorder operationLogRecorder;

    public LogCleanupTask(AdminOperationLogMapper adminOperationLogMapper,
                          AdminLoginLogMapper adminLoginLogMapper,
                          OperationLogRecorder operationLogRecorder) {
        this.adminOperationLogMapper = adminOperationLogMapper;
        this.adminLoginLogMapper = adminLoginLogMapper;
        this.operationLogRecorder = operationLogRecorder;
    }

    /** 每天 03:00 清理一次 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void clean() {
        // 时间由 Java 算好再传给 SQL：DB 服务器时区是 UTC，NOW() 比本地时间早 8 小时
        LocalDateTime before = LocalDateTime.now().minusDays(RETAIN_DAYS);
        int operationDeleted = 0;
        int loginDeleted = 0;
        try {
            operationDeleted = deleteInBatches(() -> adminOperationLogMapper.deleteBefore(before, BATCH_SIZE));
            loginDeleted = deleteInBatches(() -> adminLoginLogMapper.deleteBefore(before, BATCH_SIZE));
            log.info("日志清理完成: 阈值 {}, 操作日志 {} 条, 登录日志 {} 条",
                    before, operationDeleted, loginDeleted);
            recordClean(operationDeleted, loginDeleted, true, null);
        } catch (Exception e) {
            // 定时任务绝不能把异常抛到调度线程，也绝不能把应用拖挂
            log.warn("日志清理失败: {}", e.getMessage(), e);
            recordClean(operationDeleted, loginDeleted, false, e.getMessage());
        }
    }

    /**
     * 分批删除直到删不满一批为止。
     *
     * @return 实际删除行数
     */
    private int deleteInBatches(IntSupplier deleter) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = deleter.getAsInt();
            total += deleted;
            if (deleted < BATCH_SIZE) {
                return total;
            }
        }
        // 到达批次上限说明还有积压，留给下一次任务继续删
        log.warn("日志清理达到批次上限 {}，剩余部分留到下次任务处理", MAX_BATCHES);
        return total;
    }

    /**
     * 记录一条清理操作日志。
     * <p>
     * 这里手动写而不是打 {@code @OperationLog} 注解：定时任务里没有请求上下文，
     * 注解切面未必能覆盖到自调用的场景，手写更可控（成功/失败都能如实记录）。
     */
    private void recordClean(int operationDeleted, int loginDeleted, boolean success, String errorMsg) {
        try {
            operationLogRecorder.save(OperationLogEntry.builder()
                    .module("system")
                    .action("LOG_CLEAN")
                    .detail("清理过期日志：操作日志 " + operationDeleted + " 条，登录日志 "
                            + loginDeleted + " 条")
                    .success(success)
                    .errorMsg(errorMsg)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("记录日志清理操作失败: {}", e.getMessage());
        }
    }
}
