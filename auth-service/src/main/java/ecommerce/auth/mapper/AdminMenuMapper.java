package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminMenu;

import java.util.List;

/**
 * 后台菜单 Mapper（角色分配菜单树用）。
 */
public interface AdminMenuMapper {

    // 全部菜单（含按钮 F），用于角色分配
    List<AdminMenu> listAllWithButtons();
}
