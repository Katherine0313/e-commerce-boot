package ecommerce.auth.dto;

import lombok.Data;

/**
 * 菜单管理新增/编辑请求体。
 */
@Data
public class AdminMenuDTO {
    /** 上级菜单，0 表示顶级 */
    private Long parentId;
    private String menuName;
    /** M 目录 / C 菜单 / F 按钮 */
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer sortOrder;
    /** 1 显示 / 0 隐藏 */
    private Integer visible;
    /** 1 正常 / 0 停用 */
    private Integer status;
}
