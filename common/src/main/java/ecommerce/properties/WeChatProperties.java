package ecommerce.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wechat")
public class WeChatProperties {
    private String appid;
    private String secret;
    /** 本地开发兜底：微信因 IP 不在白名单拒绝(40164)时用固定 mock openid 登录，生产必须为 false */
    private boolean devMockEnabled;

    // 启动时校验
    @PostConstruct
    public void validate(){
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()){
            throw new IllegalStateException("wechat.appid / wechat.secret 未配置，请在application-dev.yml中配置");
        }
    }
}
