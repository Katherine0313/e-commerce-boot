package ecommerce.system.service;

import ecommerce.system.dto.AdminRoleDTO;
import ecommerce.system.vo.AdminRoleVO;
import ecommerce.system.vo.MenuVO;

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
