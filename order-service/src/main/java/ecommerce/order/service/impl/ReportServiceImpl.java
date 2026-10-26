package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.ReportService;
import ecommerce.order.vo.DashboardStatsVO;
import ecommerce.order.vo.HeadlineVO;
import ecommerce.order.vo.OperationReportRowVO;
import ecommerce.order.vo.OperationReportVO;
import ecommerce.order.vo.SalesRankingItemVO;
import ecommerce.order.vo.TurnoverReportVO;
import ecommerce.order.vo.TurnoverVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Service
public class ReportServiceImpl implements ReportService {

    /** 运营数据报表固定统计近 30 天（含今天） */
    private static final int OPERATION_REPORT_DAYS = 30;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public ReportServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    private Long requireShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问：缺少店铺上下文");
        }
        return shopId;
    }

    @Override
    public TurnoverVO getTurnoverStats(String period) {
        Long shopId = requireShopId();
        PeriodRange range = resolvePeriod(period);
        return TurnoverVO.builder()
                .turnover(turnoverOf(shopId, range.begin(), range.end()))
                .timeDesc(range.timeDesc())
                .build();
    }

    @Override
    public TurnoverReportVO getTurnoverReport(String period) {
        Long shopId = requireShopId();
        PeriodRange range = resolvePeriod(period);
        List<LocalDate> dates = getDateList(range.begin(), range.end());

        // 查询区间：[begin 0点, end 次日 0点)
        LocalDateTime start = range.begin().atStartOfDay();
        LocalDateTime end = range.end().plusDays(1).atStartOfDay();

        // 实收/退款按天分组 → Map<日期, 金额>，逐日相减，缺失日期补 0
        Map<String, BigDecimal> paidMap = toDateAmountMap(orderMapper.sumPaidAmountByDay(shopId, start, end));
        Map<String, BigDecimal> refundMap = toDateAmountMap(orderMapper.sumRefundAmountByDay(shopId, start, end));

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
        Long shopId = requireShopId();
        PeriodRange range = resolvePeriod(period);
        // 限制返回条数，避免前端传大值拖垮查询
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        LocalDateTime start = range.begin().atStartOfDay();
        LocalDateTime end = range.end().plusDays(1).atStartOfDay();
        return orderItemMapper.sumSalesRanking(shopId, start, end, safeLimit);
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        Long shopId = requireShopId();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        return DashboardStatsVO.builder()
                // 营业额与 /turnover 同口径：pay_at 区间实收 − 退款
                .todayTurnover(turnoverOf(shopId, today, today))
                .yesterdayTurnover(turnoverOf(shopId, today.minusDays(1), today.minusDays(1)))
                // 订单量按 created_at，排除已取消（与工作台原前端口径一致）
                .todayOrders(orderMapper.countCreatedBetween(shopId, todayStart, tomorrowStart))
                .yesterdayOrders(orderMapper.countCreatedBetween(shopId, yesterdayStart, todayStart))
                // 待发货 = 状态 3
                .pendingShip(orderMapper.countByStatus(shopId, OrderStatusEnum.PENDING_SHIP.getCode()))
                .build();
    }

    @Override
    public HeadlineVO getHeadline() {
        Long shopId = requireShopId();
        // 累计口径与营业额报表一致：全时段实收 − 退款，不受订单分页影响
        BigDecimal paid = orderMapper.sumPaidAmountAll(shopId);
        BigDecimal refund = orderMapper.sumRefundAmountAll(shopId);
        BigDecimal safePaid = paid == null ? BigDecimal.ZERO : paid;
        BigDecimal safeRefund = refund == null ? BigDecimal.ZERO : refund;
        return HeadlineVO.builder()
                .totalPaid(safePaid)
                .totalRefund(safeRefund)
                .netAmount(safePaid.subtract(safeRefund))
                // 累计订单量：全时段、排除已取消，不受分页影响
                .totalOrders(orderMapper.countAllCounted(shopId))
                .build();
    }

    @Override
    public OperationReportVO getOperationReport() {
        Long shopId = requireShopId();
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(OPERATION_REPORT_DAYS - 1);
        LocalDateTime start = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();

        // 一次取齐 6 组按天聚合，避免循环 30 次查库
        Map<String, BigDecimal> paidMap = toDateAmountMap(
                orderMapper.sumPaidAmountByDay(shopId, start, endExclusive));
        Map<String, BigDecimal> refundMap = toDateAmountMap(
                orderMapper.sumRefundAmountByDay(shopId, start, endExclusive));
        Map<String, Long> effectivePaidMap = toDateCountMap(
                orderMapper.countEffectivePaidByDay(shopId, start, endExclusive));
        Map<String, Long> createdMap = toDateCountMap(
                orderMapper.countCreatedByDay(shopId, start, endExclusive));
        Map<String, Long> createdPaidMap = toDateCountMap(
                orderMapper.countCreatedPaidByDay(shopId, start, endExclusive));
        Map<String, Long> newUserMap = toDateCountMap(
                orderMapper.countNewUsersByDay(shopId, start, endExclusive));
        long totalRefundOrders = orderMapper.countRefundOrders(shopId, start, endExclusive);

        List<OperationReportRowVO> rows = new ArrayList<>(OPERATION_REPORT_DAYS);
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
    private BigDecimal turnoverOf(Long shopId, LocalDate begin, LocalDate end) {
        LocalDateTime start = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        BigDecimal paid = orderMapper.sumPaidAmount(shopId, start, endExclusive);
        BigDecimal refund = orderMapper.sumRefundAmount(shopId, start, endExclusive);
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