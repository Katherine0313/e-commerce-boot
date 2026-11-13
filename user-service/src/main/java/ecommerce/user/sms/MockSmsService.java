package ecommerce.user.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 开发/测试环境的假发送：不调用任何外网，只把验证码打进日志。
 * aliyun.sms.enabled=false 或未配置时生效（matchIfMissing = true）。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "aliyun.sms", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockSmsService implements SmsService {

    @Override
    public void sendCode(String phone, String code) {
        log.info("[MOCK] 模拟发送短信 phone={} code={}", phone, code);
    }
}
