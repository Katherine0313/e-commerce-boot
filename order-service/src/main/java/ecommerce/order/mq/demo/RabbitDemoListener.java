package ecommerce.order.mq.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ demo 消费者（仅 dev）：消费 demo.queue 并打印日志。
 */
@Slf4j
@Component
@Profile("dev")
public class RabbitDemoListener {

    @RabbitListener(queues = RabbitDemoConfig.QUEUE)
    public void onMessage(String message) {
        log.info("[RabbitMQ Demo] 收到消息: {}", message);
    }
}
