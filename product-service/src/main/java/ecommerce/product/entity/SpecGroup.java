package ecommerce.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售规格名（product_spec_group 表，字典）。
 */
@Data
public class SpecGroup {
    private Long id;
    private Long categoryId;
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
