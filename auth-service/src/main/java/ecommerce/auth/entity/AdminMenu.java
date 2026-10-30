package ecommerce.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台菜单/按钮权限（admin_menu 表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMenu {
    private Long id;
    private Long parentId;
    private String menuName;
    /** M 目录 / C 菜单 / F 按钮 */
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
