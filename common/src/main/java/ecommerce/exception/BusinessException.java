package ecommerce.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：Service 层抛出，由全局异常处理器统一转成 Result + 对应 HTTP 状态码返回前端
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 真实的 HTTP 状态码，由全局异常处理器映射到响应状态 */
    private final HttpStatus httpStatus;
    /** 可选的字符串错误码（如 UNAUTHORIZED / FORBIDDEN），当前阶段默认 null */
    private final String errorCode;

    public BusinessException(String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, null, message);
    }

    public BusinessException(HttpStatus httpStatus, String message) {
        this(httpStatus, null, message);
    }

    public BusinessException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
