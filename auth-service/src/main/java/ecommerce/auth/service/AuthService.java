package ecommerce.auth.service;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.CaptchaVO;

public interface AuthService {

    // 管理员登录：校验图形验证码 + 账号密码，签发 JWT；clientIp 用于失败次数锁定
    AdminLoginVO login(AdminLoginDTO dto, String clientIp, String userAgent);

    // 管理员登出：无状态 JWT 服务端不作废令牌，只记一条登出日志；
    // authorization 是原始 Authorization 头，用于从令牌里取回登录人
    void logout(String authorization, String clientIp, String userAgent);

    // 生成图形验证码（答案存 Redis，一次性使用）
    CaptchaVO createCaptcha();

    // 查询当前登录管理员信息
    AdminUserVO getInfo(Long adminId);
}