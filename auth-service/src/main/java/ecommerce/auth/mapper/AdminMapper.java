package ecommerce.auth.mapper;

import ecommerce.auth.entity.Admin;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminMapper {
    // 按用户名查管理员（登录用；已软删的账号查不到）
    Admin findByUsername(@Param("username") String username);

    // 按用户名查管理员（含已软删的账号）；建账号时校验用户名是否被占用
    Admin findByUsernameIncludingDeleted(@Param("username") String username);

    // 按 id 查管理员（查当前登录用户用；已软删的账号查不到）
    Admin findById(@Param("id") Long id);

    // ==================== 后台账号管理 ====================

    List<Admin> listAdmins(@Param("keyword") String keyword, @Param("status") Integer status,
                           @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmins(@Param("keyword") String keyword, @Param("status") Integer status);

    int insert(Admin admin);

    int update(Admin admin);

    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("now") LocalDateTime now);

    /**
     * 软删账号：is_deleted=1 且 status=0。
     * status=0 是必须的 —— 登录和 token 失效都看 status，光靠 is_deleted 拦不住已签发的 token。
     */
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);
}
