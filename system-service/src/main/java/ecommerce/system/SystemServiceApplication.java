package ecommerce.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统服务启动类：系统管理（用户/角色/菜单/日志/数据库/接口）+ 系统监控。
 * <p>
 * scanBasePackages 显式指定 "ecommerce"：本类在 ecommerce.system 子包，默认扫描只覆盖 ecommerce.system.*，
 * 扫不到 common 模块里 ecommerce.common / ecommerce.configuration 这些兄弟包，所以必须扩大范围。
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.system.mapper")
public class SystemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
