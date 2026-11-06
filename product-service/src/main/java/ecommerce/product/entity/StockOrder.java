package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOrder {
    private Long id;
    private String orderNo;
    private String type;              // RECEIPT / SHIPMENT / ADJUST
    private String bizType;
    private String relatedNo;
    private Integer status;           // 1已完成 / 0已取消
    private Integer totalQty;
    private BigDecimal totalAmount;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
