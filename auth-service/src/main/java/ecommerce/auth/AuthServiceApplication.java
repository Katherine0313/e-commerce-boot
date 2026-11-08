package ecommerce.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类：管理员登录/登出、JWT 签发。
 * <p>
 * scanBasePackages 显式指定 "ecommerce"：本类在 ecommerce.auth 子包，默认扫描只覆盖 ecommerce.auth.*，
 * 扫不到 common 模块里 ecommerce.common / ecommerce.configuration 这些兄弟包，所以必须扩大范围。
 * <p>
 * 日志清理等动态定时任务已随系统管理迁移至 system-service。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.auth.mapper")
public class AuthServiceApplication {

    public static void main(String[] args) {
        // 关闭 Nacos 客户端自带的文件日志（nacos-logback.xml）：Windows 下滚动归档无法重命名
        // 正在写入的 config.log，会持续刷 WARN 且日志文件无限增长。关闭后跟随应用的 logback 输出到控制台。
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
