package ecommerce.monitor.support;

import java.util.regex.Pattern;

/**
 * 任务日志文字脱敏。
 * <p>
 * 后台页面回显的异常信息常把连接串、账号口令一并带出来，这里做最小处理：
 * 连接串只留协议、URL 里的 user:pass@host 整体掩码、password= 之类的参数值掩码，最后截断长度。
 */
public final class SensitiveTextSanitizer {

    /** 单条错误信息最大回显长度 */
    private static final int MAX_LENGTH = 300;

    /** password=xxx / pwd=xxx / secret=xxx 等参数值 */
    private static final Pattern SECRET_PARAM = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|access[_-]?key|token)=\\s*)([^&;,\\s}\"']+)");

    /** jdbc:mysql://user:pass@host:3306/db 这类连接串，整体只保留协议 */
    private static final Pattern CONNECTION_URL = Pattern.compile(
            "(?i)(jdbc:[a-z0-9]+://)[^\\s,;\"']+");

    /** redis://:pass@host 这类 URL 里的鉴权部分 */
    private static final Pattern URL_USERINFO = Pattern.compile("(://)([^/@\\s]+)@");

    private SensitiveTextSanitizer() {
    }

    /** 空值原样返回；其余做连接串/口令掩码并截断 */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = CONNECTION_URL.matcher(text).replaceAll("$1******");
        result = URL_USERINFO.matcher(result).replaceAll("$1******@");
        result = SECRET_PARAM.matcher(result).replaceAll("$1******");
        return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) + "…" : result;
    }
}
