package ecommerce.order.service;

import ecommerce.order.vo.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 报表统计 Service（后台工作台 /api/admin/report）。
 * <p>
 * 只放统计聚合逻辑，与订单 CRUD/履约解耦；订单统计等后续报表也在此扩展。
 */
public interface ReportService {

    /** 营业额卡片：按 period（yesterday/week/month）返回选中维度一项 */
    TurnoverVO getTurnoverStats(String period);

    /** 营业额折线报表：按 period（yesterday/week/month）按天返回日期+营业额序列 */
    TurnoverReportVO getTurnoverReport(String period);

    /** 销量排名：按 period 汇总已支付订单的商品销量，取前 limit 名 */
    List<SalesRankingItemVO> getSalesRanking(String period, int limit);

    /** 工作台统计卡：今日/昨日营业额、今日/昨日订单量、待发货数（一次取齐，供四张卡） */
    DashboardStatsVO getDashboardStats();

    /** 工作台顶部累计销售额：全时段实收 − 退款 */
    HeadlineVO getHeadline();

    /** 运营数据报表（本月，月初至今天）：概览 + 每日明细 */
    OperationReportVO getOperationReport();

    /** 运营数据报表导出：读取模板写入本月数据，Excel 流直接写给浏览器下载 */
    void exportOperationReportByTemplate(HttpServletResponse response);

}
