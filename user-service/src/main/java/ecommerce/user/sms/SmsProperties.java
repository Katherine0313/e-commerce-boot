package ecommerce.user.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信配置（前缀 aliyun.sms）。
 * 所有值都从环境变量注入，真实密钥只放 application-dev.yml / deploy/.env，不进版本库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.sms")
public class SmsProperties {

    /** false（默认）= 走 MockSmsService 只打日志；true = 真实调用阿里云 SendSms */
    private boolean enabled;

    private String accessKeyId;

    private String accessKeySecret;

    /** 短信签名，阿里云控制台审核通过后获得 */
    private String signName;

    /** 模板 code，如 SMS_33960536 */
    private String templateCode;

    /** 短信 API 域名，国内固定值，一般不用改 */
    private String endpoint = "dysmsapi.aliyuncs.com";
}
