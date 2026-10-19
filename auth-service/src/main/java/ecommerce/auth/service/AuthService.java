package ecommerce.auth.service;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;

public interface AuthService {

    // 管理员登录：校验账号密码，签发 JWT
    AdminLoginVO login(AdminLoginDTO dto);

    // 查询当前登录管理员信息
    AdminUserVO getInfo(Long adminId);
}