package ecommerce.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作日志查询条件。
 * <p>
 * 做成对象而不是一堆散参数：Mapper 里直接按属性名取值，
 * 少一层 @Param 前缀，写 SQL 时不用到处写 query.xxx。
 */
@Data
public class AdminOperationLogQuery {
    /** 当前页，从 1 开始 */
    private int page = 1;
    /** 每页条数，Service 里会夹到 100 以内 */
    private int pageSize = 10;
    /** 跳过多少行（= (page-1)*pageSize），由 {@link #normalize()} 算好 */
    private int offset;
    /** 业务模块，精确匹配，如 system */
    private String module;
    /** 动作，精确匹配，如 USER_ADD */
    private String action;
    /** 操作人账号，模糊匹配 */
    private String username;
    /** 是否成功：1 成功 0 失败；null 表示不限 */
    private Integer success;
    /** 起始时间；为空时默认取近 7 天 */
    private LocalDateTime startTime;
    /** 结束时间；为空时默认取当前时刻 */
    private LocalDateTime endTime;
    /** 关键字，模糊匹配 username / module / action / detail / uri */
    private String keyword;

    /**
     * 归一化查询条件：统一在这里夹分页、补时间范围、清掉空串条件。
     * <p>
     * 为什么默认近 7 天：operation_log 是全量审计表，不加时间范围会全表扫，
     * MySQL 5.7 上直接把列表接口拖垮。
     */
    public AdminOperationLogQuery normalize() {
        this.page = Math.max(1, page);
        this.pageSize = Math.min(100, Math.max(1, pageSize));
        this.offset = (page - 1) * pageSize;
        LocalDateTime now = LocalDateTime.now();
        if (endTime == null) {
            endTime = now;
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }
        this.module = trimToNull(module);
        this.action = trimToNull(action);
        this.username = trimToNull(username);
        this.keyword = trimToNull(keyword);
        return this;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
