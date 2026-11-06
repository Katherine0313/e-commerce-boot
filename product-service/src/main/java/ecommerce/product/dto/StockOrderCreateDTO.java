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
    private String type;        // RECEIPT / ADJUST (SHIPMENT 一般自动生成)
    private String bizType;
    private String remark;
    private List<StockOrderItemDTO> items;

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
