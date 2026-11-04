package ecommerce.order;

import ecommerce.order.config.WeChatSubscribeProperties;
import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类（订单/购物车/待付款超时关闭）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.order.mapper")
@EnableScheduling
@EnableConfigurationProperties(WeChatSubscribeProperties.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        // 关闭 Nacos 客户端自带的文件日志（nacos-logback.xml）：Windows 下滚动归档无法重命名
        // 正在写入的 config.log，会持续刷 WARN 且日志文件无限增长。关闭后跟随应用的 logback 输出到控制台。
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
