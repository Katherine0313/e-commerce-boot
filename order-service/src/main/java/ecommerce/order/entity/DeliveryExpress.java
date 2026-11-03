package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 快递公司字典实体（对应 delivery_express 表）。
 * <p>
 * code 用英文 key（如 shunfeng），与 orders.courier 存的英文 key 一致，
 * 这样 mock 物流的 CourierEnum.fromCode 仍能匹配上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryExpress {
    private Long id;
    /** 快递公司编码，如 shunfeng */
    private String code;
    /** 快递公司名称 */
    private String name;
    /** logo 相对路径，如 /image/express/xxx.webp */
    private String logo;
    /** 排序（小的在前） */
    private Integer sortOrder;
    /** 状态：1 启用 0 禁用 */
    private Integer status;
    private String remark;
    /** 软删除：1 已删除 0 正常 */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
