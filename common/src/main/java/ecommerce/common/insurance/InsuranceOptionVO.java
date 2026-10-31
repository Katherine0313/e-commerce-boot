package ecommerce.common.insurance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 增值保障项（可选保障）。
 * <p>
 * 由 {@link InsurancePolicy} 按商品属性算出：product-service 下发给前端渲染，
 * order-service 加购/下单时按同一套规则复核，防止前端伪造保障项和金额。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceOptionVO {
    /** 保障项编码：warranty 全面保修 / accident 一年意外保 / battery 电池无忧 */
    private String code;
    /** 保障项名称 */
    private String name;
    /** 单件费用 */
    private BigDecimal fee;
    /** 费用说明（如「商品售价 1%」） */
    private String desc;
}
