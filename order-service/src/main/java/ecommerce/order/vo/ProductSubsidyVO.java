package ecommerce.order.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情页「国家补贴」横幅。
 * <p>
 * 复用优惠券体系：补贴就是一张名称含「国家补贴」、作用域为全场或指定商品的折扣券。
 * 没有可领模板时接口返回 null，前端不渲染横幅。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSubsidyVO {
    private Long templateId;
    private String name;
    private BigDecimal discountRate;
    /** 展示文案，如「立减10%」 */
    private String discountDesc;
    private BigDecimal maxDiscount;
    private BigDecimal thresholdAmount;
    private Integer remainQuantity;
    /** 当前用户是否已领取；未登录恒为 false */
    private Boolean claimed;
    private LocalDateTime receiveEnd;
    private String description;
}
