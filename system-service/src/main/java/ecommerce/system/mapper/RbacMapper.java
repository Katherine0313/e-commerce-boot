package ecommerce.system.mapper;

import ecommerce.system.entity.AdminMenu;
import ecommerce.system.entity.AdminRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RBAC 只读 Mapper：角色、权限、菜单。
 */
public interface RbacMapper {

    // 系统用户拥有的角色（只取正常状态）
    List<AdminRole> listRolesByAdminId(@Param("adminId") Long adminId);

    // 系统用户拥有的权限标识（菜单 + 按钮，去重）
    List<String> listPermsByAdminId(@Param("adminId") Long adminId);

    // 系统用户可见的目录/菜单（不含按钮）
    List<AdminMenu> listMenusByAdminId(@Param("adminId") Long adminId);

    // 全部目录/菜单（超级系统用户用）
    List<AdminMenu> listAllMenus();
}
