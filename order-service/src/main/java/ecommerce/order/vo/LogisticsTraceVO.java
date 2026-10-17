package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物流轨迹单条（假数据）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsTraceVO {
    @Schema(description = "轨迹时间")
    private String time;
    @Schema(description = "轨迹状态（已揽件/运输中/派送中/已签收）")
    private String status;
    @Schema(description = "轨迹描述")
    private String desc;
}
