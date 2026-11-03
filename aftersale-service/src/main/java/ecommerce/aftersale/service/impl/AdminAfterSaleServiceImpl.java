package ecommerce.aftersale.service.impl;

import ecommerce.aftersale.annotation.AfterSaleLogRecord;
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
import ecommerce.aftersale.mapper.AfterSaleLogMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.mapper.AfterSaleOnlyRefundMapper;
import ecommerce.aftersale.mapper.AfterSaleReturnMapper;
import ecommerce.aftersale.service.AdminAfterSaleService;
import ecommerce.aftersale.support.AfterSaleLogConverter;
import ecommerce.aftersale.support.AfterSaleTransitions;
import ecommerce.aftersale.vo.AfterSaleActionVO;
import ecommerce.aftersale.vo.AfterSaleItemVO;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.OperationLog;
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
 * <p>
 * 本次加了状态流转校验：只允许状态机（support/AfterSaleTransitions）白名单里的跃迁，
 * 并把可执行动作（availableActions）下发给前端，前端不再自己维护状态表。
 */
@Service
public class AdminAfterSaleServiceImpl implements AdminAfterSaleService {

    /**
     * 目标状态 → 子表时间戳列名（白名单，防动态列名 SQL 注入）。
     * <p>
     * refunded 同时写 refund_at 和 refunded_at：项目不接真实支付，没有「等款到账」时刻，
     * 「发起退款」与「退款完成」是同一刻，与订单取消自动退款的写法保持一致。
     */
    private static final Map<String, List<String>> STATUS_COLUMN_MAP = Map.of(
            "returning", List.of("review_at"),
            "refunding", List.of("review_at"),
            "rejected", List.of("rejected_at"),
            "refunded", List.of("refund_at", "refunded_at"),
            "exchanging", List.of("exchange_at"),
            "exchanged", List.of("exchanged_at")
    );

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final AfterSaleReturnMapper afterSaleReturnMapper;
    private final AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper;
    private final AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper;
    private final AfterSaleLogMapper afterSaleLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAfterSaleServiceImpl(AfterSaleMapper afterSaleMapper, AfterSaleItemMapper afterSaleItemMapper,
                                     AfterSaleReturnMapper afterSaleReturnMapper,
                                     AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper,
                                     AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper,
                                     AfterSaleLogMapper afterSaleLogMapper,
                                     ObjectMapper objectMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.afterSaleReturnMapper = afterSaleReturnMapper;
        this.afterSaleOnlyRefundMapper = afterSaleOnlyRefundMapper;
        this.afterSaleExchangeReturnMapper = afterSaleExchangeReturnMapper;
        this.afterSaleLogMapper = afterSaleLogMapper;
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
        AfterSaleVO vo = toVO(afterSale, items, sub);
        // 下发当前可执行动作，后台按这个渲染审核按钮（不再前端硬编码状态表）
        vo.setAvailableActions(availableActions(afterSale, sub));
        return vo;
    }

    /** 售后变更流水（后台可见，含商家内部备注） */
    @Override
    public List<AfterSaleLogVO> listLogs(Long id) {
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        return AfterSaleLogConverter.toVOList(afterSaleLogMapper.listByAfterSaleId(id), true);
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
                // 后台要看到商家内部备注（C 端的 toVO 不填，见 AfterSaleServiceImpl）
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

    /** 当前状态可执行的动作（后台审核按钮） */
    private List<AfterSaleActionVO> availableActions(AfterSale afterSale, SubInfo sub) {
        boolean returnSubmitted = sub != null && sub.returnAt != null;
        return AfterSaleTransitions.actionsOf(afterSale.getAfterSaleType(), afterSale.getStatus(), returnSubmitted)
                .stream()
                .map(action -> AfterSaleActionVO.builder()
                        .status(action.status())
                        .label(action.label())
                        .kind(action.kind().name())
                        .build())
                .toList();
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

    /**
     * 审核售后。
     * <p>
     * 只允许状态机白名单里的跃迁：不是从 reviewing 直接跳 refunded 这类操作都会被拒。
     */
    @Override
    @Transactional
    @OperationLog(module = "aftersales", action = "REVIEW", bizId = "#id")
    @AfterSaleLogRecord(dynamic = true, operator = AfterSaleLogRecord.OperatorType.ADMIN,
            merchantRemark = "#p1.merchantRemark")
    public void review(Long id, AdminAfterSaleDTO dto) {
        if (dto.getStatus() == null || AfterSaleStatusEnum.fromCode(dto.getStatus()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后状态不合法");
        }
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        // 状态流转校验：白名单外的跃迁一律拒绝
        boolean returnSubmitted = isReturnSubmitted(afterSale.getAfterSaleType(), id);
        AfterSaleTransitions.Action action = AfterSaleTransitions.find(
                afterSale.getAfterSaleType(), afterSale.getStatus(), dto.getStatus(), returnSubmitted);
        if (action == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "不允许从「" + statusDesc(afterSale.getStatus()) + "」变更为「" + statusDesc(dto.getStatus()) + "」");
        }
        // 拒绝售后必须说明原因
        if (AfterSaleStatusEnum.REJECTED.getCode().equals(dto.getStatus())
                && (dto.getMerchantRemark() == null || dto.getMerchantRemark().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "拒绝售后必须填写备注原因");
        }

        LocalDateTime now = LocalDateTime.now();
        // 1. 主表：状态 + 商家备注 + 更新时间
        afterSaleMapper.updateStatusAndRemark(id, dto.getStatus(),
                dto.getMerchantRemark() == null ? "" : dto.getMerchantRemark(), now);
        // 2. 子表：按状态写对应时间戳列
        List<String> columns = STATUS_COLUMN_MAP.get(dto.getStatus());
        if (columns != null) {
            for (String column : columns) {
                updateSubTimestamp(afterSale.getAfterSaleType(), id, column, now);
            }
        }
    }

    @Override
    @Transactional
    @OperationLog(module = "aftersales", action = "DELETE", bizId = "#id")
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

    /** 状态 code → 中文名，用于报错文案 */
    private String statusDesc(String status) {
        AfterSaleStatusEnum statusEnum = AfterSaleStatusEnum.fromCode(status);
        return statusEnum == null ? String.valueOf(status) : statusEnum.getDesc();
    }

    /** 是否已提交过退货方式（return_at 非空即已提交/回填） */
    private boolean isReturnSubmitted(String type, Long id) {
        if (AfterSaleTypeEnum.EXCHANGE.getCode().equals(type)) {
            AfterSaleExchangeReturn r = afterSaleExchangeReturnMapper.findByAfterSaleId(id);
            return r != null && r.getReturnAt() != null;
        }
        AfterSaleReturn r = afterSaleReturnMapper.findByAfterSaleId(id);
        return r != null && r.getReturnAt() != null;
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
