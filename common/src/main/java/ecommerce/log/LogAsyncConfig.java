package ecommerce.log;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志写入线程池。
 * <p>
 * 单独一个池，不和业务线程池混用：日志写得慢不能拖慢业务，业务的异常高峰也不能把日志池占满。
 */
@Configuration
public class LogAsyncConfig {

    /**
     * 操作日志专用线程池。
     * <p>
     * 拒绝策略用 CallerRuns：池和队列都满了就让调用线程自己写，保证日志不丢
     * （代价是那一刻业务线程会阻塞一下，比丢审计日志可接受）。
     */
    @Bean("operationLogExecutor")
    public ThreadPoolTaskExecutor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("op-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 停机时把队列里的日志写完再退出，避免最后几条丢了
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
