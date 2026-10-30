package ecommerce.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录返回的菜单树（只含目录和菜单，不含按钮）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuVO {
    private Long id;
    private Long parentId;
    private String name;
    /** M 目录 / C 菜单 */
    private String type;
    private String path;
    /** 权限标识（按钮节点用于角色分配展示） */
    private String perms;
    private String icon;
    private Integer sortOrder;
    @Builder.Default
    private List<MenuVO> children = new ArrayList<>();
}
