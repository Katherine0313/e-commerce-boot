package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 物流轨迹（假数据，参考旧 Node 后端 getLogistics）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsVO {
    @Schema(description = "快递公司中文名")
    private String courier;
    @Schema(description = "快递单号")
    private String trackingNumber;
    @Schema(description = "轨迹列表（按时间升序，最后一条是最新）")
    private List<LogisticsTraceVO> traces;
}
