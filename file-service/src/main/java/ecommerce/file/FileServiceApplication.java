package ecommerce.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务启动类（占位骨架：图片上传/静态托管，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class FileServiceApplication {

    public static void main(String[] args) {
        // 关闭 Nacos 客户端自带的文件日志（nacos-logback.xml）：Windows 下滚动归档无法重命名
        // 正在写入的 config.log，会持续刷 WARN 且日志文件无限增长。关闭后跟随应用的 logback 输出到控制台。
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
