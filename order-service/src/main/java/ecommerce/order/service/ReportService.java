package ecommerce.order.service;

import ecommerce.order.vo.DashboardStatsVO;
import ecommerce.order.vo.TurnoverReportVO;
import ecommerce.order.vo.TurnoverVO;

/**
 * 报表统计 Service（后台工作台 /api/admin/report）。
 * <p>
 * 只放统计聚合逻辑，与订单 CRUD/履约解耦；后续订单统计、销量 Top10 等在此扩展。
 */
public interface ReportService {

    /** 营业额卡片：按 period（yesterday/week/month）返回选中维度一项 */
    TurnoverVO getTurnoverStats(String period);

    /** 营业额折线报表：按 period（yesterday/week/month）按天返回日期+营业额序列 */
    TurnoverReportVO getTurnoverReport(String period);

    /** 工作台统计卡：今日/昨日营业额、今日/昨日订单量、待发货数（一次取齐，供四张卡） */
    DashboardStatsVO getDashboardStats();
}
