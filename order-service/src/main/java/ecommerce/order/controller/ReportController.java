package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.excel.OperationReportExcelWriter;
import ecommerce.order.service.ReportService;
import ecommerce.order.vo.DashboardStatsVO;
import ecommerce.order.vo.HeadlineVO;
import ecommerce.order.vo.OperationReportVO;
import ecommerce.order.vo.SalesRankingItemVO;
import ecommerce.order.vo.TurnoverReportVO;
import ecommerce.order.vo.TurnoverVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 后台报表统计接口（/api/admin/report）。
 * <p>
 * 专门承载统计报表（营业额卡片 + 折线、销量排名等），
 * 与订单业务解耦，走 admin 身份（拦截器按 /api/admin 分流）。
 */
@Tag(name = "后台报表统计", description = "营业额卡片/折线等统计（管理员）")
@RestController
@RequestMapping("/api/admin/report")
@Slf4j
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
        TurnoverReportVO vo = reportService.getTurnoverReport(period);
        log.info("dateList:{}, turnoverList:{}",vo.getDateList(),vo.getTurnoverList());
        return Result.success(vo);
    }

    @Operation(summary = "销量排名（period：yesterday/week/month，limit 默认 8）")
    @GetMapping("/sales-ranking")
    public Result<List<SalesRankingItemVO>> salesRanking(@RequestParam String period,
                                                         @RequestParam(defaultValue = "8") int limit) {
        return Result.success(reportService.getSalesRanking(period, limit));
    }

    @Operation(summary = "工作台统计卡（今日/昨日营业额与订单量、待发货数）")
    @GetMapping("/dashboard")
    public Result<DashboardStatsVO> dashboard() {
        return Result.success(reportService.getDashboardStats());
    }

    @Operation(summary = "工作台顶部累计销售额（全时段实收 − 退款）")
    @GetMapping("/headline")
    public Result<HeadlineVO> headline() {
        return Result.success(reportService.getHeadline());
    }

    @Operation(summary = "导出运营数据报表（近 30 天 Excel）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOperationReport() {
        OperationReportVO report = reportService.getOperationReport();
        byte[] data = OperationReportExcelWriter.write(report);

        // RFC 5987：中文文件名用 UTF-8 百分号编码，避免浏览器乱码
        String filename = "运营数据报表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}