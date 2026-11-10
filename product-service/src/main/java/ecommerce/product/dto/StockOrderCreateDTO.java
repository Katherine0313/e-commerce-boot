package ecommerce.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOrderCreateDTO {
    private String type;        // RECEIPT / SHIPMENT / ADJUST
    private String bizType;
    private String relatedNo;
    private String remark;
    private List<StockOrderItemDTO> items;
    // 内部接口（订单发货自动生成出库单）用：后台接口创建单据时忽略这两个字段，以登录态为准
    private Long operatorId;
    private String operatorName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockOrderItemDTO {
        private Long skuId;
        private String skuCode;
        private String skuName;
        private String specText;
        private Integer qty;
        private BigDecimal costPrice;
        // 调整单用：填调整后数量（后端算差异）
        private Integer afterQty;
    }
}
