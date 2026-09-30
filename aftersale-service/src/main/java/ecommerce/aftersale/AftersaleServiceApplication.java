package ecommerce.aftersale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 售后服务启动类（占位骨架：仅退款/退货退款/换货，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class AftersaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersaleServiceApplication.class, args);
    }
}
