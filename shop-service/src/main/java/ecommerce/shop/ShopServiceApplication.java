package ecommerce.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 店铺服务启动类（占位骨架：多租户沙箱/模板店克隆/过期回收，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class ShopServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}
