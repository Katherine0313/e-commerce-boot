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
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = today.atStartOfDay();

        LocalDateTime start;
        LocalDateTime end;
        String timeDesc;
        switch (period == null ? "" : period) {
            case "yesterday" -> {
                // 昨日：完整自然日 [昨天0点, 今天0点)
                start = todayStart.minusDays(1);
                end = todayStart;
                timeDesc = "昨日";
            }
            case "week" -> {
                // 近7天（含今天）：[今天-6天0点, now)
                start = todayStart.minusDays(6);
                end = now;
                timeDesc = "近7天";
            }
            case "month" -> {
                // 本月：[月初0点, now)
                start = today.withDayOfMonth(1).atStartOfDay();
                end = now;
                timeDesc = "本月";
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "非法的统计维度");
        }

        return TurnoverVO.builder()
                .turnover(turnoverOf(shopId, start, end))
                .timeDesc(timeDesc)
                .build();
    }

    @Override
    public TurnoverReportVO getTurnoverReport(String period) {
        Long shopId = requireShopId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = today.atStartOfDay();

        LocalDateTime start;
        LocalDateTime end;
        List<LocalDate> dates = new ArrayList<>();
        switch (period == null ? "" : period) {
            case "yesterday" -> {
                start = todayStart.minusDays(1);
                end = todayStart;
                dates.add(today.minusDays(1));
            }
            case "week" -> {
                start = todayStart.minusDays(6);
                end = now;
                for (int i = 6; i >= 0; i--) {
                    dates.add(today.minusDays(i));
                }
            }
            case "month" -> {
                start = today.withDayOfMonth(1).atStartOfDay();
                end = now;
                LocalDate d = today.withDayOfMonth(1);
                while (!d.isAfter(today)) {
                    dates.add(d);
                    d = d.plusDays(1);
                }
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "非法的统计维度");
        }

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
                .todayTurnover(turnoverOf(shopId, todayStart, tomorrowStart))
                .yesterdayTurnover(turnoverOf(shopId, yesterdayStart, todayStart))
                // 订单量按 created_at，排除已取消（与工作台原前端口径一致）
                .todayOrders(orderMapper.countCreatedBetween(shopId, todayStart, tomorrowStart))
                .yesterdayOrders(orderMapper.countCreatedBetween(shopId, yesterdayStart, todayStart))
                // 待发货 = 状态 3
                .pendingShip(orderMapper.countByStatus(shopId, OrderStatusEnum.PENDING_SHIP.getCode()))
                .build();
    }

    /** 单区间营业额 = 实收 − 退款（时间用 JVM 本地时区，与 pay_at 同基准） */
    private BigDecimal turnoverOf(Long shopId, LocalDateTime start, LocalDateTime end) {
        BigDecimal paid = orderMapper.sumPaidAmount(shopId, start, end);
        BigDecimal refund = orderMapper.sumRefundAmount(shopId, start, end);
        return (paid == null ? BigDecimal.ZERO : paid).subtract(refund == null ? BigDecimal.ZERO : refund);
    }

    /** 分组查询结果 [{date_key, amount}] 转 Map<日期, 金额> */
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
