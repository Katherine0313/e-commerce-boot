package ecommerce.shop.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员实体（对应 admin 表，shop-service 侧只读，用于克隆后取沙箱管理员）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {
    private Long id;
    private String username;
    private String name;
    private String role;
    private Long shopId;
}
