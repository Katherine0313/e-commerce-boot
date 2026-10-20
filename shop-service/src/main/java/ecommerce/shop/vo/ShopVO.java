package ecommerce.shop.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 店铺信息 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopVO {
    @Schema(description = "店铺 ID")
    private Long id;
    @Schema(description = "店铺名称")
    private String name;
    @Schema(description = "是否模板店：1 是 0 否")
    private Integer isTemplate;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @Schema(description = "过期时间（模板店为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
