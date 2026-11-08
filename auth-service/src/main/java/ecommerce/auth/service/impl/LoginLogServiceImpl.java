package ecommerce.auth.service.impl;

import ecommerce.auth.entity.AdminLoginLog;
import ecommerce.auth.enums.LoginResult;
import ecommerce.auth.enums.LoginType;
import ecommerce.auth.mapper.AdminLoginLogMapper;
import ecommerce.auth.service.LoginLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 后台登录/登出日志服务实现（仅登录链路写日志）。
 */
@Slf4j
@Service
public class LoginLogServiceImpl implements LoginLogService {

    /** 表字段长度上限，写入前统一截断，避免超长直接抛 SQL 异常 */
    private static final int MAX_REASON_LENGTH = 128;
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final AdminLoginLogMapper adminLoginLogMapper;

    public LoginLogServiceImpl(AdminLoginLogMapper adminLoginLogMapper) {
        this.adminLoginLogMapper = adminLoginLogMapper;
    }

    @Override
    public void record(Long adminId, String username, LoginType type, LoginResult result,
                       String reason, String ip, String userAgent) {
        // 整段 try/catch：写登录日志失败绝不能把管理员挡在门外（这是登录链路，不是普通业务）
        try {
            AdminLoginLog loginLog = AdminLoginLog.builder()
                    .adminId(adminId)
                    // username 列是 NOT NULL，取不到时给空串而不是 null
                    .username(username == null ? "" : username)
                    .loginType(type == null ? LoginType.LOGIN.name() : type.name())
                    .resultCode(result == null ? LoginResult.SUCCESS.name() : result.name())
                    .reason(truncate(reason, MAX_REASON_LENGTH))
                    .ip(truncate(ip, MAX_IP_LENGTH))
                    .userAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH))
                    .createdAt(LocalDateTime.now())
                    .build();
            adminLoginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("写登录日志失败: username={}, result={}, err={}",
                    username, result, e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
