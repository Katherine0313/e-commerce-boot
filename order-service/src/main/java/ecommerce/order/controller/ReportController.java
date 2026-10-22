package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.service.ReportService;
import ecommerce.order.vo.DashboardStatsVO;
import ecommerce.order.vo.TurnoverReportVO;
import ecommerce.order.vo.TurnoverVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台报表统计接口（/api/admin/report）。
 * <p>
 * 专门承载统计报表（营业额卡片 + 折线，后续订单统计、销量 Top10 等），
 * 与订单业务解耦，走 admin 身份（拦截器按 /api/admin 分流）。
 */
@Tag(name = "后台报表统计", description = "营业额卡片/折线等统计（管理员）")
@RestController
@RequestMapping("/api/admin/report")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "营业额统计（period：yesterday/week/month）")
    @GetMapping("/turnover")
    public Result<TurnoverVO> turnover(@RequestParam String period) {
        return Result.success(reportService.getTurnoverStats(period));
    }

    @Operation(summary = "营业额折线报表（period：yesterday/week/month）")
    @GetMapping("/turnover-report")
    public Result<TurnoverReportVO> turnoverReport(@RequestParam String period) {
        return Result.success(reportService.getTurnoverReport(period));
    }

    @Operation(summary = "工作台统计卡（今日/昨日营业额与订单量、待发货数）")
    @GetMapping("/dashboard")
    public Result<DashboardStatsVO> dashboard() {
        return Result.success(reportService.getDashboardStats());
    }
}
