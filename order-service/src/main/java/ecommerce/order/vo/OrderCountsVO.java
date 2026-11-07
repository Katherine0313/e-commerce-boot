package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前用户各状态订单数量，C 端订单 Tab 角标用。
 */
@Data
public class OrderCountsVO {

    @Schema(description = "全部订单数")
    private long all;

    @Schema(description = "待付款")
    private long pendingPay;

    @Schema(description = "待发货")
    private long pendingShip;

    @Schema(description = "待收货（已发货）")
    private long pendingReceive;

    @Schema(description = "待评价（已完成且未评价）")
    private long pendingReview;

    @Schema(description = "已完成")
    private long finished;

    @Schema(description = "已取消")
    private long cancelled;

    @Schema(description = "已退款")
    private long refunded;
}
