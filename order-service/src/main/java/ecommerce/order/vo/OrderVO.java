package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @Schema(description = "下单用户 ID（后台列表展示「用户账号」用）")
    private Long userId;
    @Schema(description = "订单号")
    private String orderNo;
    @Schema(description = "订单金额（下单时 = 商品小计 + 保险；支付后即实付金额）")
    private BigDecimal payAmount;
    @Schema(description = "活动前金额（无活动时等于 payAmount）")
    private BigDecimal originalAmount;
    @Schema(description = "优惠金额（直减 + 满减 + 券）")
    private BigDecimal discountAmount;
    @Schema(description = "使用的优惠券 ID")
    private Long couponId;
    @Schema(description = "优惠券名称快照")
    private String couponName;
    @Schema(description = "优惠券优惠金额")
    private BigDecimal couponAmount;
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
    @Schema(description = "物流最新状态（已揽件/运输中/派送中/已签收，假数据）")
    private String logisticsStatus;
    @Schema(description = "物流最新轨迹描述（假数据）")
    private String latestTrace;
    @Schema(description = "下单时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @Schema(description = "支付时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payAt;
    @Schema(description = "发货时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shipAt;
    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishAt;
    @Schema(description = "是否已评价（已完成订单可评价，已评价则前端显示「已评价」）")
    private Boolean reviewed;
    @Schema(description = "是否已申请售后（已申请则前端显示「售后详情」）")
    private Boolean afterSale;
    @Schema(description = "订单明细")
    private List<OrderItemVO> items;
}
