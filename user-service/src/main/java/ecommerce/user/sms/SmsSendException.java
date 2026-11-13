package ecommerce.user.sms;

/**
 * 短信发送失败。由调用方（PcAuthServiceImpl）捕获后转成对用户友好的提示，
 * 同时释放限频 key，让用户能立刻重发。
 */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message) {
        super(message);
    }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
