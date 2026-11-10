package ecommerce.product.service;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.StockOrderCreateDTO;
import ecommerce.product.dto.StockWriteDTO;
import ecommerce.product.entity.ProductSku;
import ecommerce.product.entity.StockOrder;
import ecommerce.product.entity.StockOrderItem;
import ecommerce.product.mapper.ProductSkuMapper;
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

    /** 流水 reason 统一存中文标签，和订单/售后链路（"销售出库" 等）保持同一套词汇，便于流水页按原因筛选 */
    private static String resolveReason(String type, String bizType) {
        if (bizType == null) return "手工补录";
        return switch (bizType) {
            case "PURCHASE" -> "采购入库";
            case "RETURN" -> "退货归还";
            case "ORDER_SHIP" -> "订单发货";
            case "LOSS" -> "盘亏出库";
            case "ADJUST" -> "盘点调整";
            // MANUAL 在入库单里是手工补录、在出库单里是手工出库
            case "MANUAL" -> "SHIPMENT".equals(type) ? "手工出库" : "手工补录";
            default -> bizType;
        };
    }
    private final StockOrderMapper orderMapper;
    private final StockOrderItemMapper itemMapper;
    private final StockWriteService writeService;
    private final OrderNoGenerator noGenerator;
    private final ProductSkuMapper skuMapper;

    public StockOrderService(StockOrderMapper orderMapper, StockOrderItemMapper itemMapper,
                             StockWriteService writeService, OrderNoGenerator noGenerator,
                             ProductSkuMapper skuMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.writeService = writeService;
        this.noGenerator = noGenerator;
        this.skuMapper = skuMapper;
    }

    /** 手动建单据（Receipt / Adjustment / 手动 Shipment）：写单据 + 明细 + 库存流水 */
    @Transactional(rollbackFor = Exception.class)
    public StockOrder createAndComplete(StockOrderCreateDTO dto, Long operatorId, String operatorName) {
        return create(dto, operatorId, operatorName, true);
    }

    /**
     * 订单发货自动生成销售出库单。
     * <p>
     * 库存在「下单」时已由 order-service 调 /internal/stock-write(OUT, 销售出库) 扣减，
     * 发货时再扣一次会重复扣库存，所以这里只落单据 + 明细，不写库存流水。
     * relatedNo = 订单号；同一订单重复调用（发货重试）直接复用已有单据，保证幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockOrder recordShipment(StockOrderCreateDTO dto, Long operatorId, String operatorName) {
        StockOrder existing = orderMapper.findByRelatedNoAndType(dto.getRelatedNo(), "SHIPMENT");
        if (existing != null) {
            return existing;
        }
        dto.setType("SHIPMENT");
        dto.setBizType("ORDER_SHIP");
        return create(dto, operatorId, operatorName, false);
    }

    /**
     * 创建并完成一张单据。
     * 一个事务：写 stock_order + 写 item 明细 +（可选）逐行调 StockWriteService.write()。
     *
     * @param writeStock 是否真正扣减库存；订单发货传 false（下单时已扣过）
     */
    private StockOrder create(StockOrderCreateDTO dto, Long operatorId, String operatorName, boolean writeStock) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("单据明细不能为空");
        }

        // 1. 写主表
        StockOrder order = StockOrder.builder()
            .orderNo(noGenerator.generate(dto.getType()))
            .type(dto.getType())
            .bizType(dto.getBizType())
            .relatedNo(dto.getRelatedNo())
            .status(1)  // 创建即已完成（P0 无草稿）
            .totalQty(0) // 稍后 refreshTotal
            .remark(dto.getRemark())
            .operatorId(operatorId)
            .operatorName(operatorName)
            .build();
        orderMapper.insert(order);

        // 2. 写明细（入/出库 qty 为正数；调整单 qty 是有符号差异，由前端按 afterQty - 当前库存算好）
        List<StockOrderItem> items = new ArrayList<>();
        int totalQty = 0;
        for (StockOrderCreateDTO.StockOrderItemDTO itemDto : dto.getItems()) {
            boolean isAdjust = "ADJUST".equals(dto.getType());
            if (itemDto.getSkuId() == null || itemDto.getQty() == null
                    || (isAdjust ? itemDto.getQty() == 0 : itemDto.getQty() <= 0)) {
                // 入/出库数量必须为正；调整单 qty 是有符号差异，允许为负、不允许为 0
                throw new BusinessException("SKU " + itemDto.getSkuCode() + " 明细不完整");
            }
            // 调用方没带规格文字时，用 SKU 的 spec_key 补全（订单发货链路只传 skuId）
            String specText = itemDto.getSpecText();
            if (specText == null || specText.isBlank()) {
                ProductSku sku = skuMapper.findById(itemDto.getSkuId());
                if (sku != null) {
                    specText = sku.getSpecKey();
                }
            }
            StockOrderItem item = StockOrderItem.builder()
                .orderId(order.getId())
                .skuId(itemDto.getSkuId())
                .skuCode(itemDto.getSkuCode())
                .skuName(itemDto.getSkuName())
                .specText(specText)
                .qty(itemDto.getQty())
                .costPrice(itemDto.getCostPrice())
                .build();
            items.add(item);
            totalQty += Math.abs(itemDto.getQty()); // 调整单差异有正负，合计取绝对值
        }
        itemMapper.insertAll(items);
        order.setTotalQty(totalQty);

        if (!writeStock) {
            // 只落单据：total_qty 直接按明细汇总（没有 write() 帮忙 refreshTotal）
            orderMapper.refreshTotal(order.getId());
            return order;
        }

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
                .reason(resolveReason(dto.getType(), dto.getBizType()))
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

    /** 取消单据：同一事务里对每行明细做反向出入库冲销，再置状态为已取消 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        StockOrder order = orderMapper.findById(id);
        if (order == null) throw new BusinessException("单据不存在");
        if (order.getStatus() == 0) throw new BusinessException("单据已取消");

        for (StockOrderItem item : itemMapper.findByOrderId(id)) {
            String writeType;
            int qty;
            switch (order.getType()) {
                // 入库取消 = 把入的货再出掉；出库取消 = 退回库存；调整取消 = 差异取反
                case "RECEIPT" -> { writeType = "OUT"; qty = item.getQty(); }
                case "SHIPMENT" -> { writeType = "IN"; qty = item.getQty(); }
                case "ADJUST" -> { writeType = "ADJUST"; qty = -item.getQty(); }
                default -> throw new BusinessException("未知单据类型: " + order.getType());
            }
            writeService.write(StockWriteDTO.builder()
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .skuName(item.getSkuName())
                .specText(item.getSpecText())
                .type(writeType)
                .reason("单据取消冲销")
                .qty(qty)
                .stockOrderId(order.getId())
                .remark("冲销 " + order.getOrderNo())
                .build());
        }
        orderMapper.updateStatus(id, 0);
    }
}
