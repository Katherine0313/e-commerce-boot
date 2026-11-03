package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 快递公司字典 VO（后台快递公司页 / 下拉）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryExpressVO {
    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "快递公司编码")
    private String code;
    @Schema(description = "快递公司名称")
    private String name;
    @Schema(description = "logo 相对路径")
    private String logo;
    @Schema(description = "排序（小的在前）")
    private Integer sortOrder;
    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
    @Schema(description = "状态文案")
    private String statusDesc;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
