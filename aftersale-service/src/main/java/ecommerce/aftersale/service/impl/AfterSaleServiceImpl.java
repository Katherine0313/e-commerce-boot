package ecommerce.aftersale.service.impl;

import ecommerce.aftersale.dto.CreateAfterSaleDTO;
import ecommerce.aftersale.dto.SubmitReturnDTO;
import ecommerce.aftersale.entity.AfterSale;
import ecommerce.aftersale.entity.AfterSaleExchangeReturn;
import ecommerce.aftersale.entity.AfterSaleItem;
import ecommerce.aftersale.entity.AfterSaleOnlyRefund;
import ecommerce.aftersale.entity.AfterSaleReturn;
import ecommerce.aftersale.entity.Order;
import ecommerce.aftersale.entity.OrderItem;
import ecommerce.aftersale.enums.AfterSaleGoodsStatusEnum;
import ecommerce.aftersale.enums.AfterSaleReasonEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;
import ecommerce.aftersale.enums.CourierEnum;
import ecommerce.aftersale.enums.PayTypeEnum;
import ecommerce.aftersale.mapper.AfterSaleExchangeReturnMapper;
import ecommerce.aftersale.mapper.AfterSaleItemMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.mapper.AfterSaleOnlyRefundMapper;
import ecommerce.aftersale.mapper.AfterSaleReturnMapper;
import ecommerce.aftersale.mapper.OrderMapper;
import ecommerce.aftersale.service.AfterSaleService;
import ecommerce.aftersale.vo.AfterSaleItemVO;
import ecommerce.aftersale.vo.AfterSaleStatusConstantsVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 售后 Service 实现。
 * <p>
 * 设计照原 Node 后端，但修正了「C 端无用户归属隔离」的问题：所有 C 端查询/操作都通过
 * order_id LEFT JOIN orders 带出的 user_id 校验归属，用户只能看/操作自己的售后单。
 */
@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {

    /** 模板店（杰米小店）：多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;
    /** 售后单号格式：AS + 年月日时分秒（照原 Node） */
    private static final DateTimeFormatter AFTER_SALE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 订单状态数字 code（对齐 order-service 的 OrderStatusEnum）：2/3=未发货 4=已发货 5=已完成 */
    private static final int ORDER_STATUS_PENDING_CONFIRM = 2;
    private static final int ORDER_STATUS_PENDING_SHIP = 3;
    private static final int ORDER_STATUS_SHIPPED = 4;
    private static final int ORDER_STATUS_FINISHED = 5;

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final AfterSaleReturnMapper afterSaleReturnMapper;
    private final AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper;
    private final AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public AfterSaleServiceImpl(AfterSaleMapper afterSaleMapper, AfterSaleItemMapper afterSaleItemMapper,
                                AfterSaleReturnMapper afterSaleReturnMapper, AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper,
                                AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper, OrderMapper orderMapper,
                                ObjectMapper objectMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.afterSaleReturnMapper = afterSaleReturnMapper;
        this.afterSaleOnlyRefundMapper = afterSaleOnlyRefundMapper;
        this.afterSaleExchangeReturnMapper = afterSaleExchangeReturnMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AfterSaleVO create(CreateAfterSaleDTO dto) {
        if (dto.getOrderId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单 ID 不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后原因不能为空");
        }
        if (dto.getGoodsStatus() != null && AfterSaleGoodsStatusEnum.fromCode(dto.getGoodsStatus()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "货物状态不合法");
        }
        String type = dto.getAfterSaleType() == null || dto.getAfterSaleType().isBlank()
                ? AfterSaleTypeEnum.RETURN_REFUND.getCode() : dto.getAfterSaleType();
        if (AfterSaleTypeEnum.fromCode(type) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后类型不合法");
        }
        // 退货退款必填退货方式；上门取件还需选时段
        if (AfterSaleTypeEnum.RETURN_REFUND.getCode().equals(type)) {
            if (!"pickup".equals(dto.getReturnWay()) && !"self".equals(dto.getReturnWay())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择退货方式");
            }
            if ("pickup".equals(dto.getReturnWay())
                    && !"morning".equals(dto.getReturnTimeSlot()) && !"afternoon".equals(dto.getReturnTimeSlot())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "上门取件请选择取件时段");
            }
        }

        Long userId = currentUserId();

        // 查订单 + 校验归属（修正原 Node 越权问题）
        Order order = orderMapper.findById(dto.getOrderId(), TEMPLATE_SHOP_ID);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
        // 状态校验：仅退款允许未发货(2/3)/已发货(4)；退货退款/换货要求已发货(4)/已完成(5)
        int orderStatus = order.getOrderStatus() == null ? -1 : order.getOrderStatus();
        boolean onlyRefund = AfterSaleTypeEnum.ONLY_REFUND.getCode().equals(type);
        boolean statusOk = onlyRefund
                ? (orderStatus == ORDER_STATUS_PENDING_CONFIRM || orderStatus == ORDER_STATUS_PENDING_SHIP
                    || orderStatus == ORDER_STATUS_SHIPPED)
                : (orderStatus == ORDER_STATUS_SHIPPED || orderStatus == ORDER_STATUS_FINISHED);
        if (!statusOk) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前订单状态不支持该售后类型");
        }
        // 同一订单不允许重复申请
        if (afterSaleMapper.findByOrderId(dto.getOrderId(), TEMPLATE_SHOP_ID) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在售后申请，请勿重复提交");
        }

        // 快照订单明细：传了 orderItemId 则只退单个商品，否则整单
        List<OrderItem> orderItems;
        if (dto.getOrderItemId() != null) {
            OrderItem item = orderMapper.findItemById(dto.getOrderItemId(), dto.getOrderId(), TEMPLATE_SHOP_ID);
            if (item == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "订单项不存在");
            }
            orderItems = List.of(item);
        } else {
            orderItems = orderMapper.listItems(dto.getOrderId(), TEMPLATE_SHOP_ID);
        }
        if (orderItems.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单无明细，无法申请售后");
        }

        LocalDateTime now = LocalDateTime.now();

        // 写主表（初始状态 reviewing）
        AfterSale afterSale = AfterSale.builder()
                .shopId(TEMPLATE_SHOP_ID)
                .orderId(dto.getOrderId())
                .orderNo(order.getOrderNo())
                .afterSaleNo(generateAfterSaleNo())
                .afterSaleType(type)
                .status(AfterSaleStatusEnum.REVIEWING.getCode())
                .reason(dto.getReason())
                .goodsStatus(dto.getGoodsStatus())
                .remark(dto.getRemark() == null ? "" : dto.getRemark())
                .images(toImagesJson(dto.getImages()))
                .merchantRemark("")
                .createdAt(now)
                .updatedAt(now)
                .build();
        afterSaleMapper.insert(afterSale);

        // 写子表占位行（按类型路由；退货退款一并写退货方式）
        insertSubPlaceholder(afterSale.getId(), type, dto.getReturnWay(), dto.getReturnTimeSlot(), dto.getShipAddress(), now);

        // 写售后明细（快照）
        List<AfterSaleItem> items = orderItems.stream()
                .map(oi -> AfterSaleItem.builder()
                        .shopId(TEMPLATE_SHOP_ID)
                        .afterSaleId(afterSale.getId())
                        .orderItemId(oi.getId())
                        .productId(oi.getProductId())
                        .productName(oi.getProductName())
                        .image(oi.getImage())
                        .salesPrice(oi.getSalesPrice())
                        .quantity(oi.getQuantity())
                        .refundAmount(oi.getSubTotal() == null ? BigDecimal.ZERO : oi.getSubTotal())
                        .insuranceDetail(oi.getInsuranceDetail() == null ? "[]" : oi.getInsuranceDetail())
                        .insuranceFee(oi.getInsuranceFee() == null ? BigDecimal.ZERO : oi.getInsuranceFee())
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        afterSaleItemMapper.insertBatch(items);

        return buildDetail(afterSale.getId());
    }

    @Override
    public List<AfterSaleVO> list(String status, String search, String startDate, String endDate, String dateField) {
        String field = resolveDateField(dateField);
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        return afterSaleMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID, status, search, start, end, field).stream()
                .map(a -> toVO(a, afterSaleItemMapper.listByAfterSaleId(a.getId(), TEMPLATE_SHOP_ID), null))
                .toList();
    }

    /** 时间筛选字段白名单，防止动态列名 SQL 注入（after_sales 表只有这两个时间列） */
    private String resolveDateField(String field) {
        if (field == null || field.isBlank()) {
            return "created_at";
        }
        if (!Set.of("created_at", "updated_at").contains(field)) {
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
    public AfterSaleVO getById(Long id) {
        return buildDetail(id);
    }

    @Override
    public AfterSaleVO getByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        AfterSale afterSale = afterSaleMapper.findByOrderId(orderId, TEMPLATE_SHOP_ID);
        if (afterSale == null || !currentUserId().equals(afterSale.getUserId())) {
            return null;
        }
        return toVO(afterSale, null, null);
    }

    @Override
    @Transactional
    public AfterSaleVO submitReturn(Long id, SubmitReturnDTO dto) {
        AfterSale afterSale = getOwnedAfterSale(id);
        // 仅退款不退货，不调本接口
        if (AfterSaleTypeEnum.ONLY_REFUND.getCode().equals(afterSale.getAfterSaleType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅退款售后无需回填物流");
        }
        // 状态必须是 returning
        if (!AfterSaleStatusEnum.RETURNING.getCode().equals(afterSale.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前状态不允许提交");
        }
        // 是否已提交过（return_at 非空即已提交/回填）
        if (isReturnSubmitted(afterSale.getAfterSaleType(), id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已提交过，不能重复提交");
        }

        LocalDateTime now = LocalDateTime.now();
        if (AfterSaleTypeEnum.RETURN_REFUND.getCode().equals(afterSale.getAfterSaleType())) {
            // 退货退款：退货方式在申请时已选（create 存子表），这里回填物流；自行寄回必填单号
            AfterSaleReturn sub = afterSaleReturnMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
            if (sub == null || sub.getReturnWay() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "请先选择退货方式");
            }
            String courier = null;
            String trackingNumber = null;
            if ("self".equals(sub.getReturnWay())) {
                if (dto.getCourier() == null || dto.getCourier().isBlank()
                        || dto.getTrackingNumber() == null || dto.getTrackingNumber().isBlank()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写退货物流单号");
                }
                courier = dto.getCourier();
                trackingNumber = dto.getTrackingNumber();
            }
            afterSaleReturnMapper.updateTracking(id, TEMPLATE_SHOP_ID, courier, trackingNumber, now, now);
            afterSaleMapper.updateStatus(id, TEMPLATE_SHOP_ID, AfterSaleStatusEnum.REFUNDING.getCode(), now);
        } else {
            // 换货：提交退货方式 + 回填物流（自行寄回填单号）
            String returnWay = dto.getReturnWay();
            String returnTimeSlot = dto.getReturnTimeSlot();
            if (!"pickup".equals(returnWay) && !"self".equals(returnWay)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "return_way 必须是 pickup 或 self");
            }
            if ("pickup".equals(returnWay) && !"morning".equals(returnTimeSlot) && !"afternoon".equals(returnTimeSlot)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "上门取件必须指定时间段（morning/afternoon）");
            }
            String courier = null;
            String trackingNumber = null;
            if ("self".equals(returnWay)) {
                if (dto.getCourier() == null || dto.getCourier().isBlank()
                        || dto.getTrackingNumber() == null || dto.getTrackingNumber().isBlank()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写退货物流单号");
                }
                courier = dto.getCourier();
                trackingNumber = dto.getTrackingNumber();
            }
            afterSaleExchangeReturnMapper.updateReturnInfo(id, TEMPLATE_SHOP_ID, returnWay, returnTimeSlot, now, now);
            afterSaleExchangeReturnMapper.updateTracking(id, TEMPLATE_SHOP_ID, courier, trackingNumber, now, now);
            afterSaleMapper.updateStatus(id, TEMPLATE_SHOP_ID, afterSale.getStatus(), now);
        }

        return buildDetail(id);
    }

    @Override
    public AfterSaleStatusConstantsVO statusConstants() {
        // 状态/颜色照原 Node dict.js（含废弃的 returned，保证前端字段名与取值完全一致）
        Map<String, String> statusMap = new LinkedHashMap<>();
        statusMap.put("reviewing", "商家审核中");
        statusMap.put("refunding", "退款中");
        statusMap.put("refund_confirming", "确认退款中");
        statusMap.put("exchanging", "换货中");
        statusMap.put("returning", "退货中");
        statusMap.put("refunded", "已退款");
        statusMap.put("exchanged", "已换货");
        statusMap.put("returned", "已退货");
        statusMap.put("rejected", "售后拒绝");

        Map<String, String> colorMap = new LinkedHashMap<>();
        colorMap.put("reviewing", "processing");
        colorMap.put("refunding", "processing");
        colorMap.put("refund_confirming", "processing");
        colorMap.put("exchanging", "processing");
        colorMap.put("returning", "processing");
        colorMap.put("refunded", "success");
        colorMap.put("exchanged", "success");
        colorMap.put("returned", "success");
        colorMap.put("rejected", "error");

        Map<String, String> reasonMap = new LinkedHashMap<>();
        Map<String, String> unshippedReasonMap = new LinkedHashMap<>();
        Map<String, String> shippedReasonMap = new LinkedHashMap<>();
        for (AfterSaleReasonEnum r : AfterSaleReasonEnum.values()) {
            reasonMap.put(r.getCode(), r.getDesc());
            if (r.isShipped()) {
                shippedReasonMap.put(r.getCode(), r.getDesc());
            } else {
                unshippedReasonMap.put(r.getCode(), r.getDesc());
            }
        }
        Map<String, String> typeMap = new LinkedHashMap<>();
        for (AfterSaleTypeEnum t : AfterSaleTypeEnum.values()) {
            typeMap.put(t.getCode(), t.getDesc());
        }
        Map<String, String> goodsStatusMap = new LinkedHashMap<>();
        for (AfterSaleGoodsStatusEnum g : AfterSaleGoodsStatusEnum.values()) {
            goodsStatusMap.put(g.getCode(), g.getDesc());
        }

        return AfterSaleStatusConstantsVO.builder()
                .map(statusMap)
                .color(colorMap)
                .reasonMap(reasonMap)
                .unshippedReasonMap(unshippedReasonMap)
                .shippedReasonMap(shippedReasonMap)
                .typeMap(typeMap)
                .goodsStatusMap(goodsStatusMap)
                .build();
    }

    // ==================== 内部工具 ====================

    /** 查售后并校验归属（当前用户只能操作自己的售后单） */
    private AfterSale getOwnedAfterSale(Long id) {
        AfterSale afterSale = afterSaleMapper.findById(id, TEMPLATE_SHOP_ID);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        if (!currentUserId().equals(afterSale.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该售后单");
        }
        return afterSale;
    }

    /** 组装售后详情（主表 + 明细 + 子表平铺字段） */
    private AfterSaleVO buildDetail(Long id) {
        AfterSale afterSale = getOwnedAfterSale(id);
        List<AfterSaleItem> items = afterSaleItemMapper.listByAfterSaleId(id, TEMPLATE_SHOP_ID);
        SubInfo sub = loadSub(afterSale.getAfterSaleType(), id);
        return toVO(afterSale, items, sub);
    }

    /** 按售后类型路由到对应子表，读取全部时间戳/退货字段平铺 */
    private SubInfo loadSub(String type, Long id) {
        SubInfo sub = new SubInfo();
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return sub;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> {
                AfterSaleReturn r = afterSaleReturnMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
                if (r != null) {
                    sub.returnWay = r.getReturnWay();
                    sub.returnTimeSlot = r.getReturnTimeSlot();
                    sub.returnCourier = r.getReturnCourier();
                    sub.returnTrackingNumber = r.getReturnTrackingNumber();
                    sub.returnAt = r.getReturnAt();
                    sub.reviewAt = r.getReviewAt();
                    sub.rejectedAt = r.getRejectedAt();
                    sub.refundAt = r.getRefundAt();
                    sub.refundedAt = r.getRefundedAt();
                }
            }
            case ONLY_REFUND -> {
                AfterSaleOnlyRefund r = afterSaleOnlyRefundMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
                if (r != null) {
                    sub.reviewAt = r.getReviewAt();
                    sub.rejectedAt = r.getRejectedAt();
                    sub.refundAt = r.getRefundAt();
                    sub.refundedAt = r.getRefundedAt();
                }
            }
            case EXCHANGE -> {
                AfterSaleExchangeReturn r = afterSaleExchangeReturnMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
                if (r != null) {
                    sub.returnWay = r.getReturnWay();
                    sub.returnTimeSlot = r.getReturnTimeSlot();
                    sub.returnCourier = r.getReturnCourier();
                    sub.returnTrackingNumber = r.getReturnTrackingNumber();
                    sub.shipAddress = r.getShipAddress();
                    sub.returnAt = r.getReturnAt();
                    sub.reviewAt = r.getReviewAt();
                    sub.rejectedAt = r.getRejectedAt();
                    sub.exchangeAt = r.getExchangeAt();
                    sub.exchangedAt = r.getExchangedAt();
                }
            }
        }
        return sub;
    }

    /** 按类型写子表占位行；退货退款一并写退货方式 */
    private void insertSubPlaceholder(Long afterSaleId, String type, String returnWay, String returnTimeSlot, String shipAddress, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.insertPlaceholder(
                    AfterSaleReturn.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId)
                            .returnWay(returnWay).returnTimeSlot(returnTimeSlot).createdAt(now).updatedAt(now).build());
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.insertPlaceholder(
                    AfterSaleOnlyRefund.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId).createdAt(now).updatedAt(now).build());
            case EXCHANGE -> afterSaleExchangeReturnMapper.insertPlaceholder(
                    AfterSaleExchangeReturn.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId)
                            .shipAddress(shipAddress).createdAt(now).updatedAt(now).build());
        }
    }

    /** 是否已提交过退货方式（return_at 非空） */
    private boolean isReturnSubmitted(String type, Long id) {
        if (AfterSaleTypeEnum.EXCHANGE.getCode().equals(type)) {
            AfterSaleExchangeReturn r = afterSaleExchangeReturnMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
            return r != null && r.getReturnAt() != null;
        }
        AfterSaleReturn r = afterSaleReturnMapper.findByAfterSaleId(id, TEMPLATE_SHOP_ID);
        return r != null && r.getReturnAt() != null;
    }

    /** 组装 VO。items/sub 为 null 时（列表场景）不返回明细与子表字段 */
    private AfterSaleVO toVO(AfterSale a, List<AfterSaleItem> items, SubInfo sub) {
        AfterSaleStatusEnum status = AfterSaleStatusEnum.fromCode(a.getStatus());
        AfterSaleTypeEnum type = AfterSaleTypeEnum.fromCode(a.getAfterSaleType());
        AfterSaleReasonEnum reason = AfterSaleReasonEnum.fromCode(a.getReason());
        AfterSaleGoodsStatusEnum goodsStatus = AfterSaleGoodsStatusEnum.fromCode(a.getGoodsStatus());
        PayTypeEnum payType = PayTypeEnum.fromCode(a.getPayType());
        CourierEnum courier = CourierEnum.fromCode(a.getCourier());
        CourierEnum returnCourier = sub != null && sub.returnCourier != null ? CourierEnum.fromCode(sub.returnCourier) : null;
        return AfterSaleVO.builder()
                .id(a.getId())
                .orderId(a.getOrderId())
                .afterSaleNo(a.getAfterSaleNo())
                .orderNo(a.getOrderNo())
                .afterSaleType(a.getAfterSaleType())
                .afterSaleTypeDesc(type == null ? null : type.getDesc())
                .status(a.getStatus())
                .statusDesc(status == null ? null : status.getDesc())
                .reason(a.getReason())
                .reasonDesc(reason == null ? null : reason.getDesc())
                .goodsStatus(a.getGoodsStatus())
                .goodsStatusDesc(goodsStatus == null ? null : goodsStatus.getDesc())
                .remark(a.getRemark())
                .images(parseImages(a.getImages()))
                .merchantRemark(a.getMerchantRemark())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .consignee(a.getConsignee())
                .phone(a.getPhone())
                .region(a.getRegion())
                .detailAddress(a.getDetailAddress())
                .shipAddress(sub == null ? null : sub.shipAddress)
                .payType(a.getPayType())
                .payTypeDesc(payType == null ? null : payType.getDesc())
                .payAt(a.getPayAt())
                .payAmount(a.getPayAmount())
                .courier(a.getCourier())
                .courierDesc(courier == null ? null : courier.getDesc())
                .trackingNumber(a.getTrackingNumber())
                .orderStatus(a.getOrderStatus())
                .returnWay(sub == null ? null : sub.returnWay)
                .returnTimeSlot(sub == null ? null : sub.returnTimeSlot)
                .returnCourier(sub == null ? null : sub.returnCourier)
                .returnCourierDesc(returnCourier == null ? null : returnCourier.getDesc())
                .returnTrackingNumber(sub == null ? null : sub.returnTrackingNumber)
                .returnAt(sub == null ? null : sub.returnAt)
                .reviewAt(sub == null ? null : sub.reviewAt)
                .rejectedAt(sub == null ? null : sub.rejectedAt)
                .refundAt(sub == null ? null : sub.refundAt)
                .refundedAt(sub == null ? null : sub.refundedAt)
                .exchangeAt(sub == null ? null : sub.exchangeAt)
                .exchangedAt(sub == null ? null : sub.exchangedAt)
                .items(items == null ? null : items.stream().map(this::toItemVO).toList())
                .build();
    }

    private AfterSaleItemVO toItemVO(AfterSaleItem item) {
        return AfterSaleItemVO.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .image(item.getImage())
                .salesPrice(item.getSalesPrice())
                .quantity(item.getQuantity())
                .refundAmount(item.getRefundAmount())
                .insuranceDetail(parseInsurance(item.getInsuranceDetail()))
                .insuranceFee(item.getInsuranceFee())
                .build();
    }

    private String generateAfterSaleNo() {
        return "AS" + LocalDateTime.now().format(AFTER_SALE_NO_FORMATTER);
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    /** 图片 URL 数组 → JSON 字符串，空或失败返回 "[]" */
    private String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** JSON 字符串 → 图片 URL 数组，失败返回空数组 */
    @SuppressWarnings("unchecked")
    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return (List<String>) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** JSON 字符串 → 增值保障明细数组，失败返回空数组 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseInsurance(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return (List<Map<String, Object>>) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 子表平铺字段集合 */
    private static class SubInfo {
        String returnWay;
        String returnTimeSlot;
        String returnCourier;
        String returnTrackingNumber;
        String shipAddress;
        LocalDateTime returnAt;
        LocalDateTime reviewAt;
        LocalDateTime rejectedAt;
        LocalDateTime refundAt;
        LocalDateTime refundedAt;
        LocalDateTime exchangeAt;
        LocalDateTime exchangedAt;
    }
}
