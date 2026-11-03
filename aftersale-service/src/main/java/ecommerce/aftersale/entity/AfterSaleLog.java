package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 售后变更流水（after_sale_log 表）。
 * <p>
 * 每一次售后单的状态变化写一条：谁（user_id/user_type）、从哪个状态到哪个状态
 * （before_status/after_status）、做的什么操作（operate_type）、给买家看的说明（content）。
 * <p>
 * content 与 merchant_remark 分两列：商家内部备注只进 merchant_remark，买家接口不返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleLog {

    /** 操作人类型：买家 */
    public static final int USER_TYPE_BUYER = 1;
    /** 操作人类型：后台管理员 */
    public static final int USER_TYPE_ADMIN = 2;

    private Long id;
    private Long afterSaleId;
    private Long userId;
    private Integer userType;
    private String beforeStatus;
    private String afterStatus;
    private Integer operateType;
    /** 买家可见的公开说明 */
    private String content;
    /** 商家内部备注（仅后台可见） */
    private String merchantRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
