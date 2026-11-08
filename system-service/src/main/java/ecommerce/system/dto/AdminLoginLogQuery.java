package ecommerce.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台登录日志查询条件。
 * <p>
 * 做成对象而不是一堆散参数：Mapper 里直接按属性名取值，
 * 少一层 @Param 前缀，写 SQL 时不用到处写 query.xxx。
 */
@Data
public class AdminLoginLogQuery {
    /** 当前页，从 1 开始 */
    private int page = 1;
    /** 每页条数，Service 里会夹到 100 以内 */
    private int pageSize = 10;
    /** 跳过多少行（= (page-1)*pageSize），由 {@link #normalize()} 算好 */
    private int offset;
    /** 登录名，模糊匹配 */
    private String username;
    /** LOGIN / LOGOUT */
    private String loginType;
    /** SUCCESS / BAD_CREDENTIALS … */
    private String resultCode;
    /** 客户端 IP，精确匹配 */
    private String ip;
    /** 起始时间；为空时默认取近 7 天 */
    private LocalDateTime startTime;
    /** 结束时间；为空时默认取当前时刻 */
    private LocalDateTime endTime;

    /**
     * 归一化查询条件：统一在这里夹分页、补时间范围、清掉空串条件。
     * <p>
     * 默认近 7 天：登录日志量大，不限时间范围会全表扫。
     */
    public AdminLoginLogQuery normalize() {
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
        this.username = trimToNull(username);
        this.loginType = trimToNull(loginType);
        this.resultCode = trimToNull(resultCode);
        this.ip = trimToNull(ip);
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
