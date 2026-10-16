package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 售后单实体（对应 after_sales 表）。
 * <p>
 * userId / consignee / phone / region / detailAddress 是查询时 LEFT JOIN orders 带出的字段，
 * 用于归属校验（userId）和列表/详情展示（收货信息），并不落在本表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSale {
    private Long id;
    private Long shopId;
    private Long orderId;
    private String afterSaleNo;
    private String orderNo;
    private String afterSaleType;
    private String status;
    private String reason;
    private String remark;
    private String images;
    private String merchantRemark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // LEFT JOIN orders 带出的字段
    private Long userId;
    private String consignee;
    private String phone;
    private String region;
    private String detailAddress;
}
