package ecommerce.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志条目（operation_log 表的内存模型）。
 * <p>
 * 由切面采集组装，交给 {@link OperationLogRecorder} 决定写到哪里、什么时候写。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogEntry {

    /** 操作人 ID，未登录为 null */
    private Long userId;

    /** 操作人类型：admin / user / null（未登录） */
    private String userType;

    /** 业务模块 */
    private String module;

    /** 动作 */
    private String action;

    /** 业务对象 ID */
    private Long bizId;

    /** 补充说明 */
    private String detail;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 客户端 IP */
    private String ip;

    /** 请求参数（JSON 字符串，可能已截断） */
    private String params;

    /** 是否成功 */
    private boolean success;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 创建时间（由 Java 取本地时间，不依赖 DB 的 NOW()） */
    private LocalDateTime createdAt;
}
