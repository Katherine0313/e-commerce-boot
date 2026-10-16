package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 VO（C 端返回）。
 * <p>
 * orderStatus 是数字 code，orderStatusDesc 是中文描述（见 OrderStatusEnum）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {
    @Schema(description = "订单 ID")
    private Long id;
    @Schema(description = "订单号")
    private String orderNo;
    @Schema(description = "实付金额")
    private BigDecimal payAmount;
    @Schema(description = "支付方式")
    private String payType;
    @Schema(description = "支付方式中文描述")
    private String payTypeDesc;
    @Schema(description = "订单状态 code")
    private Integer orderStatus;
    @Schema(description = "订单状态中文描述")
    private String orderStatusDesc;
    @Schema(description = "买家备注")
    private String remark;
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "配送方式")
    private String shippingType;
    @Schema(description = "快递公司")
    private String courier;
    @Schema(description = "快递单号")
    private String trackingNumber;
    @Schema(description = "签收状态")
    private String signStatus;
    @Schema(description = "下单时间")
    private LocalDateTime createdAt;
    @Schema(description = "支付时间")
    private LocalDateTime payAt;
    @Schema(description = "发货时间")
    private LocalDateTime shipAt;
    @Schema(description = "确认收款时间")
    private LocalDateTime confirmAt;
    @Schema(description = "完成时间")
    private LocalDateTime finishAt;
    @Schema(description = "订单明细")
    private List<OrderItemVO> items;
}
