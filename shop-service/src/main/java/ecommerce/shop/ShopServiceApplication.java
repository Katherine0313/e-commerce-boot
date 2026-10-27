package ecommerce.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 店铺服务启动类（端口 9002）：多租户沙箱——开沙箱（克隆模板店数据 + 签发 admin 令牌）、当前店铺信息。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.shop.mapper")
@EnableScheduling
public class ShopServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}
