package ecommerce.aftersale.service.impl;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;
import ecommerce.aftersale.entity.AfterSale;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;
import ecommerce.aftersale.mapper.AfterSaleExchangeReturnMapper;
import ecommerce.aftersale.mapper.AfterSaleItemMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.mapper.AfterSaleOnlyRefundMapper;
import ecommerce.aftersale.mapper.AfterSaleReturnMapper;
import ecommerce.aftersale.service.AdminAfterSaleService;
import ecommerce.exception.BusinessException;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

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

    public AdminAfterSaleServiceImpl(AfterSaleMapper afterSaleMapper, AfterSaleItemMapper afterSaleItemMapper,
                                     AfterSaleReturnMapper afterSaleReturnMapper,
                                     AfterSaleOnlyRefundMapper afterSaleOnlyRefundMapper,
                                     AfterSaleExchangeReturnMapper afterSaleExchangeReturnMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.afterSaleReturnMapper = afterSaleReturnMapper;
        this.afterSaleOnlyRefundMapper = afterSaleOnlyRefundMapper;
        this.afterSaleExchangeReturnMapper = afterSaleExchangeReturnMapper;
    }

    private Long requireShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问：缺少店铺上下文");
        }
        return shopId;
    }

    @Override
    @Transactional
    public void review(Long id, AdminAfterSaleDTO dto) {
        Long shopId = requireShopId();
        if (dto.getStatus() == null || AfterSaleStatusEnum.fromCode(dto.getStatus()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "售后状态不合法");
        }
        AfterSale afterSale = afterSaleMapper.findById(id, shopId);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 1. 主表：状态 + 商家备注 + 更新时间
        afterSaleMapper.updateStatusAndRemark(id, shopId, dto.getStatus(),
                dto.getMerchantRemark() == null ? "" : dto.getMerchantRemark(), now);
        // 2. 子表：按状态写对应时间戳列
        String column = STATUS_COLUMN_MAP.get(dto.getStatus());
        if (column != null) {
            updateSubTimestamp(afterSale.getAfterSaleType(), id, shopId, column, now);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long shopId = requireShopId();
        AfterSale afterSale = afterSaleMapper.findById(id, shopId);
        if (afterSale == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        afterSaleMapper.softDelete(id, shopId, now);
        afterSaleItemMapper.softDelete(id, shopId, now);
        softDeleteSub(afterSale.getAfterSaleType(), id, shopId, now);
    }

    /** 按售后类型路由到对应子表，写入时间戳列 */
    private void updateSubTimestamp(String type, Long afterSaleId, Long shopId, String column, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.updateTimestamp(afterSaleId, shopId, column, now);
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.updateTimestamp(afterSaleId, shopId, column, now);
            case EXCHANGE -> afterSaleExchangeReturnMapper.updateTimestamp(afterSaleId, shopId, column, now);
        }
    }

    /** 按售后类型路由到对应子表，软删 */
    private void softDeleteSub(String type, Long afterSaleId, Long shopId, LocalDateTime now) {
        AfterSaleTypeEnum typeEnum = AfterSaleTypeEnum.fromCode(type);
        if (typeEnum == null) {
            return;
        }
        switch (typeEnum) {
            case RETURN_REFUND -> afterSaleReturnMapper.softDelete(afterSaleId, shopId, now);
            case ONLY_REFUND -> afterSaleOnlyRefundMapper.softDelete(afterSaleId, shopId, now);
            case EXCHANGE -> afterSaleExchangeReturnMapper.softDelete(afterSaleId, shopId, now);
        }
    }
}
