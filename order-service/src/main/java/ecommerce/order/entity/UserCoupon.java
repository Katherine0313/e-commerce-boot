package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户券实例（user_coupon 表）。
 * <p>
 * status：UNUSED 未使用 / LOCKED 下单锁定 / USED 已使用；
 * 过期不落状态，UNUSED 且 endTime &lt; now 由 Java 判定为已过期。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {
    private Long id;
    private String code;
    private Long templateId;
    private Long userId;
    private String status;
    private LocalDateTime receiveTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long orderId;
    private LocalDateTime lockTime;
    private LocalDateTime usedTime;
    /** RECEIVE 领取 / ADMIN 后台发放 */
    private String source;
}
