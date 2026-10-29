package ecommerce.aftersale.service.impl;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;
import ecommerce.aftersale.entity.AfterSale;
import ecommerce.aftersale.entity.AfterSaleExchangeReturn;
import ecommerce.aftersale.entity.AfterSaleItem;
import ecommerce.aftersale.entity.AfterSaleOnlyRefund;
import ecommerce.aftersale.entity.AfterSaleReturn;
import ecommerce.aftersale.enums.AfterSaleReasonEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;
import ecommerce.aftersale.enums.PayTypeEnum;
import ecommerce.aftersale.mapper.AfterSaleExchangeReturnMapper;
import ecommerce.aftersale.mapper.AfterSaleItemMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.mapper.AfterSaleOnlyRefundMapper;
import ecommerce.aftersale.mapper.AfterSaleReturnMapper;
import ecommerce.aftersale.service.AdminAfterSaleService;
import ecommerce.aftersale.vo.AfterSaleItemVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台售后管理 Service 实现。
 * <p>
 * 审核照旧 Node 的 updateStatus：主表只改 status/merchant_remark/updated_at，
 * 业务时间戳按状态写对应子表列（STATUS_COLUMN_MAP 白名单）。
 */
@Service
public class AdminAfterSaleServiceImpl implements AdminAfterSaleService {

    /** 目标状态 → 子表时间戳列名（白名单，防动态列名 SQL 注入） */
    private static final Map<String, String> STATUS_COLUMN_MAP = Map.of(
            "returning", "review_at",
            "refunding", "review_at",
            "rejected", "rejected_at",
            "refund_confirming", "refund_at",
            "refunded", "refunded_at",
            "exchanging", "exchange_at",
            "exchanged", "exchanged_at"
    );

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final AfterSaleReturnMapper afterSaleReturnMapper;
    private final AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper;
    private final AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper;
    private final ObjectMapper objectMapper;

    public AdminAfterSaleServiceImpl(AfterSaleMapper afterSaleMapper, AfterSaleItemMapper afterSaleItemMapper,
                                     AfterSaleReturnMapper afterSaleReturnMapper,
                                     AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper,
                                     AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper,
                                     ObjectMapper objectMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.afterSaleReturnMapper = afterSaleReturnMapper;
        this.afterSaleOnlyRefundMapper = afterSaleOnlyRefundMapper;
        this.afterSaleExchangeReturnMapper = afterSaleExchangeReturnMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AfterSaleVO> list(String status, String search, String startDate, String endDate, String dateField) {
        String field = resolveDateField(dateField);
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        return afterSaleMapper.listByShop(status, search, start, end, field).stream()
                .map(a -> toVO(a, null, null))
                .toList();
    }

    /** 时间筛选字段白名单，防动态列名 SQL 注入 */
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
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        List<AfterSaleItem> items = afterSaleItemMapper.listByAfterSaleId(id);
        SubInfo sub = loadSub(afterSale.getAfterSaleType(), id);
        return toVO(afterSale, items, sub);
    }

    /** 按售后类型路由到对应子表，读取时间戳/退货字段平铺 */
    private SubInfo loadSub(String type, Long id) {
        SubInfo sub = new SubInfo();
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return sub;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> {
                AfterSaleReturn r = afterSaleReturnMapper.findByAfterSaleId(id);
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
                AfterSaleOnlyRefund r = afterSaleOnlyRefundMapper.findByAfterSaleId(id);
                if (r != null) {
                    sub.reviewAt = r.getReviewAt();
                    sub.rejectedAt = r.getRejectedAt();
                    sub.refundAt = r.getRefundAt();
                    sub.refundedAt = r.getRefundedAt();
                }
            }
            case EXCHANGE -> {
                AfterSaleExchangeReturn r = afterSaleExchangeReturnMapper.findByAfterSaleId(id);
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

    /** 组装售后 VO：items/sub 为 null 时（列表场景）不返回明细与子表字段 */
    private AfterSaleVO toVO(AfterSale a, List<AfterSaleItem> items, SubInfo sub) {
        AfterSaleStatusEnum status = AfterSaleStatusEnum.fromCode(a.getStatus());
        AfterSaleTypeEnum type = AfterSaleTypeEnum.fromCode(a.getAfterSaleType());
        AfterSaleReasonEnum reason = AfterSaleReasonEnum.fromCode(a.getReason());
        PayTypeEnum payType = PayTypeEnum.fromCode(a.getPayType());
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
                .payType(a.getPayType())
                .payTypeDesc(payType == null ? null : payType.getDesc())
                .payAt(a.getPayAt())
                .payAmount(a.getPayAmount())
                .orderStatus(a.getOrderStatus())
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

    @Override
    @Transactional
    public void review(Long id, AdminAfterSaleDTO dto) {
        if (dto.getStatus() == null || AfterSaleStatusEnum.fromCode(dto.getStatus()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后状态不合法");
        }
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 1. 主表：状态 + 商家备注 + 更新时间
        afterSaleMapper.updateStatusAndRemark(id, dto.getStatus(),
                dto.getMerchantRemark() == null ? "" : dto.getMerchantRemark(), now);
        // 2. 子表：按状态写对应时间戳列
        String column = STATUS_COLUMN_MAP.get(dto.getStatus());
        if (column != null) {
            updateSubTimestamp(afterSale.getAfterSaleType(), id, column, now);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        afterSaleMapper.softDelete(id, now);
        afterSaleItemMapper.softDelete(id, now);
        softDeleteSub(afterSale.getAfterSaleType(), id, now);
    }

    /** 按售后类型路由到对应子表，写入时间戳列 */
    private void updateSubTimestamp(String type, Long afterSaleId, String column, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.updateTimestamp(afterSaleId, column, now);
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.updateTimestamp(afterSaleId, column, now);
            case EXCHANGE -> afterSaleExchangeReturnMapper.updateTimestamp(afterSaleId, column, now);
        }
    }

    /** 按售后类型路由到对应子表，软删 */
    private void softDeleteSub(String type, Long afterSaleId, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.softDelete(afterSaleId, now);
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.softDelete(afterSaleId, now);
            case EXCHANGE -> afterSaleExchangeReturnMapper.softDelete(afterSaleId, now);
        }
    }
}
