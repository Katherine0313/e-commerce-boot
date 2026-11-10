package ecommerce.monitor.sql;

import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 慢 SQL 采集装配。
 * <p>
 * 只在满足两个条件时生效：
 * 1) classpath 里有 MyBatis（file-service 没有，网关不引 common，都不会加载）；
 * 2) monitor.sql.enabled 不为 false。
 * <p>
 * 装配方式依赖 mybatis-spring-boot-starter 的自动配置：它会把容器里所有 Interceptor Bean
 * 注册进 MyBatis Configuration，各业务服务启动类不用改一行。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
@ConditionalOnProperty(prefix = "monitor.sql", name = "enabled", matchIfMissing = true)
public class SqlMonitorConfig {

    /**
     * 慢 SQL 写入线程池。
     * <p>
     * 拒绝策略是「丢弃 + 计数」而不是 CallerRuns：慢 SQL 只是采样数据，
     * 队列满时宁可少记一条，也不能让业务线程停下来等写库。
     */
    @Bean("sqlMonitorExecutor")
    public ThreadPoolTaskExecutor sqlMonitorExecutor(SqlMonitorMetrics metrics) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("sql-monitor-");
        executor.setRejectedExecutionHandler(countingDiscard(metrics));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    public SqlMonitorInterceptor sqlMonitorInterceptor(SqlMonitorProperties properties,
                                                       SqlMonitorRecorder recorder,
                                                       @Value("${spring.application.name:unknown}") String serviceName) {
        return new SqlMonitorInterceptor(properties, recorder, serviceName);
    }

    private RejectedExecutionHandler countingDiscard(SqlMonitorMetrics metrics) {
        return (Runnable task, ThreadPoolExecutor pool) -> metrics.incrementDropped();
    }
}
