package ecommerce.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类：管理员登录/登出、JWT 签发。
 * <p>
 * scanBasePackages 显式指定 "ecommerce"：本类在 ecommerce.auth 子包，默认扫描只覆盖 ecommerce.auth.*，
 * 扫不到 common 模块里 ecommerce.common / ecommerce.configuration 这些兄弟包，所以必须扩大范围。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.auth.mapper")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
