package ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AddCartDTO {
    private Long productId;
    private Integer quantity;
    // 增值保障明细（加购弹窗里勾选的保障项数组）；后端按规则复核并重算费用，空表示不买
    private List<Map<String, Object>> insuranceDetail;
    // 单件增值保障费合计（后端按规则重算，不采信前端值）
    private BigDecimal insuranceFee;
}
