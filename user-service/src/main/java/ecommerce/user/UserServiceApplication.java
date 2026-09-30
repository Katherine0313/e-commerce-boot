package ecommerce.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 * <p>
 * scanBasePackages 显式指定 "ecommerce"：本类在 ecommerce.user 子包，默认扫描只覆盖 ecommerce.user.*，
 * 扫不到 common 模块里 ecommerce.common / ecommerce.configuration 这些兄弟包，所以必须扩大范围。
 * Maven 模块隔离了 classpath，扫 "ecommerce" 只会命中本服务依赖到的 common + 本服务自己的类，不会串到别的服务。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.user.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
