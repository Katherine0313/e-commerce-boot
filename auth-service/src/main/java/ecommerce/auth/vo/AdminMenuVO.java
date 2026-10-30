package ecommerce.auth.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单管理树节点（与角色分配用的 MenuVO 区分：这里保留完整字段，并包含停用项）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMenuVO {
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    @Builder.Default
    private List<AdminMenuVO> children = new ArrayList<>();
}
