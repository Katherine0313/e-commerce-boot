package ecommerce.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration

public class MybatisConfig {
    static{
        System.out.println("配置类被加载了");
    }
}
