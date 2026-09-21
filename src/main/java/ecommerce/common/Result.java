package ecommerce.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体，所有接口都返回这个结构。
 * <p>
 * 成功时 HTTP 状态码为 200，失败时返回真实 HTTP 状态码（400/401/403/404/500），
 * 前端通过 success 字段 + HTTP 状态码判断结果，错误提示读 message 字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    /** 是否成功：true 成功，false 失败 */
    @Schema(description = "是否成功：true 成功，false 失败")
    private Boolean success;
    /** 业务数据，失败时为 null */
    @Schema(description = "业务数据，失败时为 null")
    private T data;
    /** 提示信息，失败时可直接展示给用户 */
    @Schema(description = "提示信息，失败时可直接展示给用户", example = "success")
    private String message;
    /** 可选字符串错误码（如 UNAUTHORIZED / FORBIDDEN / NOT_FOUND），成功或无需细分时为 null */
    @Schema(description = "可选字符串错误码，如 UNAUTHORIZED / FORBIDDEN / NOT_FOUND，成功或无需细分时为 null")
    private String code;

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(true, data, null, null);
    }

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 失败，仅提示信息 */
    public static <T> Result<T> error(String message) {
        return new Result<>(false, null, message, null);
    }

    /** 失败，带字符串错误码 */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(false, null, message, code);
    }
}
