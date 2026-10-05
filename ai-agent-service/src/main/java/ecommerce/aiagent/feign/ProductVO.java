package ecommerce.aiagent.feign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品数据结构（Feign 调 product-service 返回，只取导购需要的字段）。
 * <p>
 * product-service 的 ProductVO 字段更多，这里按需精简；Jackson 反序列化时未知字段默认忽略。
 */
@Data
public class ProductVO {
    private Long id;
    private String name;
    private String subTitle;
    private String brand;
    private BigDecimal price;
    private String image;
    private String description;
}
