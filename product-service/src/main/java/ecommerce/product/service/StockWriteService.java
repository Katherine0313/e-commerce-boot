package ecommerce.product.service;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.StockWriteDTO;
import ecommerce.product.entity.ProductSkuLite;
import ecommerce.product.entity.StockTransaction;
import ecommerce.product.mapper.StockOrderMapper;
import ecommerce.product.mapper.StockProductSkuMapper;
import ecommerce.product.mapper.StockTransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 所有出入库的唯一入口。order-service / aftersale-service / 库存模块建单完成 — 全部走 write()。
 * <p>
 * 事务：@Transactional 保证 FOR UPDATE 锁行 → UPDATE product_sku → 刷新 products.stock → INSERT 流水 原子完成。
 */
@Service
public class StockWriteService {
    private final StockProductSkuMapper skuMapper;
    private final StockTransactionMapper transactionMapper;
    private final StockOrderMapper orderMapper;

    public StockWriteService(StockProductSkuMapper skuMapper,
                             StockTransactionMapper transactionMapper,
                             StockOrderMapper orderMapper) {
        this.skuMapper = skuMapper;
        this.transactionMapper = transactionMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void write(StockWriteDTO dto) {
        // 1. 行锁读（FOR UPDATE，和下面 UPDATE 同事务）
        ProductSkuLite sku = skuMapper.lockById(dto.getSkuId());
        if (sku == null) {
            throw new BusinessException("SKU " + dto.getSkuId() + " 不存在");
        }

        // 2. 计算方向
        int delta;
        switch (dto.getType()) {
            case "IN":     delta = dto.getQty(); break;
            case "OUT":    delta = -dto.getQty(); break;
            case "ADJUST": delta = dto.getQty(); break;
            default: throw new BusinessException("未知库存类型: " + dto.getType());
        }
        int newStock = sku.getStock() + delta;
        if ("OUT".equals(dto.getType()) && newStock < 0) {
            throw new BusinessException("SKU " + sku.getSkuCode() + " 库存不足，当前 " + sku.getStock() + "，需扣 " + dto.getQty());
        }

        // 3. UPDATE product_sku（直接赋值）
        skuMapper.updateStockDirect(dto.getSkuId(), newStock);

        // 4. 刷新商品级汇总冗余
        skuMapper.refreshProductStock(sku.getProductId());

        // 5. 写流水（结存快照 = newStock）
        StockTransaction tx = new StockTransaction();
        tx.setSkuId(dto.getSkuId());
        tx.setSkuCode(sku.getSkuCode());
        tx.setSkuName(dto.getSkuName());
        tx.setSpecText(dto.getSpecText());
        tx.setOrderItemId(dto.getOrderItemId());
        tx.setStockOrderId(dto.getStockOrderId());
        tx.setType(dto.getType());
        tx.setReason(dto.getReason());
        tx.setQty(Math.abs(delta));
        tx.setBalanceAfter(newStock);
        tx.setOperatorId(dto.getOperatorId());
        tx.setOperatorName(dto.getOperatorName());
        tx.setRemark(dto.getRemark());
        transactionMapper.insert(tx);

        // 6. 单据驱动时刷新 order.total_qty
        if (dto.getStockOrderId() != null) {
            orderMapper.refreshTotal(dto.getStockOrderId());
        }
    }
}
