package ecommerce.auth.service;

import ecommerce.auth.dto.AdminRoleDTO;
import ecommerce.auth.vo.AdminRoleVO;
import ecommerce.auth.vo.MenuVO;

import java.util.List;

/**
 * 后台角色与菜单分配。
 */
public interface AdminRoleService {

    List<AdminRoleVO> list();

    AdminRoleVO get(Long id);

    Long create(AdminRoleDTO dto);

    void update(Long id, AdminRoleDTO dto);

    void delete(Long id);

    void assignMenus(Long id, List<Long> menuIds);

    /** 全部菜单树（含按钮），角色分配用 */
    List<MenuVO> menuTree();
}
