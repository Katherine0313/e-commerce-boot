package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AdminOrderDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.entity.Product;
import ecommerce.order.enums.CourierEnum;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.AfterSaleRefundMapper;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.service.AdminOrderService;
import ecommerce.order.vo.LogisticsTraceVO;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderItemVO;
import ecommerce.order.vo.OrderVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台订单管理 Service 实现。
 * <p>
 * 商家视角：查所有订单（不限用户）；
 * 状态文案用 OrderStatusEnum 的 adminDesc（「待确认」而非顾客视角「待发货」）。
 */
@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 售后单号格式：AS + 年月日时分秒（照 aftersale-service 的 generateAfterSaleNo） */
    private static final DateTimeFormatter AFTER_SALE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final AfterSaleRefundMapper afterSaleRefundMapper;

    public AdminOrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                 ProductMapper productMapper, AfterSaleRefundMapper afterSaleRefundMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.afterSaleRefundMapper = afterSaleRefundMapper;
    }

    @Override
    public PageVO<OrderVO> listAll(int page, int pageSize, Integer status, String keyword,
                                   String startDate, String endDate, String dateField) {
        String field = resolveDateField(dateField);
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        int offset = (page - 1) * pageSize;
        List<OrderVO> list = orderMapper.listAdmin(offset, pageSize, status, keyword, start, end, field).stream()
                .map(this::toVO)
                .toList();
        long total = orderMapper.countAdmin(status, keyword, start, end, field);
        return new PageVO<>(list, total, page, pageSize);
    }

    /** 时间筛选字段白名单，防止动态列名 SQL 注入 */
    private String resolveDateField(String field) {
        if (field == null || field.isBlank()) {
            return "created_at";
        }
        if (!Set.of("created_at", "confirm_at", "pay_at", "ship_at", "finish_at").contains(field)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "非法的日期字段");
        }
        return field;
    }

    /** 解析 yyyy-MM-dd HH:mm 为 LocalDateTime，空或格式错返回 null（不筛） */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public OrderVO getOrder(Long id) {
        return toVO(requireOrder(id));
    }

    @Override
    public Map<String, Object> getStatusConstants() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("map", OrderStatusEnum.adminDescMap());
        result.put("color", OrderStatusEnum.colorMap());
        return result;
    }

    @Override
    public List<Map<String, String>> getCouriers() {
        return java.util.Arrays.stream(CourierEnum.values())
                .map(c -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("value", c.getCode());
                    item.put("label", c.getDesc());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    public OrderVO updateOrder(Long id, AdminOrderDTO dto) {
        requireOrder(id);
        Order o = new Order();
        o.setId(id);
        o.setConsignee(dto.getConsignee());
        o.setPhone(dto.getPhone());
        o.setRegion(dto.getRegion());
        o.setDetailAddress(dto.getDetailAddress());
        o.setRemark(dto.getRemark());
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.update(o);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO confirmOrder(Long id) {
        Order order = requireOrder(id);
        checkTransition(order, OrderStatusEnum.PENDING_SHIP);
        orderMapper.updateStatus(id, OrderStatusEnum.PENDING_SHIP.getCode(), null, null, null, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO shipOrder(Long id, String courier, String trackingNumber) {
        Order order = requireOrder(id);
        checkTransition(order, OrderStatusEnum.SHIPPED);
        orderMapper.updateStatus(id, OrderStatusEnum.SHIPPED.getCode(), null, courier, trackingNumber, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO finishOrder(Long id) {
        Order order = requireOrder(id);
        checkTransition(order, OrderStatusEnum.FINISHED);
        orderMapper.updateStatus(id, OrderStatusEnum.FINISHED.getCode(), null, null, null, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO cancelOrder(Long id) {
        Order order = requireOrder(id);
        checkTransition(order, OrderStatusEnum.CANCEL);
        restoreStock(order.getId());
        // 已支付未发货（status 2/3）取消 → 自动生成退款售后单（整单退）
        if (OrderStatusEnum.PENDING_CONFIRM.getCode().equals(order.getOrderStatus())
                || OrderStatusEnum.PENDING_SHIP.getCode().equals(order.getOrderStatus())) {
            autoRefundOnCancel(order);
        }
        orderMapper.updateStatus(id, OrderStatusEnum.CANCEL.getCode(), null, null, null, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    public void deleteOrder(Long id) {
        requireOrder(id);
        orderMapper.delete(id, LocalDateTime.now());
    }

    @Override
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ids 必须是非空数组");
        }
        for (Long id : ids) {
            if (orderMapper.findById(id) != null) {
                orderMapper.delete(id, LocalDateTime.now());
            }
        }
    }

    @Override
    public LogisticsVO getLogistics(Long id) {
        return buildLogistics(requireOrder(id));
    }

    // ==================== 内部工具 ====================

    private Order requireOrder(Long id) {
        Order order = orderMapper.findById(id);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void checkTransition(Order order, OrderStatusEnum target) {
        OrderStatusEnum current = OrderStatusEnum.fromCode(order.getOrderStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "不允许从「" + (current == null ? "未知状态" : current.getAdminDesc()) + "」变更为「" + target.getAdminDesc() + "」");
        }
    }

    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId);
        for (OrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    /** 取消订单自动退款：生成 refunded 售后单（整单退，退款跟着原订单 pay_at 归属营业额） */
    private void autoRefundOnCancel(Order order) {
        // 已有售后单则跳过（退款已走售后流程，不重复退）
        if (afterSaleRefundMapper.countAfterSale(order.getId()) > 0) {
            return;
        }
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
        if (items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order.getId());
        params.put("orderNo", order.getOrderNo());
        params.put("afterSaleNo", "AS" + now.format(AFTER_SALE_NO_FORMATTER));
        params.put("now", now);
        afterSaleRefundMapper.insertAfterSale(params);
        Long afterSaleId = ((Number) params.get("id")).longValue();
        afterSaleRefundMapper.insertItems(afterSaleId, items, now);
        afterSaleRefundMapper.insertOnlyRefund(afterSaleId, now);
    }

    /** 生成假物流轨迹（照旧 Node getLogistics）：订单 id 做种子，courier 英文 key 转中文 */
    private LogisticsVO buildLogistics(Order order) {
        int seed = (int) (order.getId() % 97);
        List<String> cities = List.of("杭州", "北京", "上海", "广州", "深圳", "成都", "武汉", "南京", "西安", "重庆");
        List<String> districts = List.of("萧山", "朝阳", "浦东", "天河", "南山", "武侯", "洪山", "江宁", "雁塔", "渝北");
        String originCity = cities.get(seed % cities.size());
        String originDistrict = districts.get((seed + 3) % districts.size());
        String destCity = cities.get((seed + 5) % cities.size());

        CourierEnum courierEnum = CourierEnum.fromCode(order.getCourier());
        String courierName = courierEnum != null ? courierEnum.getDesc()
                : List.of("顺丰速运", "中通快递", "圆通速递", "韵达快递", "申通快递", "极兔速递", "京东物流", "德邦快递").get((seed + 7) % 8);
        String courierShort = courierName.replaceAll("速运|快递|速递|物流", "");
        String trackingNumber = order.getTrackingNumber() != null ? order.getTrackingNumber() : ("YT" + System.currentTimeMillis());

        List<LogisticsTraceVO> traces = new ArrayList<>();
        LocalDateTime shipTime = order.getShipAt();
        LocalDateTime finishTime = order.getFinishAt();
        String[] courierStaff = {"张伟", "李强", "王磊", "刘洋", "陈明"};

        if (OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus())) {
            traces.add(trace(shipTime, "已揽件", "【" + originCity + originDistrict + "营业点】" + courierShort + "快递员已揽件，即将发往分拣中心"));
            traces.add(trace(plusHours(shipTime, 2, seed), "运输中", "【" + originCity + "分拣中心】快件已完成分拣，准备发往" + destCity));
            traces.add(trace(plusHours(shipTime, 6, seed), "运输中", "【" + originCity + "中转部】快件已发出，正在运往" + destCity));
            traces.add(trace(plusHours(shipTime, 18, seed), "运输中", "快件已到达" + destCity + "中转中心"));
            traces.add(trace(null, "运输中", "【" + destCity + "分拣中心】快件正在分拣，即将安排派送"));
        } else if (OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())) {
            traces.add(trace(shipTime, "已揽件", "【" + originCity + originDistrict + "营业点】" + courierShort + "快递员已揽件，即将发往分拣中心"));
            traces.add(trace(plusHours(shipTime, 2, seed), "运输中", "【" + originCity + "分拣中心】快件已完成分拣，准备发往" + destCity));
            traces.add(trace(plusHours(shipTime, 6, seed), "运输中", "【" + originCity + "中转部】快件已发出，正在运往" + destCity));
            traces.add(trace(plusHours(shipTime, 18, seed), "运输中", "快件已到达" + destCity + "中转中心"));
            traces.add(trace(plusHours(shipTime, 22, seed), "运输中", "【" + destCity + "分拣中心】快件正在分拣，即将安排派送"));
            traces.add(trace(plusHours(shipTime, 24, seed), "派送中", "【" + destCity + "】" + courierShort + "快递员" + courierStaff[seed % courierStaff.length] + "正在为您派送，请保持电话畅通"));
            traces.add(trace(finishTime, "已签收", "快件已由本人签收，感谢使用"));
        }

        return LogisticsVO.builder()
                .courier(courierName)
                .trackingNumber(trackingNumber)
                .traces(traces)
                .build();
    }

    private LogisticsTraceVO trace(LocalDateTime time, String status, String desc) {
        return LogisticsTraceVO.builder()
                .time(time == null ? "" : time.format(DATE_FMT))
                .status(status)
                .desc(desc)
                .build();
    }

    private LocalDateTime plusHours(LocalDateTime base, long hours, int seed) {
        return base == null ? null : base.plusHours(hours + seed % 7);
    }

    /** 组装订单 VO：状态文案用 adminDesc（后台视角），courier 英文 key 转中文 */
    private OrderVO toVO(Order order) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        PayTypeEnum payType = PayTypeEnum.fromCode(order.getPayType());
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
        CourierEnum courierEnum = CourierEnum.fromCode(order.getCourier());
        String courierDesc = courierEnum != null ? courierEnum.getDesc() : order.getCourier();
        // 已发货/已完成订单附物流摘要，列表卡片直接展示
        String logisticsStatus = null;
        String latestTrace = null;
        if (OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus())
                || OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())) {
            List<LogisticsTraceVO> traces = buildLogistics(order).getTraces();
            if (!traces.isEmpty()) {
                LogisticsTraceVO latest = traces.get(traces.size() - 1);
                logisticsStatus = latest.getStatus();
                latestTrace = latest.getDesc();
            }
        }
        return OrderVO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .orderNo(order.getOrderNo())
                .payAmount(order.getPayAmount())
                .payType(order.getPayType())
                .payTypeDesc(payType == null ? null : payType.getDesc())
                .orderStatus(order.getOrderStatus())
                .orderStatusDesc(status == null ? null : status.getAdminDesc())
                .remark(order.getRemark())
                .consignee(order.getConsignee())
                .phone(order.getPhone())
                .region(order.getRegion())
                .detailAddress(order.getDetailAddress())
                .shippingType(order.getShippingType())
                .courier(courierDesc)
                .trackingNumber(order.getTrackingNumber())
                .signStatus(order.getSignStatus())
                .logisticsStatus(logisticsStatus)
                .latestTrace(latestTrace)
                .createdAt(order.getCreatedAt())
                .payAt(order.getPayAt())
                .shipAt(order.getShipAt())
                .confirmAt(order.getConfirmAt())
                .finishAt(order.getFinishAt())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    private OrderItemVO toItemVO(OrderItem item) {
        Integer stock = null;
        if (item.getProductId() != null) {
            Product product = productMapper.findById(item.getProductId());
            stock = product == null ? null : product.getStock();
        }
        return OrderItemVO.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .image(item.getImage())
                .salesPrice(item.getSalesPrice())
                .quantity(item.getQuantity())
                .stock(stock)
                .subTotal(item.getSubTotal())
                .build();
    }
}
