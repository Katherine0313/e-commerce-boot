package ecommerce.log;

/**
 * 操作日志写入 SPI。
 * <p>
 * 切面只负责采集与分发，写库交给实现者，这样同步/异步、写哪张表都能换。
 * 默认实现是 {@link JdbcOperationLogRecorder}（异步写 operation_log）；
 * 某个服务要写自己的表时，实现本接口并注册成 {@code @Primary} 即可覆盖默认行为。
 */
public interface OperationLogRecorder {

    /**
     * 保存一条操作日志。
     * <p>
     * 实现方必须「吞掉所有异常」：审计日志写失败不能影响业务。
     */
    void save(OperationLogEntry entry);
}
