package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 首页聚合 VO：分类入口 + 猜你喜欢。
 * <p>
 * 小程序首屏只发一次请求，弱网下少一次网络往返；两个数据源都在 product-service 内部。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeVO {
    @Schema(description = "一级分类（含子分类）")
    private List<CategoryTreeVO> categories;
    @Schema(description = "猜你喜欢商品")
    private List<ProductVO> recommend;
}
