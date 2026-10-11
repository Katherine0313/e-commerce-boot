package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收货地址实体（对应 address 表，模板店 shopId=1）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Address {
    private Long id;
    private Long shopId;
    private Long userId;
    private String consignee;
    private String phone;
    private String region;
    private String detailAddress;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
