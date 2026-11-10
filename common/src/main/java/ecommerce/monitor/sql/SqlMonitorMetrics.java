package ecommerce.monitor.sql;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢 SQL 采集计数，供总览页展示「采了多少 / 丢弃多少」。
 * <p>
 * 计数只在本实例内存里，服务重启归零；它衡量的是采集链路健康度，不需要持久化。
 */
@Component
public class SqlMonitorMetrics {

    /** 成功提交到写入线程池的条数 */
    private final AtomicLong recorded = new AtomicLong();

    /** 限流或线程池满而丢弃的条数 */
    private final AtomicLong dropped = new AtomicLong();

    public void incrementRecorded() {
        recorded.incrementAndGet();
    }

    public void incrementDropped() {
        dropped.incrementAndGet();
    }

    public long getRecorded() {
        return recorded.get();
    }

    public long getDropped() {
        return dropped.get();
    }
}
