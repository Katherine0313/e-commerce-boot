package ecommerce.user.service;

import ecommerce.user.dto.PcCodeLoginDTO;
import ecommerce.user.dto.PcLoginDTO;
import ecommerce.user.dto.PcRegisterDTO;
import ecommerce.user.dto.PcSendCodeDTO;
import ecommerce.user.vo.PcLoginVO;
import ecommerce.user.vo.PcSendCodeVO;

/**
 * PC 商城登录/注册：账号密码 + 手机号验证码（验证码本地模拟，不接短信）
 */
public interface PcAuthService {

    /** 用户名 + 密码登录 */
    PcLoginVO loginByPassword(PcLoginDTO dto);

    /** 用户名 + 手机号 + 密码注册 */
    PcLoginVO register(PcRegisterDTO dto);

    /** 发送登录验证码（60 秒限频，5 分钟有效，模拟返回） */
    PcSendCodeVO sendLoginCode(PcSendCodeDTO dto);

    /** 手机号 + 验证码登录；手机号未注册时自动创建账号 */
    PcLoginVO loginByCode(PcCodeLoginDTO dto);
}
