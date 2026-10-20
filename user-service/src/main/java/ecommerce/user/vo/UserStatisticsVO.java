package ecommerce.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户增长统计 VO（后台工作台 /api/admin/users/statistics）。
 * <p>
 * newUsers 是每月新增用户数；「用户总量」由前端累加 newUsers 得到（累计曲线）。
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
}
