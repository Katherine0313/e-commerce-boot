package ecommerce.aftersale.vo;

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
 * 售后单 VO（C 端返回）。
 * <p>
 * 主表字段 + 订单收货信息（JOIN orders）+ 子表平铺字段（按 after_sale_type 有值）+ 明细。
 * 字段名与原 Node 后端返回保持一致，前端无需改字段名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleVO {
    @Schema(description = "售后单 ID")
    private Long id;
    @Schema(description = "关联订单 ID")
    private Long orderId;
    @Schema(description = "售后单号")
    private String afterSaleNo;
    @Schema(description = "原订单号")
    private String orderNo;
    @Schema(description = "售后类型 code")
    private String afterSaleType;
    @Schema(description = "售后类型中文")
    private String afterSaleTypeDesc;
    @Schema(description = "售后状态 code")
    private String status;
    @Schema(description = "售后状态中文")
    private String statusDesc;
    @Schema(description = "售后原因 code")
    private String reason;
    @Schema(description = "售后原因中文")
    private String reasonDesc;
    @Schema(description = "货物状态 code")
    private String goodsStatus;
    @Schema(description = "货物状态中文")
    private String goodsStatusDesc;
    @Schema(description = "买家备注")
    private String remark;
    @Schema(description = "图片凭证 URL 数组")
    private List<String> images;
    @Schema(description = "商家备注")
    private String merchantRemark;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "换货发货地址")
    private String shipAddress;
    @Schema(description = "支付方式 code")
    private String payType;
    @Schema(description = "支付方式中文描述")
    private String payTypeDesc;
    @Schema(description = "支付时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payAt;
    @Schema(description = "实付金额")
    private BigDecimal payAmount;
    @Schema(description = "快递公司 code")
    private String courier;
    @Schema(description = "快递公司中文")
    private String courierDesc;
    @Schema(description = "快递单号")
    private String trackingNumber;
    @Schema(description = "订单状态 code")
    private Integer orderStatus;
    @Schema(description = "退货方式：pickup / self")
    private String returnWay;
    @Schema(description = "取件时段：morning / afternoon")
    private String returnTimeSlot;
    @Schema(description = "退货快递公司 code")
    private String returnCourier;
    @Schema(description = "退货快递公司中文")
    private String returnCourierDesc;
    @Schema(description = "退货快递单号")
    private String returnTrackingNumber;
    @Schema(description = "发起退货时间")
    private LocalDateTime returnAt;
    @Schema(description = "审核时间")
    private LocalDateTime reviewAt;
    @Schema(description = "驳回时间")
    private LocalDateTime rejectedAt;
    @Schema(description = "发起退款时间")
    private LocalDateTime refundAt;
    @Schema(description = "退款完成时间")
    private LocalDateTime refundedAt;
    @Schema(description = "发起换货时间")
    private LocalDateTime exchangeAt;
    @Schema(description = "换货完成时间")
    private LocalDateTime exchangedAt;
    @Schema(description = "售后明细")
    private List<AfterSaleItemVO> items;
    @Schema(description = "当前状态可执行的动作（仅后台详情接口下发，前端按此渲染审核按钮）")
    private List<AfterSaleActionVO> availableActions;
}
