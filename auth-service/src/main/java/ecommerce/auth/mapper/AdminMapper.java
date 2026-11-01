package ecommerce.auth.mapper;

import ecommerce.auth.entity.Admin;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminMapper {
    // 按用户名查管理员（登录用）
    Admin findByUsername(@Param("username") String username);

    // 按 id 查管理员（查当前登录用户用）
    Admin findById(@Param("id") Long id);

    // ==================== 后台账号管理 ====================

    List<Admin> listAdmins(@Param("keyword") String keyword, @Param("status") Integer status,
                           @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmins(@Param("keyword") String keyword, @Param("status") Integer status);

    int insert(Admin admin);

    int update(Admin admin);

    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("now") LocalDateTime now);
}
