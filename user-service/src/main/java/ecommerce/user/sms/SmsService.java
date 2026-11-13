package ecommerce.user.sms;

/**
 * 短信发送抽象。
 * 实现由 aliyun.sms.enabled 决定：false → MockSmsService（打日志），true → AliyunSmsService（真实发送）。
 */
public interface SmsService {

    /**
     * 发送登录验证码短信。
     *
     * @param phone 中国大陆手机号，11 位，1 开头
     * @param code  6 位数字验证码
     * @throws SmsSendException 发送失败（余额不足、模板未审核、签名不匹配、网络异常等）
     */
    void sendCode(String phone, String code);
}
