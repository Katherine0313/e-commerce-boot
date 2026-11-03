package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货流水 VO（后台「查看流水」抽屉）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveryLogVO {
    @Schema(description = "流水 ID")
    private Long id;
    @Schema(description = "包裹 ID")
    private Long deliveryId;
    @Schema(description = "包裹编号")
    private String packageNo;
    @Schema(description = "订单 ID")
    private Long orderId;
    @Schema(description = "订单编号")
    private String orderNo;
    @Schema(description = "操作类型：1 创建发货 2 补发 3 修改快递信息")
    private Integer operateType;
    @Schema(description = "操作类型文案")
    private String operateTypeDesc;
    @Schema(description = "操作说明")
    private String content;
    @Schema(description = "内部备注")
    private String remark;
    @Schema(description = "操作人 ID")
    private Long operatorId;
    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
