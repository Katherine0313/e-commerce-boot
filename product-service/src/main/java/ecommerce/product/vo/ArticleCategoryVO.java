package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章分类 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCategoryVO {
    @Schema(description = "分类 ID")
    private Long id;
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "分类图标")
    private String picUrl;
    @Schema(description = "排序，越小越靠前")
    private Integer sortOrder;
    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
    @Schema(description = "状态中文")
    private String statusDesc;
    @Schema(description = "该分类下的文章数")
    private Integer articleCount;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
