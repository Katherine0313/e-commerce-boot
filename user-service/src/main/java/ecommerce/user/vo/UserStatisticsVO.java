package ecommerce.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户增长统计 VO（后台工作台 /api/admin/users/statistics）。
 * <p>
 * newUsers 是每月新增用户数；累计曲线由前端累加 newUsers 得到。
 * totalUsers 是当前店铺的真实用户总量（不受近 12 个月窗口限制），供工作台统计卡使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatisticsVO {
    /** 月份序列，如 ["2025-11", "2025-12", ...]，与 newUsers 对齐 */
    private List<String> months;
    /** 每月新增用户数，与 months 对齐 */
    private List<Long> newUsers;
    /** 用户总量（当前店铺未删除用户数） */
    private Long totalUsers;
}
