package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransaction {
    private Long id;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String specText;
    private Long orderItemId;
    private Long stockOrderId;
    private String type;        // IN / OUT / ADJUST
    private String reason;      // 销售出库/采购入库/退货归还/盘点调整/手工补录
    private Integer qty;        // 正数
    private Integer balanceAfter;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
