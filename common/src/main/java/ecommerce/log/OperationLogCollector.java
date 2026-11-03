package ecommerce.log;

import ecommerce.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * 操作日志采集工具：把「操作人 / IP / 参数」这些通用字段从请求上下文里取出来。
 * <p>
 * 单独抽出来是因为线 2（售后变更流水）也要复用其中一部分，不重复造。
 */
@Component
public class OperationLogCollector {

    /** params 字段最大长度（对应 operation_log.params varchar(2000)） */
    private static final int MAX_PARAMS_LENGTH = 2000;

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

    /**
     * 取客户端 IP。
     * <p>
     * 生产走 Nginx/Cloudflare 反代，remoteAddr 是代理机 IP，所以优先取 X-Forwarded-For 首段。
     */
    public String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
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
            String json = objectMapper.writeValueAsString(safe);
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
}
