package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 包裹 VO（后台发货列表）。
 * <p>
 * 同时带出关联订单的收货信息与状态，列表不必再查订单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveryVO {
    @Schema(description = "包裹 ID")
    private Long id;
    @Schema(description = "订单 ID")
    private Long orderId;
    @Schema(description = "订单编号")
    private String orderNo;
    @Schema(description = "包裹编号")
    private String packageNo;
    @Schema(description = "快递公司编码")
    private String expressCode;
    @Schema(description = "快递公司名称")
    private String expressName;
    @Schema(description = "快递单号")
    private String trackingNumber;
    @Schema(description = "发货类型：1 正常发货 2 补发")
    private Integer deliveryType;
    @Schema(description = "发货类型文案")
    private String deliveryTypeDesc;
    @Schema(description = "发货时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;
    @Schema(description = "发货备注")
    private String remark;
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "买家备注")
    private String buyerRemark;
    @Schema(description = "订单状态 code")
    private Integer orderStatus;
    @Schema(description = "订单状态中文描述")
    private String orderStatusDesc;
    @Schema(description = "订单签收状态：signed / auto / unsigned")
    private String signStatus;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
