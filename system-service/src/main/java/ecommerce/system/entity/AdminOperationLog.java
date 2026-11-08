package ecommerce.system.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台操作日志实体（对应 operation_log 表）。
 * <p>
 * 写入方是 common 的 JdbcOperationLogRecorder（JdbcTemplate 直写），
 * 这里只是读侧的内存模型，字段与表结构一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperationLog {
    private Long id;
    /** 操作人 ID */
    private Long userId;
    /** 操作人类型：admin / user */
    private String userType;
    /** 操作人账号快照（账号可能被改名或软删，只靠 userId 认不出人） */
    private String username;
    /** 业务模块，如 system / aftersales */
    private String module;
    /** 动作，如 USER_ADD / USER_EDIT */
    private String action;
    /** 业务对象 ID */
    private Long bizId;
    /** 补充说明 / 变更详情 */
    private String detail;
    /** HTTP 方法 */
    private String method;
    /** 请求路径 */
    private String uri;
    /** 客户端 IP */
    private String ip;
    /** 请求参数 JSON（敏感字段已脱敏） */
    private String params;
    /** 是否成功：1 成功 0 失败 */
    private Integer success;
    /** 失败原因 */
    private String errorMsg;
    /** 耗时（毫秒） */
    private Long costMs;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
