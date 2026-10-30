package ecommerce.auth.service;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.CaptchaVO;

public interface AuthService {

    // 管理员登录：校验图形验证码 + 账号密码，签发 JWT
    AdminLoginVO login(AdminLoginDTO dto);

    // 生成图形验证码（答案存 Redis，一次性使用）
    CaptchaVO createCaptcha();

    // 查询当前登录管理员信息
    AdminUserVO getInfo(Long adminId);
}