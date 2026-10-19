package ecommerce.product.dto;

import lombok.Data;

import java.util.List;

/**
 * 标签绑定/解绑商品请求体（/api/admin/tags/{tagId}/products）。
 */
@Data
public class TagProductsDTO {
    private List<Long> productIds;
}
