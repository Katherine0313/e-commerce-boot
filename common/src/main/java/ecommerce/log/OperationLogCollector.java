package ecommerce.log;

import ecommerce.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 操作日志采集工具：把「操作人 / IP / 参数」这些通用字段从请求上下文里取出来。
 * <p>
 * 单独抽出来是因为线 2（售后变更流水）也要复用其中一部分，不重复造。
 */
@Component
public class OperationLogCollector {

    /** params 字段最大长度（对应 operation_log.params varchar(2000)） */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** 敏感参数名（大小写不敏感），值一律替换成 ***
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pwd", "pass", "oldpassword", "newpassword", "oldpwd", "newpwd",
            "token", "accesstoken", "refreshtoken", "authorization", "secret", "appsecret", "apikey");

    /** 11 位手机号 */
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 脱敏后的固定值 */
    private static final String MASKED = "***";

    private final ObjectMapper objectMapper;

    public OperationLogCollector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 当前操作人 ID，未登录为 null */
    public Long currentUserId() {
        return UserContext.getUserId();
    }

    /** 当前操作人类型：admin / user，未登录为 null */
    public String currentUserType() {
        return UserContext.getUserType();
    }

    /** 当前操作人账号快照，未登录为 null */
    public String currentUsername() {
        return UserContext.getUsername();
    }

    /**
     * 取客户端 IP。
     * <p>
     * 生产走 EdgeOne / Nginx / Cloudflare 反代，remoteAddr 是代理机 IP。
     * 优先级与登录接口（AuthController.resolveClientIp）保持一致：
     * EO-Connecting-IP（EdgeOne 注入，最接近真实客户端）→ X-Forwarded-For 首段 → X-Real-IP → remoteAddr。
     */
    public String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String eoIp = request.getHeader("EO-Connecting-IP");
        if (eoIp != null && !eoIp.isBlank()) {
            return eoIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 请求参数转 JSON。
     * <p>
     * 过滤掉 Servlet 请求/响应、文件上传、校验结果这些没法（也没必要）序列化的参数；
     * 序列化失败或超长一律截断/返回 null，绝不抛异常影响业务。
     * <p>
     * 脱敏在 JSON 树上做（而不是对序列化后的字符串做正则），这样嵌套结构里的
     * 密码字段、手机号也能覆盖到。
     */
    public String paramsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> safe = Arrays.stream(args)
                .filter(this::isSerializable)
                .toList();
        if (safe.isEmpty()) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(safe);
            mask(tree);
            String json = objectMapper.writeValueAsString(tree);
            return truncate(json, MAX_PARAMS_LENGTH);
        } catch (Exception e) {
            return null;
        }
    }

    /** 截断到指定长度 */
    public String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 参数是否适合进日志 */
    private boolean isSerializable(Object arg) {
        return !(arg instanceof HttpServletRequest)
                && !(arg instanceof HttpServletResponse)
                && !(arg instanceof MultipartFile)
                && !(arg instanceof BindingResult);
    }

    /**
     * 递归脱敏：敏感键的值换成 ***，11 位手机号保留前 3 后 4。
     * <p>
     * 走 JSON 树逐节点处理，避免「序列化后正则匹配」漏掉嵌套里的同名字段。
     */
    private void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // 先把键快照出来：处理过程中会替换子节点，边改边遍历容易出问题
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : obj.properties()) {
                keys.add(entry.getKey());
            }
            for (String key : keys) {
                JsonNode value = obj.get(key);
                if (isSensitiveKey(key)) {
                    obj.put(key, MASKED);
                } else if (value != null && value.isString() && isMobile(value.asText())) {
                    obj.put(key, maskPhone(value.asText()));
                } else {
                    mask(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                if (item != null && item.isString() && isMobile(item.asText())) {
                    arr.set(i, objectMapper.getNodeFactory().textNode(maskPhone(item.asText())));
                } else {
                    mask(item);
                }
            }
        }
    }

    /** 是否 11 位手机号 */
    private boolean isMobile(String text) {
        return text != null && MOBILE.matcher(text).matches();
    }

    /** 手机号脱敏：138****5678 */
    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 敏感字段名判断（大小写不敏感，兼容 password / userPassword 这类驼峰写法） */
    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(lower)) {
            return true;
        }
        // 驼峰写法：userPassword / oldPwd → 去掉驼峰再比对
        String flat = lower.replace("_", "");
        return SENSITIVE_KEYS.contains(flat)
                || flat.endsWith("password")
                || flat.endsWith("pwd")
                || flat.endsWith("token")
                || flat.endsWith("secret");
    }
}
