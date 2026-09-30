package ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务启动类（占位骨架：商品/分类/库存/促销，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
