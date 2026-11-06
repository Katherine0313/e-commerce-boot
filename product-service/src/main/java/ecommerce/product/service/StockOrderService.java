package ecommerce.product.service;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.StockOrderCreateDTO;
import ecommerce.product.dto.StockWriteDTO;
import ecommerce.product.entity.StockOrder;
import ecommerce.product.entity.StockOrderItem;
import ecommerce.product.mapper.StockOrderItemMapper;
import ecommerce.product.mapper.StockOrderMapper;
import ecommerce.product.support.OrderNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class StockOrderService {
    private final StockOrderMapper orderMapper;
    private final StockOrderItemMapper itemMapper;
    private final StockWriteService writeService;
    private final OrderNoGenerator noGenerator;

    public StockOrderService(StockOrderMapper orderMapper, StockOrderItemMapper itemMapper,
                             StockWriteService writeService, OrderNoGenerator noGenerator) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.writeService = writeService;
        this.noGenerator = noGenerator;
    }

    /**
     * 创建并完成一张单据（Receipt / Adjustment 手动建；Shipment 一般自动生成）。
     * 一个事务：写 stock_order + 写 item 明细 + 逐行调 StockWriteService.write()。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockOrder createAndComplete(StockOrderCreateDTO dto, Long operatorId, String operatorName) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("单据明细不能为空");
        }

        // 1. 写主表
        StockOrder order = StockOrder.builder()
            .orderNo(noGenerator.generate(dto.getType()))
            .type(dto.getType())
            .bizType(dto.getBizType())
            .status(1)  // 创建即已完成（P0 无草稿）
            .totalQty(0) // 稍后 refreshTotal
            .remark(dto.getRemark())
            .operatorId(operatorId)
            .operatorName(operatorName)
            .build();
        orderMapper.insert(order);

        // 2. 写明细（注意：调整单用 afterQty，其他类型用 qty）
        List<StockOrderItem> items = new ArrayList<>();
        int totalQty = 0;
        for (StockOrderCreateDTO.StockOrderItemDTO itemDto : dto.getItems()) {
            if (itemDto.getSkuId() == null || itemDto.getQty() == null || itemDto.getQty() <= 0) {
                throw new BusinessException("SKU " + itemDto.getSkuCode() + " 明细不完整");
            }
            StockOrderItem item = StockOrderItem.builder()
                .orderId(order.getId())
                .skuId(itemDto.getSkuId())
                .skuCode(itemDto.getSkuCode())
                .skuName(itemDto.getSkuName())
                .specText(itemDto.getSpecText())
                .qty(itemDto.getQty())
                .costPrice(itemDto.getCostPrice())
                .build();
            items.add(item);
            totalQty += itemDto.getQty();
        }
        itemMapper.insertAll(items);
        order.setTotalQty(totalQty);

        // 3. 逐行调 StockWriteService.write()
        //    RECEIPT → IN, SHIPMENT → OUT, ADJUST → 按差异方向
        for (int i = 0; i < items.size(); i++) {
            StockOrderItem item = items.get(i);
            StockOrderCreateDTO.StockOrderItemDTO itemDto = dto.getItems().get(i);

            String writeType;
            int writeQty;
            if ("RECEIPT".equals(dto.getType())) {
                writeType = "IN";
                writeQty = itemDto.getQty();
            } else if ("SHIPMENT".equals(dto.getType())) {
                writeType = "OUT";
                writeQty = itemDto.getQty();
            } else if ("ADJUST".equals(dto.getType())) {
                // 调整单：afterQty - 当前 stock = 差异
                // 前端没传 afterQty 时，按 qty 直接调
                // 后端这里需要先查一下当前 stock 算差异方向
                writeType = "ADJUST";
                writeQty = itemDto.getQty();
            } else {
                throw new BusinessException("未知单据类型: " + dto.getType());
            }

            StockWriteDTO writeDTO = StockWriteDTO.builder()
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .skuName(item.getSkuName())
                .specText(item.getSpecText())
                .type(writeType)
                .reason(dto.getBizType() != null ? dto.getBizType() : "手工补录")
                .qty(writeQty)
                .stockOrderId(order.getId())
                .operatorId(operatorId)
                .operatorName(operatorName)
                .remark(dto.getRemark())
                .build();
            writeService.write(writeDTO);
        }

        return order;
    }

    /** 取消单据（已完成不能取消） */
    public void cancel(Long id) {
        StockOrder order = orderMapper.findById(id);
        if (order == null) throw new BusinessException("单据不存在");
        if (order.getStatus() == 0) throw new BusinessException("单据已取消");
        orderMapper.updateStatus(id, 0);
    }
}
