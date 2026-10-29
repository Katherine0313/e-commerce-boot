package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单实体（aftersale 域内只读快照，仅售后需要的字段）。
 * <p>
 * 与 order-service 的 Order 同名不同包：售后与订单共用同一数据库，这里只按需查 orders 表，
 * 不做跨服务依赖。order_status 是数字 code（见 order-service 的 OrderStatusEnum）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;
    private Long userId;
    private String orderNo;
    private Integer orderStatus;
}
