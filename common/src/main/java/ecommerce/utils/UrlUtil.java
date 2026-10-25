package ecommerce.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * URL 工具：把库里存的相对路径（如 /upload/avatar/xxx.jpg）拼成前端可直接访问的绝对地址。
 * <p>
 * 取域名以 {@code Host} 为准：网关侧对 user-service 路由配了 {@code PreserveHostHeader}，
 * 下游看到的就是客户端访问的域名。{@code X-Forwarded-Host} 只作兜底——实测本项目的
 * Spring Cloud Gateway 会把它覆盖成 lb 解析出的下游实例地址（192.168.92.1:9006），不能优先信。
 * 凡是要往外返回静态资源地址的接口都走这里，不要再各自读 Host 拼。
 */
public class UrlUtil {

    /** 工具类，禁止实例化 */
    private UrlUtil() {
    }

    /**
     * 拼接绝对地址
     *
     * @param request     当前请求，用于取域名与协议
     * @param relativeUrl 相对路径（如 /upload/avatar/x.jpg）；已是 http(s):// 开头则原样返回；null 返回 null
     */
    public static String fullUrl(HttpServletRequest request, String relativeUrl) {
        // 1.没有地址就不拼了
        if (relativeUrl == null) {
            return null;
        }
        // 2.已经是完整 URL（外部头像链接等）原样返回，避免拼成 http://hosthttps://xxx
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        // 3.域名以 Host 为准（网关对该路由配了 PreserveHostHeader，下游看到的就是客户端域名）；
        //   X-Forwarded-Host 仅兜底——网关会把它覆盖成下游实例地址，不能优先信
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = forwardHeader(request, "X-Forwarded-Host");
        }
        // 4.协议：同样优先转发头，但注意网关会把它改写成「网关→下游」的协议，
        //   所以生产上网关对外是 https 时，需要在网关/Nginx 层显式透传客户端协议，否则会拼出 http 链接
        String scheme = forwardHeader(request, "X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }
        // 生产对外只有 HTTPS：网关/代理没透传协议时会拿到 http，
        // 而小程序真机会拒绝加载 http 图片（开发者工具会因 HSTS 自动升级，掩盖问题）。
        // 非本机地址一律按 https 拼接，避免拼出头像等资源的 http 链接。
        if ("http".equalsIgnoreCase(scheme) && !isLocalHost(host)) {
            scheme = "https";
        }
        return scheme + "://" + host + relativeUrl;
    }

    // 判断是否本机地址（本地开发仍走 http）
    private static boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        // 去掉端口：127.0.0.1:8080 / localhost:8080；兼容 IPv6 的 [::1]:8080
        String name = host.startsWith("[") ? host.substring(1, host.indexOf(']')) : host.split(":")[0];
        return "localhost".equalsIgnoreCase(name) || "127.0.0.1".equals(name) || "::1".equals(name);
    }

    // 取转发头：缺失或空白返回 null，多级代理下是逗号分隔列表，取第一段（最靠近客户端）
    private static String forwardHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split(",")[0].trim();
    }
}
