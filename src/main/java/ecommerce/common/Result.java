package ecommerce.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体，所有接口都返回这个结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    /** 业务状态码：200 成功，其余为失败 */
    @Schema(description = "业务状态码：200 成功，400 参数错误，401 未登录/登录过期，403 无权访问，500 其他失败。"
            + "注意 HTTP 状态码始终为 200，前端必须判断该字段", example = "200")
    private Integer code;
    /** 提示信息 */
    @Schema(description = "提示信息，失败时可直接展示给用户", example = "success")
    private String msg;
    /** 业务数据 */
    @Schema(description = "业务数据，失败时为 null")
    private T data;

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 失败，默认 500 */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /** 失败，指定状态码 */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
