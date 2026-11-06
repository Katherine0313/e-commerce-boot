package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
import ecommerce.order.client.StockInternalClient;
import ecommerce.order.dto.AdminOrderDTO;
import ecommerce.order.dto.DeliveryCreateDTO;
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
import ecommerce.order.service.DeliveryExpressService;
import ecommerce.order.service.OrderDeliveryService;
import ecommerce.order.support.LogisticsBuilder;
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

/**
 * 后台订单管理 Service 实现。
 * <p>
 * 商家视角：查所有订单（不限用户）；
 * 状态文案用 OrderStatusEnum 的 adminDesc。
 */
@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    /** 售后单号格式：AS + 年月日时分秒（照 aftersale-service 的 generateAfterSaleNo） */
    private static final DateTimeFormatter AFTER_SALE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final AfterSaleRefundMapper afterSaleRefundMapper;
    private final OrderDeliveryService orderDeliveryService;
    private final DeliveryExpressService deliveryExpressService;
    private final LogisticsBuilder logisticsBuilder;
    private final StockInternalClient stockClient;

    public AdminOrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                 ProductMapper productMapper, AfterSaleRefundMapper afterSaleRefundMapper,
                                 OrderDeliveryService orderDeliveryService,
                                 DeliveryExpressService deliveryExpressService,
                                 LogisticsBuilder logisticsBuilder,
                                 StockInternalClient stockClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.afterSaleRefundMapper = afterSaleRefundMapper;
        this.orderDeliveryService = orderDeliveryService;
        this.deliveryExpressService = deliveryExpressService;
        this.logisticsBuilder = logisticsBuilder;
        this.stockClient = stockClient;
    }

    @Override
    public PageVO<OrderVO> listAll(int page, int pageSize, Integer status, String keyword,
                                   String startDate, String endDate, String dateField) {
        String field = resolveDateField(dateField);
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        int offset = (page - 1) * pageSize;
        // 快递公司字典只查一次，避免列表页逐单查库
        Map<String, String> courierMap = courierMap();
        List<OrderVO> list = orderMapper.listAdmin(offset, pageSize, status, keyword, start, end, field).stream()
                .map(o -> toVO(o, courierMap))
                .toList();
        long total = orderMapper.countAdmin(status, keyword, start, end, field);
        return new PageVO<>(list, total, page, pageSize);
    }

    /** 时间筛选字段白名单，防止动态列名 SQL 注入 */
    private String resolveDateField(String field) {
        if (field == null || field.isBlank()) {
            return "created_at";
        }
        if (!Set.of("created_at", "pay_at", "ship_at", "finish_at").contains(field)) {
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
        // 快递公司改读字典表（delivery_express），形状仍是 [{value,label}]，前端不用改
        return deliveryExpressService.courierOptions();
    }

    @Override
    @OperationLog(module = "order", action = "ORDER_UPDATE", bizId = "#id")
    public OrderVO updateOrder(Long id, AdminOrderDTO dto) {
        Order old = requireOrder(id);
        Order o = new Order();
        o.setId(id);
        o.setConsignee(dto.getConsignee());
        o.setPhone(dto.getPhone());
        o.setRegion(dto.getRegion());
        o.setDetailAddress(dto.getDetailAddress());
        o.setRemark(dto.getRemark());
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.update(o);
        // 变更文案：old 是改之前的订单，逐字段比只记真正变了的
        recordOrderDiff(old, dto);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO shipOrder(Long id, String courier, String trackingNumber) {
        // 老接口（PUT /api/admin/orders/{id}/ship）保留，逻辑委托给发货服务：
        // 写包裹 + 条件更新订单状态 + 写发货流水，同一事务
        DeliveryCreateDTO dto = new DeliveryCreateDTO();
        dto.setExpressCode(courier);
        dto.setTrackingNumber(trackingNumber);
        orderDeliveryService.ship(id, dto);
        return getOrder(id);
    }

    @Override
    @Transactional
    @OperationLog(module = "order", action = "ORDER_CANCEL", bizId = "#id")
    public OrderVO cancelOrder(Long id) {
        Order order = requireOrder(id);
        checkTransition(order, OrderStatusEnum.CANCEL);
        restoreStock(order.getId());
        // 已支付未发货（status 3）取消 → 自动生成退款售后单（整单退）
        if (OrderStatusEnum.PENDING_SHIP.getCode().equals(order.getOrderStatus())) {
            autoRefundOnCancel(order);
        }
        orderMapper.updateStatus(id, OrderStatusEnum.CANCEL.getCode(), null, null, null, LocalDateTime.now());
        // 变更文案：状态码翻成后台文案（待付款 → 已取消）
        LogDetail.add(LogDiff.of("订单状态", order.getOrderStatus(), OrderStatusEnum.CANCEL.getCode(),
                value -> orderStatusDesc(value == null ? null : Integer.valueOf(String.valueOf(value)))));
        return getOrder(id);
    }

    @Override
    @OperationLog(module = "order", action = "ORDER_REMOVE", bizId = "#id")
    public void deleteOrder(Long id) {
        requireOrder(id);
        orderMapper.delete(id, LocalDateTime.now());
        // 软删不是物理删除，日志里点明一下，避免审计时误以为数据消失
        LogDetail.add("删除订单（软删）");
    }

    @Override
    @OperationLog(module = "order", action = "ORDER_BATCH_REMOVE")
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ids 必须是非空数组");
        }
        int deleted = 0;
        for (Long id : ids) {
            if (orderMapper.findById(id) != null) {
                orderMapper.delete(id, LocalDateTime.now());
                deleted++;
            }
        }
        // 批量删除记条数：ids 里可能有已删/不存在的，实际删掉的才是有效操作
        LogDetail.add("批量删除订单（软删），共 " + deleted + " 条");
    }

    @Override
    public LogisticsVO getLogistics(Long id) {
        return buildLogistics(requireOrder(id));
    }

    // ==================== 内部工具 ====================

    /**
     * 记录订单信息变更文案（只记真正变了的字段）。
     * <p>
     * 为什么只比 DTO 传了的字段：orderMapper.update 是动态 set，DTO 里为 null 的字段根本不会落库，
     * 若也拿来比较，日志会写成「从【张三】修改为【空】」，属于假变更。
     * <p>
     * 手机号：比较用原值（否则只有中间位不同的号码会被误判成没变），写进 detail 用脱敏后的值。
     */
    private void recordOrderDiff(Order old, AdminOrderDTO dto) {
        if (dto.getConsignee() != null) {
            LogDetail.add(LogDiff.of("收货人", old.getConsignee(), dto.getConsignee()));
        }
        if (dto.getPhone() != null) {
            LogDetail.add(LogDiff.of("手机号", old.getPhone(), dto.getPhone(),
                    value -> maskPhone(String.valueOf(value))));
        }
        if (dto.getRegion() != null) {
            LogDetail.add(LogDiff.of("地区", old.getRegion(), dto.getRegion()));
        }
        if (dto.getDetailAddress() != null) {
            LogDetail.add(LogDiff.of("详细地址", old.getDetailAddress(), dto.getDetailAddress()));
        }
        if (dto.getRemark() != null) {
            LogDetail.add(LogDiff.of("备注", old.getRemark(), dto.getRemark()));
        }
    }

    /**
     * 手机号脱敏：保留前 3 后 4（138****5678）。
     * <p>
     * 号码不足 11 位时按比例截取，保证完整号码绝不出现在操作日志里。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "空";
        }
        String value = phone.trim();
        if (value.length() <= 4) {
            return "****";
        }
        int head = Math.min(3, value.length() - 1);
        int tail = Math.min(4, value.length() - head);
        return value.substring(0, head) + "****" + value.substring(value.length() - tail);
    }

    /** 订单状态 code → 后台中文文案 */
    private String orderStatusDesc(Integer code) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(code);
        return status == null ? String.valueOf(code) : status.getAdminDesc();
    }

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
            if (item.getSkuId() != null) {
                // SKU 级回补 → 委托 product-service 写流水
                stockClient.write(item.getSkuId(), item.getSkuCode(), "IN", "订单取消回补", item.getQuantity(), item.getId());
            } else {
                // 无 SKU 旧数据兜底
                productMapper.restoreStock(item.getProductId(), item.getQuantity());
            }
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

    /**
     * 生成假物流轨迹：轨迹逻辑见 {@link LogisticsBuilder}（C 端与后台共用一份，避免两边跑偏）。
     * 快递公司：后台优先读字典表（商家可自定义名称），读不到退回枚举，最后由 LogisticsBuilder 兜底随机。
     */
    private LogisticsVO buildLogistics(Order order) {
        return logisticsBuilder.build(order);
    }

    /** 组装订单 VO：状态文案用 adminDesc（后台视角），courier 英文 key 转中文 */
    private OrderVO toVO(Order order) {
        return toVO(order, courierMap());
    }

    /** 组装订单 VO（快递公司字典已提前查好，避免逐单查库） */
    private OrderVO toVO(Order order, Map<String, String> courierMap) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        PayTypeEnum payType = PayTypeEnum.fromCode(order.getPayType());
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
        String courierDesc = courierName(order.getCourier(), courierMap);
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
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .couponId(order.getCouponId())
                .couponName(order.getCouponName())
                .couponAmount(order.getCouponAmount())
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
                .afterSale(order.getAfterSale())
                .createdAt(order.getCreatedAt())
                .payAt(order.getPayAt())
                .shipAt(order.getShipAt())
                .finishAt(order.getFinishAt())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    /** 快递公司字典：code → 中文名 */
    private Map<String, String> courierMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, String> option : deliveryExpressService.courierOptions()) {
            map.put(option.get("value"), option.get("label"));
        }
        return map;
    }

    /** 快递公司中文名：先查字典表，查不到再退回旧的枚举 */
    private String courierName(String code, Map<String, String> courierMap) {
        if (code == null) {
            return null;
        }
        String name = courierMap.get(code);
        if (name != null) {
            return name;
        }
        CourierEnum courierEnum = CourierEnum.fromCode(code);
        return courierEnum != null ? courierEnum.getDesc() : code;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        Integer stock = null;
        if (item.getProductId() != null) {
            Product product = productMapper.findById(item.getProductId());
            stock = product == null ? null : product.getStock();
        }
        return OrderItemVO.builder()
                .orderItemId(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .image(item.getImage())
                .salesPrice(item.getSalesPrice())
                .originalPrice(item.getOriginalPrice())
                .discountAmount(item.getDiscountAmount())
                .quantity(item.getQuantity())
                .stock(stock)
                .subTotal(item.getSubTotal())
                .afterSaleStatus(item.getAfterSaleStatus())
                .afterSaleId(item.getAfterSaleId())
                .build();
    }
}
