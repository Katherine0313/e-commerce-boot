package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.ReportService;
import ecommerce.order.vo.DashboardStatsVO;
import ecommerce.order.vo.TurnoverReportVO;
import ecommerce.order.vo.TurnoverVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private final OrderMapper orderMapper;

    public ReportServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
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