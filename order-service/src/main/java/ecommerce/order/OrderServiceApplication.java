package ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务启动类（占位骨架：订单/购物车/待付款超时关闭，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
