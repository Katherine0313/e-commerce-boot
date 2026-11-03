package ecommerce.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户增长统计 VO（后台工作台 /api/admin/users/statistics）。
 * <p>
 * buckets 是时间桶序列（小时 / 天 / 月，由 bucketType 区分）；
 * newUsers 与 buckets 一一对应，是每个桶的新增用户数（增量，即「每日新增」）；
 * cumulativeUsers 与 buckets 一一对应，是每个桶结束时的累计用户数（存量曲线），
 * 由后端算好，前端直接画；
 * totalUsers 是当前店铺的真实用户总量（不受统计窗口限制），供工作台统计卡使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatisticsVO {
    /** 时间桶类型：hour（昨日按小时）/ day（近7天、本月按天）/ month（近12月） */
    private String bucketType;
    /** 时间桶序列：如 ["00:00"...] / ["2026-10-18"...] / ["2025-11"...] */
    private List<String> buckets;
    /** 每个时间桶的新增用户数，与 buckets 对齐 */
    private List<Long> newUsers;
    /**
     * 每个时间桶结束时的累计用户数（存量曲线），与 buckets 对齐。
     * <p>
     * 后端算好给出：期初存量 = 用户总量 − 本窗口新增，之后逐桶累加。
     * 前端只负责画，不再自己反推，避免口径散落在两端。
     */
    private List<Long> cumulativeUsers;
    /** 用户总量（当前店铺未删除用户数） */
    private Long totalUsers;
}
