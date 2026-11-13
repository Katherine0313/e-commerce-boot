package ecommerce.user.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 阿里云短信真实发送（aliyun.sms.enabled=true 时生效）。
 * 只负责「把验证码发出去」，存码/校验/限频都在 PcAuthServiceImpl + Redis 里，不在这里。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "aliyun.sms", name = "enabled", havingValue = "true")
public class AliyunSmsService implements SmsService {

    /** 阿里云返回的发送成功码 */
    private static final String SUCCESS_CODE = "OK";

    private final SmsProperties properties;

    /** SDK 客户端线程安全，懒加载一次即可 */
    private volatile Client client;

    public AliyunSmsService(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendCode(String phone, String code) {
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(properties.getSignName())
                .setTemplateCode(properties.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\"}");

        SendSmsResponse response;
        try {
            response = client().sendSms(request);
        } catch (Exception e) {
            throw new SmsSendException("调用阿里云短信接口异常: " + e.getMessage(), e);
        }

        SendSmsResponseBody body = response == null ? null : response.getBody();
        String resultCode = body == null ? null : body.getCode();
        if (!SUCCESS_CODE.equals(resultCode)) {
            String message = body == null ? "响应为空" : body.getMessage();
            throw new SmsSendException("阿里云短信发送失败 code=" + resultCode + " message=" + message);
        }
        log.info("短信发送成功 phone={} bizId={}", phone, body.getBizId());
    }

    /**
     * 懒加载 SDK 客户端：避免配置缺失时影响服务启动，只在真正发短信时初始化。
     */
    private Client client() throws Exception {
        Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    requireConfigured();
                    Config config = new Config()
                            .setAccessKeyId(properties.getAccessKeyId())
                            .setAccessKeySecret(properties.getAccessKeySecret());
                    config.endpoint = properties.getEndpoint();
                    local = new Client(config);
                    client = local;
                }
            }
        }
        return local;
    }

    private void requireConfigured() {
        if (isBlank(properties.getAccessKeyId())
                || isBlank(properties.getAccessKeySecret())
                || isBlank(properties.getSignName())
                || isBlank(properties.getTemplateCode())) {
            throw new SmsSendException(
                    "短信配置不完整：请检查 aliyun.sms.access-key-id / access-key-secret / sign-name / template-code");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
