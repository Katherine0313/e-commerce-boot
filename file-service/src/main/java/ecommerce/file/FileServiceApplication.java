package ecommerce.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务启动类（占位骨架：图片上传/静态托管，业务后续迁移）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
