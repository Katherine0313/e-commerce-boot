package ecommerce.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WeChatProperties {
    private String appid;
    private String secret;

    // 启动时校验
    @PostConstruct
    public void validate(){
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()){
            throw new IllegalStateException("wechat.appid / wechat.secret 未配置，请在application-dev.yml中配置");
        }
    }
}
