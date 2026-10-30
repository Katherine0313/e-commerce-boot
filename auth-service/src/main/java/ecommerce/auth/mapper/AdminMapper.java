package ecommerce.auth.mapper;

import ecommerce.auth.entity.Admin;
import org.apache.ibatis.annotations.Param;

public interface AdminMapper {
    // 按用户名查管理员（登录用）
    Admin findByUsername(@Param("username") String username);

    // 按 id 查管理员（查当前登录用户用）
    Admin findById(@Param("id") Long id);
}
