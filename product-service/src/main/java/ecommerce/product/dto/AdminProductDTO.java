package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 后台商品新增/编辑请求体（/api/admin/products）。
 * <p>
 * 前端（照旧 Node 蛇形契约）提交蛇形字段，@JsonNaming(SnakeCase) 自动映射到驼峰；
 * 字段全部可空，支持局部更新（上架开关只传 listing_status）。
 */
@Data
public class AdminProductDTO {
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "副标题")
    private String subTitle;
    @Schema(description = "品牌")
    private String brand;
    @Schema(description = "商品描述")
    private String description;
    @Schema(description = "主图 URL")
    private String image;
    @Schema(description = "图文详情（富文本）")
    private String body;
    @Schema(description = "所属分类 ID")
    private Long categoryId;
    @Schema(description = "销售模式：spot 现货")
    private String salesMode;
    @Schema(description = "上架状态：1 上架 0 下架")
    private Integer listingStatus;
    @Schema(description = "售价")
    private BigDecimal salesPrice;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "成本价")
    private BigDecimal costPrice;
    @Schema(description = "商品编码")
    private String productCode;
    @Schema(description = "库存数量")
    private Integer stock;
    @Schema(description = "重量")
    private BigDecimal weight;
    @Schema(description = "体积")
    private BigDecimal volume;
    @Schema(description = "规格参数（有序键值对，非 SKU）")
    private List<SpecItemDTO> specs;
}
