package ecommerce.user;

import ecommerce.properties.WeChatProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 用户服务启动类。
 * <p>
 * scanBasePackages 显式指定 "ecommerce"：本类在 ecommerce.user 子包，默认扫描只覆盖 ecommerce.user.*，
 * 扫不到 common 模块里 ecommerce.common / ecommerce.configuration 这些兄弟包，所以必须扩大范围。
 * Maven 模块隔离了 classpath，扫 "ecommerce" 只会命中本服务依赖到的 common + 本服务自己的类，不会串到别的服务。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@EnableConfigurationProperties(WeChatProperties.class)
@MapperScan("ecommerce.user.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        // 关闭 Nacos 客户端自带的文件日志（nacos-logback.xml）：Windows 下滚动归档无法重命名
        // 正在写入的 config.log，会持续刷 WARN 且日志文件无限增长。关闭后跟随应用的 logback 输出到控制台。
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
