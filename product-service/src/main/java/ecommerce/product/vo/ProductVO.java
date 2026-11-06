package ecommerce.product.vo;

import ecommerce.common.insurance.InsuranceOptionVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 VO（C 端浏览）。
 * <p>
 * 单规格：价格/库存直接挂在商品上，不做 SKU。cost_price 是后台字段，C 端不下发。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO {
    @Schema(description = "商品 ID")
    private Long id;
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "副标题")
    private String subTitle;
    @Schema(description = "品牌")
    private String brand;
    @Schema(description = "主图 URL")
    private String image;
    @Schema(description = "主标题")
    private String title;
    @Schema(description = "图文详情（富文本，列表不返回）")
    private String body;
    @Schema(description = "售价（有活动时为活动价）")
    private BigDecimal price;
    @Schema(description = "活动前价格（有活动时用于划线展示，无活动为 null）")
    private BigDecimal originalPrice;
    @Schema(description = "促销标签（如「限时立减」，无活动为 null）")
    private String promotionTag;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "库存数量")
    private Integer stock;
    @Schema(description = "所属分类 ID")
    private Long categoryId;
    @Schema(description = "标签 ID")
    private Long tagId;
    @Schema(description = "销售模式：spot 现货")
    private String salesMode;
    @Schema(description = "上架状态：1 上架 0 下架")
    private Integer status;
    @Schema(description = "商品编码")
    private String productCode;
    @Schema(description = "规格参数（有序键值对，非 SKU）")
    private List<SpecItemVO> specs;
    @Schema(description = "可选增值保障项（商品不适用保障时为空列表）")
    private List<InsuranceOptionVO> insuranceOptions;
    @Schema(description = "0 单规格 / 1 多规格")
    private Integer skuType;
    @Schema(description = "销售规格维度定义（多规格时有值）")
    private List<SkuSpecVO> skuSpecs;
    @Schema(description = "SKU 明细（仅商品详情下发，列表为空）")
    private List<SkuVO> skus;
    @Schema(description = "最低 SKU 价")
    private BigDecimal priceMin;
    @Schema(description = "最高 SKU 价")
    private BigDecimal priceMax;
}
