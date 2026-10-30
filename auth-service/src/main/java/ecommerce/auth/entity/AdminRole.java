package ecommerce.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台角色（admin_role 表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRole {
    private Long id;
    /** admin / operator / service / warehouse */
    private String roleKey;
    private String roleName;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
