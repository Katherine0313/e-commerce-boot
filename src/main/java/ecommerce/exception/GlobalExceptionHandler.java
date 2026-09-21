package ecommerce.exception;

import ecommerce.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把异常统一转成 Result + 真实 HTTP 状态码返回，避免每个 Controller 自己 try-catch
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：Service 主动抛出的，状态码和文案都是设计好给前端看的
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: status={}, code={}, msg={}", e.getHttpStatus(), e.getErrorCode(), e.getMessage());
        Result<Void> body = Result.error(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    /**
     * 请求体解析失败（JSON 格式错误、字段类型不匹配等）
     * 这是调用方的问题，不能算 500
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error("INVALID_PARAMS", "请求参数格式错误"));
    }

    /**
     * 兜底：未预料的异常
     * 注意：绝不把 e.getMessage() 或堆栈回传前端，只记服务端日志
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        // Spring 自带的请求类异常（404、405、请求体格式错误等）实现了 ErrorResponse，保留其原始状态码，
        // 否则它们会被一律当成 500，排查时容易误导
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            log.warn("请求异常: status={}, msg={}", status, e.getMessage());
            return ResponseEntity.status(status).body(Result.error("请求不合法"));
        }
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部错误"));
    }
}
