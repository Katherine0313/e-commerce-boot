package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单包裹实体（对应 order_delivery 表）。
 * <p>
 * 一个订单可以有多条包裹（补发会产生新包裹）；包裹本身没有独立状态，
 * 订单状态看 orders.order_status。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDelivery {
    private Long id;
    private Long orderId;
    /** 订单编号（快照） */
    private String orderNo;
    /** 包裹编号：PKG + 时间戳 + 4 位随机 */
    private String packageNo;
    /** 快递公司编码，关联 delivery_express.code */
    private String expressCode;
    /** 快递公司名称快照 */
    private String expressName;
    /** 快递单号 */
    private String trackingNumber;
    /** 发货类型：1 正常发货 2 补发 */
    private Integer deliveryType;
    /** 发货时间 */
    private LocalDateTime deliveryTime;
    /** 发货备注 */
    private String remark;
    /** 软删除：1 已删除 0 正常 */
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== 以下为关联订单的字段（非本表列，listAdmin 查询时带出） ====================
    /** 收货人姓名 */
    private String consignee;
    /** 收货人手机号 */
    private String phone;
    /** 省市区 */
    private String region;
    /** 详细地址 */
    private String detailAddress;
    /** 买家备注（orders.remark，别名 buyer_remark） */
    private String buyerRemark;
    /** 订单状态 code */
    private Integer orderStatus;
    /** 订单签收状态：signed / auto / unsigned */
    private String signStatus;
}
