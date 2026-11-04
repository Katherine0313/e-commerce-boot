package ecommerce.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务启动类（商品/分类/标签浏览，后续迁移库存/促销）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.product.mapper")
public class ProductServiceApplication {

    public static void main(String[] args) {
        // 关闭 Nacos 客户端自带的文件日志（nacos-logback.xml）：Windows 下滚动归档无法重命名
        // 正在写入的 config.log，会持续刷 WARN 且日志文件无限增长。关闭后跟随应用的 logback 输出到控制台。
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
