package ecommerce.monitor.sql;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 采集配置。
 * <p>
 * 默认值全部写死在代码里：各服务 yml 不配置也能按「只记 300ms 以上、每分钟最多 60 条」工作；
 * 需要调整时再通过环境变量覆盖，避免漏配某个服务导致采集行为不一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "monitor.sql")
public class SqlMonitorProperties {

    /** 总开关，false 时拦截器不装配 */
    private boolean enabled = true;

    /** 超过该耗时才记录（毫秒） */
    private long slowThresholdMs = 300;

    /** 每个服务实例每分钟最多入库条数，超出的丢弃并计数 */
    private int maxPerMinute = 60;

    /** SQL 文本截断长度 */
    private int maxSqlLength = 2000;

    /** 记录保留天数，供 system-service 清理任务使用 */
    private int retainDays = 7;
}
