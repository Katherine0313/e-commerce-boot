package ecommerce.utils;

/**
 * IP 脱敏（日志展示/导出用）。
 * <p>
 * 数据库里保留完整 IP 供安全排查，只在返回给前端或导出时脱敏：
 * IPv4 保留前两段（如 203.0.*.*），IPv6 保留前两组（如 240e:3b6:*:*:*:*:*:*）。
 * <p>
 * 为什么保留前两段：既能看出大致来源地区/运营商，又不会把具体主机地址暴露出来。
 */
public final class IpMasker {

    private IpMasker() {
    }

    /**
     * 脱敏 IP。
     *
     * @param ip 原始 IP，可为空
     * @return 脱敏后的 IP；空值原样返回，无法识别的格式返回 {@code ***}
     */
    public static String mask(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        String value = ip.trim();
        // IPv4-mapped IPv6（如 ::ffff:203.0.113.5）按 IPv4 处理
        if (value.regionMatches(true, 0, "::ffff:", 0, 7)) {
            value = value.substring(7);
        }
        if (value.indexOf(':') >= 0 && value.indexOf('.') < 0) {
            return maskIpv6(value);
        }
        if (value.indexOf('.') >= 0) {
            return maskIpv4(value);
        }
        return "***";
    }

    private static String maskIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return "***";
        }
        return parts[0] + "." + parts[1] + ".*.*";
    }

    private static String maskIpv6(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length < 2 || (parts[0].isEmpty() && parts[1].isEmpty())) {
            // ::1、:: 这类地址没有可保留的网段
            return "::*";
        }
        StringBuilder masked = new StringBuilder(parts[0]).append(':').append(parts[1]);
        for (int i = 2; i < parts.length; i++) {
            masked.append(":*");
        }
        return masked.toString();
    }
}
