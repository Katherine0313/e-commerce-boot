package ecommerce.order.support;

import ecommerce.order.entity.DeliveryExpress;
import ecommerce.order.entity.Order;
import ecommerce.order.enums.CourierEnum;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.DeliveryExpressMapper;
import ecommerce.order.vo.LogisticsTraceVO;
import ecommerce.order.vo.LogisticsVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流轨迹生成（假数据，不接真实物流 API）。
 * <p>
 * 后台管理端、小程序、PC 商城三端看的都是这一份，保证轨迹完全同步 ——
 * 不会出现「商家这边已送达、顾客那边还在派送中」。
 * <p>
 * 轨迹以订单实际发货时间为起点，按真实时效生成，且只展示已经发生的节点。
 */
@Component
public class LogisticsBuilder {

    /** 轨迹时间展示格式 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 起始城市池（按订单 id 取种子，保证同一订单每次看到的城市一致） */
    private static final List<String> CITIES = List.of("杭州", "北京", "上海", "广州", "深圳", "成都", "武汉", "南京", "西安", "重庆");
    /** 起始区县池 */
    private static final List<String> DISTRICTS = List.of("萧山", "朝阳", "浦东", "天河", "南山", "武侯", "洪山", "江宁", "雁塔", "渝北");
    /** 快递公司兜底列表（订单上没快递公司时随机取一个） */
    private static final List<String> COURIERS = List.of("顺丰速运", "中通快递", "圆通速递", "韵达快递", "申通快递", "极兔速递", "京东物流", "德邦快递");
    /** 派送员名字池 */
    private static final String[] COURIER_STAFF = {"张伟", "李强", "王磊", "刘洋", "陈明"};

    private final DeliveryExpressMapper deliveryExpressMapper;

    public LogisticsBuilder(DeliveryExpressMapper deliveryExpressMapper) {
        this.deliveryExpressMapper = deliveryExpressMapper;
    }

    /** 轨迹节点相对【发货时间】的小时偏移：揽件 → 分拣 → 发出 → 到达 → 派送 */
    private static final int[] OFFSETS = {5, 5, 5, 12, 20};

    /**
     * 生成物流轨迹。
     * <p>
     * 时间以订单实际发货时间 {@code shipAt} 为起点向后推，只展示「已经发生」的节点，
     * 因此不会出现揽件时间早于发货时间；目的地取收货地址 {@code region}，不再随机。
     *
     * @param order 订单（取状态、发货时间、快递公司、单号、收货地区）
     */
    public LogisticsVO build(Order order) {
        return build(order, null);
    }

    /**
     * 生成物流轨迹（不查库版本）：courierMap 由调用方预先一次性加载（code → 中文名），
     * 供订单列表批量装配复用，避免逐单查快递字典。
     *
     * @param order      订单
     * @param courierMap 快递字典；为 null 时退回逐单查库（单条详情路径沿用）
     */
    public LogisticsVO build(Order order, Map<String, String> courierMap) {
        int seed = (int) (order.getId() % 97);
        String originCity = CITIES.get(seed % CITIES.size());
        String originDistrict = DISTRICTS.get((seed + 3) % DISTRICTS.size());
        String destCity = resolveDestination(order.getRegion(), seed);

        // 快递公司：字典表优先（商家可自定义名称），订单上没有就兜底随机
        String courierName = courierName(order.getCourier(), courierMap);
        if (courierName == null || courierName.isBlank()) {
            courierName = COURIERS.get((seed + 7) % COURIERS.size());
        }
        String courierShort = courierName.replaceAll("速运|快递|速递|物流", "");
        String trackingNumber = order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()
                ? order.getTrackingNumber()
                : String.format("MOCK%08d", order.getId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = order.getShipAt() != null ? order.getShipAt()
                : order.getCreatedAt() != null ? order.getCreatedAt() : now;
        if (base.isAfter(now)) {
            base = now;
        }

        boolean shipped = OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus());
        boolean finished = OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus());

        // 已完成订单以签收时间为终点，进行中订单以当前时间为终点；终点不能超过现在
        LocalDateTime end = finished
                ? (order.getFinishAt() != null ? order.getFinishAt() : now)
                : now;
        if (end.isAfter(now)) {
            end = now;
        }
        if (end.isBefore(base)) {
            end = base;
        }

        List<LogisticsTraceVO> traces = new ArrayList<>();
        if (shipped || finished) {
            // 快递员还没揽件时只显示“待揽件”，不要伪造后续的运输/派送节点
            LocalDateTime pickupTime = base.plusHours(OFFSETS[0]);
            boolean picked = finished || !pickupTime.isAfter(end);
            if (!picked) {
                add(traces, base, "待揽件", "【" + originCity + originDistrict + "营业点】商家已发货，等待"
                        + courierShort + "快递员揽件");
            } else {
                addIfReached(traces, base.plusHours(OFFSETS[0]), end, "已揽件", "【" + originCity + originDistrict + "营业点】" + courierShort + "快递员已揽件，即将发往分拣中心");
                addIfReached(traces, base.plusHours(OFFSETS[1]), end, "运输中", "【" + originCity + originDistrict + "分拣中心】快件已完成分拣，准备发往" + destCity);
                addIfReached(traces, base.plusHours(OFFSETS[2]), end, "运输中", "【" + originCity + "中转部】快件已发出，正在运往" + destCity);
                addIfReached(traces, base.plusHours(OFFSETS[3]), end, "运输中", "快件已到达" + destCity + "中转中心");
                addIfReached(traces, base.plusHours(OFFSETS[4]), end, "派送中", "【" + destCity + "】" + courierShort + "快递员"
                        + COURIER_STAFF[seed % COURIER_STAFF.length] + "正在为您派送，请保持电话畅通");
            }
        }
        if (finished) {
            add(traces, end, "已签收", "快件已由本人签收，感谢使用");
        }

        return LogisticsVO.builder()
                .courier(courierName)
                .trackingNumber(trackingNumber)
                .traces(traces)
                .build();
    }

    /** 目的地城市：优先从收货地址解析，解析不到再按订单 id 兜底 */
    private String resolveDestination(String region, int seed) {
        if (region != null && !region.isBlank()) {
            String s = region.replace("特别行政区", "|").replace("自治区", "|").replace("省", "|");
            int bar = s.lastIndexOf('|');
            if (bar >= 0) {
                s = s.substring(bar + 1);
            }
            int idx = s.indexOf('市');
            if (idx > 0) {
                return s.substring(0, idx);
            }
        }
        return CITIES.get((seed + 5) % CITIES.size());
    }

    /** 仅当节点时间已到（不晚于终点）才追加，避免展示未来轨迹 */
    private void addIfReached(List<LogisticsTraceVO> traces, LocalDateTime time, LocalDateTime end, String status, String desc) {
        if (!time.isAfter(end)) {
            add(traces, time, status, desc);
        }
    }

    /**
     * 快递公司中文名：字典表（delivery_express）优先，商家在后台改了名称三端同步生效；
     * 字典没有则退回枚举，最后原样返回 code。code 为空返回 null。
     */
    public String courierName(String code) {
        return courierName(code, null);
    }

    /**
     * 快递公司中文名（不查库版本）：courierMap 不为 null 时只读内存字典，缺失则退回枚举/原编码；
     * 为 null 时按原有逻辑逐单查字典表。
     */
    public String courierName(String code, Map<String, String> courierMap) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (courierMap != null) {
            String name = courierMap.get(code);
            if (name != null && !name.isBlank()) {
                return name;
            }
            CourierEnum courierEnum = CourierEnum.fromCode(code);
            return courierEnum != null ? courierEnum.getDesc() : code;
        }
        DeliveryExpress express = deliveryExpressMapper.findByCode(code);
        if (express != null && express.getName() != null && !express.getName().isBlank()) {
            return express.getName();
        }
        CourierEnum courierEnum = CourierEnum.fromCode(code);
        return courierEnum != null ? courierEnum.getDesc() : code;
    }

    /** 一次性加载快递字典（code → 中文名），供订单列表批量装配复用 */
    public Map<String, String> courierNameMap() {
        Map<String, String> map = new HashMap<>();
        for (DeliveryExpress express : deliveryExpressMapper.listAll()) {
            if (express.getCode() != null && express.getName() != null && !express.getName().isBlank()) {
                map.put(express.getCode(), express.getName());
            }
        }
        return map;
    }

    private void add(List<LogisticsTraceVO> traces, LocalDateTime time, String status, String desc) {
        traces.add(LogisticsTraceVO.builder()
                .time(time == null ? "" : time.format(DATE_FMT))
                .status(status)
                .desc(desc)
                .build());
    }
}
