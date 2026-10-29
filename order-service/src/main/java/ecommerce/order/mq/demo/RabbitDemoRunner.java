package ecommerce.order.mq.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RabbitMQ demo 触发器（仅 dev）：服务启动完成后发一条测试消息。
 */
@Slf4j
@Component
@Profile("dev")
public class RabbitDemoRunner implements ApplicationRunner {

    private final RabbitDemoProducer producer;

    public RabbitDemoRunner(RabbitDemoProducer producer) {
        this.producer = producer;
    }

    @Override
    public void run(ApplicationArguments args) {
        String message = "hello rabbitmq " + LocalDateTime.now();
        producer.send(message);
        log.info("[RabbitMQ Demo] 已发送: {}", message);
    }
}
