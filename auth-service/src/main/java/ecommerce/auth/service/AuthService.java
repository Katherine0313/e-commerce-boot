package ecommerce.auth.service;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.dto.PhoneLoginDTO;
import ecommerce.auth.dto.SendSmsCodeDTO;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.SmsCodeVO;

public interface AuthService {

    // 管理员登录：校验账号密码，签发 JWT
    AdminLoginVO login(AdminLoginDTO dto);

    // 发送手机登录验证码（开发期只写日志，不接真实短信通道）
    SmsCodeVO sendSmsCode(SendSmsCodeDTO dto);

    // 手机号 + 验证码登录：校验通过后签发与账号密码登录相同的 JWT
    AdminLoginVO loginByPhone(PhoneLoginDTO dto);

    // 查询当前登录管理员信息
    AdminUserVO getInfo(Long adminId);
}