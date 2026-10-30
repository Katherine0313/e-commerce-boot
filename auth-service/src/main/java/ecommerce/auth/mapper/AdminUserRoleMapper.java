package ecommerce.auth.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理员-角色关联 Mapper。
 */
public interface AdminUserRoleMapper {

    List<Long> listRoleIds(@Param("adminId") Long adminId);

    int deleteByAdminId(@Param("adminId") Long adminId);

    int insert(@Param("adminId") Long adminId, @Param("roleId") Long roleId);

    long countByRoleId(@Param("roleId") Long roleId);

    // 拥有该角色的管理员（角色变更后让其旧 token 失效），排除超级管理员
    List<Long> listAdminIdsByRoleId(@Param("roleId") Long roleId);
}
