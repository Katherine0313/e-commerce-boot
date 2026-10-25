package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 下单请求。
 * <p>
 * 商品只传 product_id 和 quantity，价格/快照由后端查 products 表生成，不信任前端价格。
 */
@Data
public class CreateOrderDTO {
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "买家备注")
    private String remark;
    @Schema(description = "幂等键：同一次下单意图复用同一值（前端生成 UUID），下单成功后前端丢弃")
    private String idempotencyKey;
    @Schema(description = "是否同意接收未支付提醒（wx.requestSubscribeMessage 的 accept 结果）")
    private Boolean subscribeUnpaid;
    @Schema(description = "订单明细")
    private List<OrderItemDTO> items;
}
