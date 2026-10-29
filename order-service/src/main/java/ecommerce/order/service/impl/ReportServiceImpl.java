package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.ReportService;
import ecommerce.order.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表统计 Service 实现（后台工作台 /api/admin/report）。
 * <p>
 * 口径：营业额 = 实收（pay_at 区间 pay_amount 合计）− 退款（refunded 售后单 refund_amount），
 * 实收/退款都含取消订单，退款跟着原订单 pay_at 归属，历史数字随退款动态变动。
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    /** 运营数据报表模板预置的明细行数（一个月最多 31 天） */
    private static final int OPERATION_REPORT_MAX_ROWS = 31;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public ReportServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public TurnoverVO getTurnoverStats(String period) {
        PeriodRange range = resolvePeriod(period);
        return TurnoverVO.builder()
                .turnover(turnoverOf(range.begin(), range.end()))
                .timeDesc(range.timeDesc())
                .build();
    }

    @Override
    public TurnoverReportVO getTurnoverReport(String period) {
        PeriodRange range = resolvePeriod(period);
        List<LocalDate> dates = getDateList(range.begin(), range.end());

        // 查询区间：[begin 0点, end 次日 0点)
        LocalDateTime start = range.begin().atStartOfDay();
        LocalDateTime end = range.end().plusDays(1).atStartOfDay();

        // 实收/退款按天分组 → Map<日期, 金额>，逐日相减，缺失日期补 0
        Map<String, BigDecimal> paidMap = toDateAmountMap(orderMapper.sumPaidAmountByDay(start, end));
        Map<String, BigDecimal> refundMap = toDateAmountMap(orderMapper.sumRefundAmountByDay(start, end));

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> dateList = new ArrayList<>();
        List<BigDecimal> turnoverList = new ArrayList<>();
        for (LocalDate date : dates) {
            String key = date.format(dayFmt);
            BigDecimal paid = paidMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal refund = refundMap.getOrDefault(key, BigDecimal.ZERO);
            dateList.add(key);
            turnoverList.add(paid.subtract(refund));
        }

        return TurnoverReportVO.builder()
                .dateList(dateList)
                .turnoverList(turnoverList)
                .build();
    }

    @Override
    public List<SalesRankingItemVO> getSalesRanking(String period, int limit) {
        PeriodRange range = resolvePeriod(period);
        // 限制返回条数，避免前端传大值拖垮查询
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        LocalDateTime start = range.begin().atStartOfDay();
        LocalDateTime end = range.end().plusDays(1).atStartOfDay();
        return orderItemMapper.sumSalesRanking(start, end, safeLimit);
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        return DashboardStatsVO.builder()
                // 营业额与 /turnover 同口径：pay_at 区间实收 − 退款
                .todayTurnover(turnoverOf(today, today))
                .yesterdayTurnover(turnoverOf(today.minusDays(1), today.minusDays(1)))
                // 订单量按 created_at，排除已取消（与工作台原前端口径一致）
                .todayOrders(orderMapper.countCreatedBetween(todayStart, tomorrowStart))
                .yesterdayOrders(orderMapper.countCreatedBetween(yesterdayStart, todayStart))
                // 待发货 = 状态 3
                .pendingShip(orderMapper.countByStatus(OrderStatusEnum.PENDING_SHIP.getCode()))
                .build();
    }

    @Override
    public HeadlineVO getHeadline() {
        // 累计口径与营业额报表一致：全时段实收 − 退款，不受订单分页影响
        BigDecimal paid = orderMapper.sumPaidAmountAll();
        BigDecimal refund = orderMapper.sumRefundAmountAll();
        BigDecimal safePaid = paid == null ? BigDecimal.ZERO : paid;
        BigDecimal safeRefund = refund == null ? BigDecimal.ZERO : refund;
        return HeadlineVO.builder()
                .totalPaid(safePaid)
                .totalRefund(safeRefund)
                .netAmount(safePaid.subtract(safeRefund))
                // 累计订单量：全时段、排除已取消，不受分页影响
                .totalOrders(orderMapper.countAllCounted())
                .build();
    }

    @Override
    public OperationReportVO getOperationReport() {
        LocalDate end = LocalDate.now();
        // 本月：月初至今天（含）
        LocalDate begin = end.withDayOfMonth(1);
        LocalDateTime start = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();

        // 一次取齐 6 组按天聚合，避免循环 30 次查库
        Map<String, BigDecimal> paidMap = toDateAmountMap(
                orderMapper.sumPaidAmountByDay(start, endExclusive));
        Map<String, BigDecimal> refundMap = toDateAmountMap(
                orderMapper.sumRefundAmountByDay(start, endExclusive));
        Map<String, Long> effectivePaidMap = toDateCountMap(
                orderMapper.countEffectivePaidByDay(start, endExclusive));
        Map<String, Long> createdMap = toDateCountMap(
                orderMapper.countCreatedByDay(start, endExclusive));
        Map<String, Long> createdPaidMap = toDateCountMap(
                orderMapper.countCreatedPaidByDay(start, endExclusive));
        Map<String, Long> newUserMap = toDateCountMap(
                orderMapper.countNewUsersByDay(start, endExclusive));
        long totalRefundOrders = orderMapper.countRefundOrders(start, endExclusive);

        List<OperationReportRowVO> rows = new ArrayList<>(end.getDayOfMonth());
        BigDecimal totalSales = BigDecimal.ZERO;
        long totalEffectivePaid = 0;
        long totalCreated = 0;
        long totalCreatedPaid = 0;
        long totalNewUsers = 0;

        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            // 销售额与退款都按原订单 pay_at 归属到当天
            BigDecimal sales = paidMap.getOrDefault(key, BigDecimal.ZERO)
                    .subtract(refundMap.getOrDefault(key, BigDecimal.ZERO));
            long effectivePaid = effectivePaidMap.getOrDefault(key, 0L);
            long created = createdMap.getOrDefault(key, 0L);
            long createdPaid = createdPaidMap.getOrDefault(key, 0L);
            long newUsers = newUserMap.getOrDefault(key, 0L);

            rows.add(OperationReportRowVO.builder()
                    .date(d)
                    .sales(sales)
                    .effectivePaidOrders(effectivePaid)
                    // 同批口径：当天提交的订单里支付成功过的占比（后续退款不扣分子）
                    .paymentConversionRate(rate(createdPaid, created))
                    .averageOrderValue(average(sales, effectivePaid))
                    .newUsers(newUsers)
                    .build());

            totalSales = totalSales.add(sales);
            totalEffectivePaid += effectivePaid;
            totalCreated += created;
            totalCreatedPaid += createdPaid;
            totalNewUsers += newUsers;
        }

        return OperationReportVO.builder()
                .begin(begin)
                .end(end)
                .totalSales(totalSales)
                .totalEffectivePaidOrders(totalEffectivePaid)
                .paymentConversionRate(rate(totalCreatedPaid, totalCreated))
                .averageOrderValue(average(totalSales, totalEffectivePaid))
                .totalNewUsers(totalNewUsers)
                .totalRefundOrders(totalRefundOrders)
                .rows(rows)
                .build();
    }

    @Override
    public void exportOperationReportByTemplate(HttpServletResponse response) {
        // 统计数据与页面展示共用同一份聚合结果
        OperationReportVO report = getOperationReport();

        // 模板放在 resources/template 下，打包后仍能从 classpath 读取
        try (InputStream is = this.getClass().getClassLoader()
                .getResourceAsStream("template/运营报表模板.xlsx")) {
            if (is == null) {
                throw new BusinessException("报表模板不存在");
            }
            try (XSSFWorkbook excel = new XSSFWorkbook(is)) {
                XSSFSheet sheet = excel.getSheetAt(0);

                // 概览区：Excel 第 3 行（POI 下标 2）和第 4 行（下标 3）
                Row overview1 = sheet.getRow(2);
                overview1.getCell(1).setCellValue(report.getTotalSales().doubleValue());
                overview1.getCell(3).setCellValue(report.getPaymentConversionRate());
                overview1.getCell(5).setCellValue(report.getTotalNewUsers());

                Row overview2 = sheet.getRow(3);
                overview2.getCell(1).setCellValue(report.getTotalEffectivePaidOrders());
                overview2.getCell(3).setCellValue(report.getAverageOrderValue().doubleValue());
                overview2.getCell(5).setCellValue(report.getTotalRefundOrders());

                // 明细区：模板第 7 行起（下标 6）预置了 31 行带样式的空行，
                // 用 getRow 取行再填值，样式不会丢
                int rowIndex = 6;
                for (OperationReportRowVO item : report.getRows()) {
                    Row row = sheet.getRow(rowIndex++);
                    row.getCell(0).setCellValue(item.getDate().toString());
                    row.getCell(1).setCellValue(item.getSales().doubleValue());
                    row.getCell(2).setCellValue(item.getEffectivePaidOrders());
                    row.getCell(3).setCellValue(item.getPaymentConversionRate());
                    row.getCell(4).setCellValue(item.getAverageOrderValue().doubleValue());
                    row.getCell(5).setCellValue(item.getNewUsers());
                }
                // 本月未过完时，隐藏模板中剩余的空行，报表只展示到今天的明细
                for (int i = rowIndex; i < 6 + OPERATION_REPORT_MAX_ROWS; i++) {
                    Row emptyRow = sheet.getRow(i);
                    if (emptyRow != null) {
                        emptyRow.setZeroHeight(true);
                    }
                }

                // 下载头：中文文件名按 RFC 5987 编码，避免浏览器乱码
                String filename = "运营数据报表_"
                        + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
                String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setContentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

                excel.write(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("运营数据报表导出失败", e);
            throw new BusinessException("导出报表异常");
        }
    }

    /** 比率：分母为 0 时返回 0，避免除零 */
    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0d : (double) numerator / denominator;
    }

    /** 客单价：分母为 0 时返回 0，保留两位小数 */
    private static BigDecimal average(BigDecimal amount, long count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /** 分组查询结果 [{date_key, cnt}] 转 Map<日期, 数量> */
    private Map<String, Long> toDateCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object key = row.get("date_key");
            Object cnt = row.get("cnt");
            if (key != null) {
                map.put(String.valueOf(key), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        return map;
    }

    /**
     * 根据 period 算起止日期（闭区间），switch 只负责这一件事
     */
    private PeriodRange resolvePeriod(String period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "" : period) {
            // 昨日：完整自然日
            case "yesterday" -> new PeriodRange(today.minusDays(1), today.minusDays(1), "昨日");
            // 近7天（含今天）
            case "week" -> new PeriodRange(today.minusDays(6), today, "近7天");
            // 本月：月初至今天
            case "month" -> new PeriodRange(today.withDayOfMonth(1), today, "本月");
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "非法的统计维度");
        };
    }

    /**
     * 连续日期列表（闭区间，按天递增）
     */
    private List<LocalDate> getDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate d = begin;
        while (!d.isAfter(end)) {
            dates.add(d);
            d = d.plusDays(1);
        }
        return dates;
    }

    /**
     * 单区间营业额 = 实收 − 退款（日期闭区间 → [begin 0点, end 次日 0点)，JVM 本地时区与 pay_at 同基准）
     */
    private BigDecimal turnoverOf(LocalDate begin, LocalDate end) {
        LocalDateTime start = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        BigDecimal paid = orderMapper.sumPaidAmount(start, endExclusive);
        BigDecimal refund = orderMapper.sumRefundAmount(start, endExclusive);
        return (paid == null ? BigDecimal.ZERO : paid).subtract(refund == null ? BigDecimal.ZERO : refund);
    }

    /**
     * 统计周期：日期闭区间 + 文案
     */
    private record PeriodRange(LocalDate begin, LocalDate end, String timeDesc) {
    }

    /**
     * 分组查询结果 [{date_key, amount}] 转 Map<日期, 金额>
     */
    private Map<String, BigDecimal> toDateAmountMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("date_key");
            Object amount = row.get("amount");
            if (key != null) {
                map.put(String.valueOf(key), amount == null ? BigDecimal.ZERO : new BigDecimal(amount.toString()));
            }
        }
        return map;
    }
}