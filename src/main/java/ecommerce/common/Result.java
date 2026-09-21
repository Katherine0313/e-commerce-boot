package ecommerce.common;

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
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
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
