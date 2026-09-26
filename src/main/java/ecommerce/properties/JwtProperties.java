package ecommerce.properties;

import jakarta.annotation.PostConstruct;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long expire;
    // 启动时校验
    @PostConstruct
    public void validate(){
        if(secret == null || secret.isBlank()){
            throw new IllegalStateException(
                    "jwt.secret未配置，请在application-dev.yml中配置"
            );
        }
    }
}
