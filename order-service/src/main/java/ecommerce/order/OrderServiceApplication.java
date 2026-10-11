package ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类（订单/购物车/待付款超时关闭）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.order.mapper")
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
