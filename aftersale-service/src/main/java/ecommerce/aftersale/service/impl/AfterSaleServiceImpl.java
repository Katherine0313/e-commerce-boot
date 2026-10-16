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
import ecommerce.aftersale.enums.AfterSaleReasonEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;
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
    /** 订单状态数字 code（对齐 order-service 的 OrderStatusEnum）：4=已发货 5=已完成 */
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
        String type = dto.getAfterSaleType() == null || dto.getAfterSaleType().isBlank()
                ? AfterSaleTypeEnum.RETURN_REFUND.getCode() : dto.getAfterSaleType();
        if (AfterSaleTypeEnum.fromCode(type) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后类型不合法");
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
        // 只有已发货/已完成订单才能申请售后
        if (order.getOrderStatus() == null
                || (order.getOrderStatus() != ORDER_STATUS_SHIPPED && order.getOrderStatus() != ORDER_STATUS_FINISHED)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有已发货或已完成的订单才能申请售后");
        }
        // 同一订单不允许重复申请
        if (afterSaleMapper.findByOrderId(dto.getOrderId(), TEMPLATE_SHOP_ID) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已存在售后申请，请勿重复提交");
        }

        // 快照订单明细
        List<OrderItem> orderItems = orderMapper.listItems(dto.getOrderId(), TEMPLATE_SHOP_ID);
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
                .remark(dto.getRemark() == null ? "" : dto.getRemark())
                .images(toImagesJson(dto.getImages()))
                .merchantRemark("")
                .createdAt(now)
                .updatedAt(now)
                .build();
        afterSaleMapper.insert(afterSale);

        // 写子表占位行（按类型路由）
        insertSubPlaceholder(afterSale.getId(), type, now);

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
    public List<AfterSaleVO> list(String status) {
        return afterSaleMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID, status).stream()
                .map(a -> toVO(a, null, null))
                .toList();
    }

    @Override
    public AfterSaleVO getById(Long id) {
        return buildDetail(id);
    }

    @Override
    @Transactional
    public AfterSaleVO submitReturn(Long id, SubmitReturnDTO dto) {
        String returnWay = dto.getReturnWay();
        String returnTimeSlot = dto.getReturnTimeSlot();
        if (!"pickup".equals(returnWay) && !"self".equals(returnWay)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "return_way 必须是 pickup 或 self");
        }
        if ("pickup".equals(returnWay) && !"morning".equals(returnTimeSlot) && !"afternoon".equals(returnTimeSlot)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "上门取件必须指定时间段（morning/afternoon）");
        }

        AfterSale afterSale = getOwnedAfterSale(id);
        // 仅退款不退货，不调本接口
        if (AfterSaleTypeEnum.ONLY_REFUND.getCode().equals(afterSale.getAfterSaleType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅退款售后无需提交退货方式");
        }
        // 状态必须是 returning
        if (!AfterSaleStatusEnum.RETURNING.getCode().equals(afterSale.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前状态不允许提交退货方式");
        }
        // 是否已提交过（return_at 非空即已提交）
        if (isReturnSubmitted(afterSale.getAfterSaleType(), id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已提交过退货方式，不能重复提交");
        }

        LocalDateTime now = LocalDateTime.now();
        // 更新子表退货字段
        if (AfterSaleTypeEnum.EXCHANGE.getCode().equals(afterSale.getAfterSaleType())) {
            afterSaleExchangeReturnMapper.updateReturnInfo(id, TEMPLATE_SHOP_ID, returnWay, returnTimeSlot, now, now);
        } else {
            afterSaleReturnMapper.updateReturnInfo(id, TEMPLATE_SHOP_ID, returnWay, returnTimeSlot, now, now);
        }
        // 主表状态推进：return_refund → refunding；exchange 保持 returning
        if (AfterSaleTypeEnum.RETURN_REFUND.getCode().equals(afterSale.getAfterSaleType())) {
            afterSaleMapper.updateStatus(id, TEMPLATE_SHOP_ID, AfterSaleStatusEnum.REFUNDING.getCode(), now);
        } else {
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
        for (AfterSaleReasonEnum r : AfterSaleReasonEnum.values()) {
            reasonMap.put(r.getCode(), r.getDesc());
        }
        Map<String, String> typeMap = new LinkedHashMap<>();
        for (AfterSaleTypeEnum t : AfterSaleTypeEnum.values()) {
            typeMap.put(t.getCode(), t.getDesc());
        }

        return AfterSaleStatusConstantsVO.builder()
                .map(statusMap)
                .color(colorMap)
                .reasonMap(reasonMap)
                .typeMap(typeMap)
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

    /** 按类型写子表占位行 */
    private void insertSubPlaceholder(Long afterSaleId, String type, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.insertPlaceholder(
                    AfterSaleReturn.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId).createdAt(now).updatedAt(now).build());
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.insertPlaceholder(
                    AfterSaleOnlyRefund.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId).createdAt(now).updatedAt(now).build());
            case EXCHANGE -> afterSaleExchangeReturnMapper.insertPlaceholder(
                    AfterSaleExchangeReturn.builder().shopId(TEMPLATE_SHOP_ID).afterSaleId(afterSaleId).createdAt(now).updatedAt(now).build());
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
                .remark(a.getRemark())
                .images(parseImages(a.getImages()))
                .merchantRemark(a.getMerchantRemark())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .consignee(a.getConsignee())
                .phone(a.getPhone())
                .region(a.getRegion())
                .detailAddress(a.getDetailAddress())
                .returnWay(sub == null ? null : sub.returnWay)
                .returnTimeSlot(sub == null ? null : sub.returnTimeSlot)
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
        LocalDateTime returnAt;
        LocalDateTime reviewAt;
        LocalDateTime rejectedAt;
        LocalDateTime refundAt;
        LocalDateTime refundedAt;
        LocalDateTime exchangeAt;
        LocalDateTime exchangedAt;
    }
}
