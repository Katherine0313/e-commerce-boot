package ecommerce.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置，对应 application.yml 中的 wechat.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WeChatProperties {
    /** 小程序 appid */
    private String appid;
    /** 小程序密钥 */
    private String secret;

    /**
     * 启动时校验：没配就直接失败，避免调用微信时返回看不懂的 errcode
     */
    @PostConstruct
    public void validate() {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "wechat.appid / wechat.secret 未配置，请在 application-dev.yml 或环境变量中设置");
        }
    }
}
