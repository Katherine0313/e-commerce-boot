package ecommerce.aftersale;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 售后服务启动类（仅退款/退货退款/换货）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.aftersale.mapper")
public class AftersaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersaleServiceApplication.class, args);
    }
}
