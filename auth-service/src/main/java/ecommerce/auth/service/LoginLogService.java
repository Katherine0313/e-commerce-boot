package ecommerce.auth.service;

import ecommerce.auth.dto.AdminLoginLogQuery;
import ecommerce.auth.enums.LoginResult;
import ecommerce.auth.enums.LoginType;
import ecommerce.auth.vo.AdminLoginLogVO;
import ecommerce.common.PageVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 后台登录/登出日志服务。
 * <p>
 * 登录日志不走 AOP：失败原因分散在登录流程的多个分支里（参数为空、锁定、验证码错、
 * 账密错、账号停用），AOP 拿不到「这次到底为什么失败」的语义，只能显式调用记录。
 */
public interface LoginLogService {

    /**
     * 记录一条登录/登出日志。
     * <p>
     * <b>实现必须整段 try/catch</b>：写日志失败绝不能把管理员挡在门外。
     *
     * @param adminId   管理员 ID；账号不存在时传 null
     * @param username  登录名（原样 trim 值，账号不存在也照记，便于查爆破）
     * @param type      LOGIN / LOGOUT
     * @param result    登录结果码
     * @param reason    失败原因文本，可为 null
     * @param ip        客户端 IP
     * @param userAgent 浏览器 User-Agent
     */
    void record(Long adminId, String username, LoginType type, LoginResult result,
                String reason, String ip, String userAgent);

    /** 分页查询登录日志 */
    PageVO<AdminLoginLogVO> page(AdminLoginLogQuery query);

    /** 导出登录日志（format：csv / xlsx），直接写响应流 */
    void export(AdminLoginLogQuery query, String format, HttpServletResponse response);
}
