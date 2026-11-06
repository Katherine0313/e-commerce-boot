package ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AddCartDTO {
    private Long productId;
    /** 选中的 SKU；为空时后端回落到商品默认 SKU（兼容旧客户端/单规格） */
    private Long skuId;
    private Integer quantity;
    // 增值保障明细（加购弹窗里勾选的保障项数组）；后端按规则复核并重算费用，空表示不买
    private List<Map<String, Object>> insuranceDetail;
    // 单件增值保障费合计（后端按规则重算，不采信前端值）
    private BigDecimal insuranceFee;
}
