package ecommerce.system.service;

import ecommerce.system.dto.AdminMenuDTO;
import ecommerce.system.vo.AdminMenuVO;

import java.util.List;

/**
 * 后台菜单管理（系统管理 → 菜单管理）。
 */
public interface AdminMenuService {

    // 全部菜单树（含停用/隐藏/按钮），供管理页维护
    List<AdminMenuVO> manageTree();

    Long create(AdminMenuDTO dto);

    void update(Long id, AdminMenuDTO dto);

    void delete(Long id);
}
