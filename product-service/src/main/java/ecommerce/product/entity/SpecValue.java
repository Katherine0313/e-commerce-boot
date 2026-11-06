package ecommerce.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售规格值（product_spec_value 表，字典）。
 */
@Data
public class SpecValue {
    private Long id;
    private Long groupId;
    private String value;
    private Integer sort;
    private LocalDateTime createTime;
}
