package ecommerce.order.mq.demo;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ demo 生产者（仅 dev）：把消息发到 demo.exchange。
 */
@Component
@Profile("dev")
public class RabbitDemoProducer {

    private final RabbitTemplate rabbitTemplate;

    public RabbitDemoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(String message) {
        rabbitTemplate.convertAndSend(RabbitDemoConfig.EXCHANGE, RabbitDemoConfig.ROUTING_KEY, message);
    }
}
