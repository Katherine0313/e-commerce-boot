package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 后台角色管理 Mapper。
 */
public interface AdminRoleMapper {

    List<AdminRole> listRoles();

    AdminRole findById(@Param("id") Long id);

    AdminRole findByKey(@Param("roleKey") String roleKey);

    int insert(AdminRole role);

    int update(AdminRole role);

    int delete(@Param("id") Long id);

    List<Long> listMenuIds(@Param("roleId") Long roleId);

    int deleteRoleMenus(@Param("roleId") Long roleId);

    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    long countUsers(@Param("roleId") Long roleId);
}
