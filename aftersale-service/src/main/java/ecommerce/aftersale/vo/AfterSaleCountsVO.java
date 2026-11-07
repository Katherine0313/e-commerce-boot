package ecommerce.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 买家售后各状态数量（个人中心售后页 Tab 角标用）。
 */
@Data
@Schema(description = "售后各状态数量")
public class AfterSaleCountsVO {
    @Schema(description = "全部售后单数")
    private long all;

    @Schema(description = "商家审核中")
    private long reviewing;

    @Schema(description = "退货中")
    private long returning;

    @Schema(description = "退款中")
    private long refunding;

    @Schema(description = "换货中")
    private long exchanging;

    @Schema(description = "已换货")
    private long exchanged;

    @Schema(description = "已退款")
    private long refunded;

    @Schema(description = "售后拒绝")
    private long rejected;
}
