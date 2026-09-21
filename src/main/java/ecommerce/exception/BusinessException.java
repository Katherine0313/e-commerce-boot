package ecommerce.exception;

import lombok.Getter;

/**
 * 业务异常：Service 层抛出，由全局异常处理器统一转成 Result 返回前端
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 业务状态码 */
    private final Integer code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
