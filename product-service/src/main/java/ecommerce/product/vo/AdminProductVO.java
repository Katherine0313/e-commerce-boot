package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import ecommerce.common.insurance.InsuranceOptionVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台商品 VO（/api/admin/**）。
 * <p>
 * 与 C 端 ProductVO 的区别：下发成本价 cost_price，时间字段用 created_at/updated_at 对齐旧 Node 前端。
 * 整个类用 @JsonNaming(SnakeCase) 把驼峰字段序列化成蛇形 JSON，前端（照旧 Node 蛇形契约写）字段零改动。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductVO {
    @Schema(description = "商品 ID")
    private Long id;
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "副标题")
    private String subTitle;
    @Schema(description = "品牌")
    private String brand;
    @Schema(description = "主标题")
    private String title;
    @Schema(description = "主图 URL")
    private String image;
    @Schema(description = "图文详情（富文本）")
    private String body;
    @Schema(description = "所属分类 ID")
    private Long categoryId;
    @Schema(description = "标签 ID")
    private Long tagId;
    @Schema(description = "销售模式：spot 现货")
    private String salesMode;
    @Schema(description = "上架状态：1 上架 0 下架")
    private Integer listingStatus;
    @Schema(description = "售价")
    private BigDecimal salesPrice;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "成本价（仅后台可见）")
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
    private List<SpecItemVO> specs;
    @Schema(description = "可选增值保障项（商品不适用保障时为空列表）")
    private List<InsuranceOptionVO> insuranceOptions;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
