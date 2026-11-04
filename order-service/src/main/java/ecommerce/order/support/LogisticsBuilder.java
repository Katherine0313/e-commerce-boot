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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流轨迹生成（假数据，不接真实物流 API）。
 * <p>
 * 后台管理端、小程序、PC 商城三端看的都是这一份，保证轨迹完全同步 ——
 * 不会出现「商家这边已送达、顾客那边还在派送中」。
 * <p>
 * 轨迹是【滚动时间轴】：所有节点时间都相对「当前时间」生成，每次查看都是最新状态。
 * 早先的写法是「发货时间 + 固定小时数」，发完一段时间后节点就不再更新，顾客看到的还是两天前的状态。
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

    /**
     * 生成物流轨迹。
     *
     * @param order 订单（取状态、发货时间、快递公司、单号）
     */
    public LogisticsVO build(Order order) {
        int seed = (int) (order.getId() % 97);
        String originCity = CITIES.get(seed % CITIES.size());
        String originDistrict = DISTRICTS.get((seed + 3) % DISTRICTS.size());
        String destCity = CITIES.get((seed + 5) % CITIES.size());

        // 快递公司：字典表优先（商家可自定义名称），订单上没有就兜底随机
        String courierName = courierName(order.getCourier());
        if (courierName == null || courierName.isBlank()) {
            courierName = COURIERS.get((seed + 7) % COURIERS.size());
        }
        String courierShort = courierName.replaceAll("速运|快递|速递|物流", "");
        String trackingNumber = order.getTrackingNumber() != null ? order.getTrackingNumber() : ("YT" + System.currentTimeMillis());

        // 时间基准取整点：同一小时内所有请求返回完全相同的轨迹，后台 / 小程序 / PC 商城
        // 三端看到的轨迹逐字一致（否则两次请求跨一秒时间戳就差一秒）；跨整点自动推进一次，
        // 不会像「发货时间 + 固定小时数」那样发完就停在某个节点不再更新。
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        List<LogisticsTraceVO> traces = new ArrayList<>();

        boolean shipped = OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus());
        boolean finished = OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus());

        if (shipped || finished) {
            add(traces, now.minusHours(6), "已揽件", "【" + originCity + originDistrict + "营业点】" + courierShort + "快递员已揽件，即将发往分拣中心");
            add(traces, now.minusHours(6), "运输中", "【" + originCity + originDistrict + "分拣中心】快件已完成分拣，准备发往" + destCity);
            add(traces, now.minusHours(6), "运输中", "【" + originCity + "中转部】快件已发出，正在运往" + destCity);
            add(traces, now.minusHours(4), "运输中", "快件已到达" + destCity + "中转中心");
        }
        if (finished) {
            add(traces, now.minusHours(2), "派送中", "【" + destCity + "】" + courierShort + "快递员"
                    + COURIER_STAFF[seed % COURIER_STAFF.length] + "正在为您派送，请保持电话畅通");
            add(traces, order.getFinishAt() != null ? order.getFinishAt() : now, "已签收", "快件已由本人签收，感谢使用");
        }

        return LogisticsVO.builder()
                .courier(courierName)
                .trackingNumber(trackingNumber)
                .traces(traces)
                .build();
    }

    /**
     * 快递公司中文名：字典表（delivery_express）优先，商家在后台改了名称三端同步生效；
     * 字典没有则退回枚举，最后原样返回 code。code 为空返回 null。
     */
    public String courierName(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        DeliveryExpress express = deliveryExpressMapper.findByCode(code);
        if (express != null && express.getName() != null && !express.getName().isBlank()) {
            return express.getName();
        }
        CourierEnum courierEnum = CourierEnum.fromCode(code);
        return courierEnum != null ? courierEnum.getDesc() : code;
    }

    private void add(List<LogisticsTraceVO> traces, LocalDateTime time, String status, String desc) {
        traces.add(LogisticsTraceVO.builder()
                .time(time == null ? "" : time.format(DATE_FMT))
                .status(status)
                .desc(desc)
                .build());
    }
}
