package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 后台菜单 Mapper（菜单维护 + 角色分配菜单树）。
 */
public interface AdminMenuMapper {

    // 全部启用菜单（含按钮 F），用于角色分配
    List<AdminMenu> listAllWithButtons();

    // 全部菜单（含禁用/隐藏），用于菜单管理页
    List<AdminMenu> listAll();

    AdminMenu findById(@Param("id") Long id);

    int insert(AdminMenu menu);

    int update(AdminMenu menu);

    int delete(@Param("id") Long id);

    // 子菜单数量（删除/改类型前校验）
    long countChildren(@Param("parentId") Long parentId);

    // 权限标识是否已被其他菜单占用
    long countPerms(@Param("perms") String perms, @Param("excludeId") Long excludeId);

    // 菜单被多少个角色引用（删除前校验）
    long countRoleRefs(@Param("menuId") Long menuId);

    // 拥有该菜单的管理员（权限变更后让其旧 token 失效），排除超级管理员
    List<Long> listAdminIdsByMenuId(@Param("menuId") Long menuId);
}
