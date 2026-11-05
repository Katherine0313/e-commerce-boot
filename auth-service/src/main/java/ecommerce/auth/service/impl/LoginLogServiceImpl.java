package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminLoginLogQuery;
import ecommerce.auth.entity.AdminLoginLog;
importecommerce.auth.enums.LoginResult;
import ecommerce.auth.enums.LoginType;
import ecommerce.auth.mapper.AdminLoginLogMapper;
import ecommerce.auth.service.LoginLogService;
import ecommerce.auth.support.LogExportSupport;
import ecommerce.auth.vo.AdminLoginLogVO;
import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.OperationLog;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 后台登录/登出日志服务实现。
 */
@Slf4j
@Service
public class LoginLogServiceImpl implements LoginLogService {

    /** 表字段长度上限，写入前统一截断，避免超长直接抛 SQL 异常 */
    private static final int MAX_REASON_LENGTH = 128;
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    /** 导出文件名里的时间格式 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AdminLoginLogMapper adminLoginLogMapper;
    private final LogExportSupport logExportSupport;

    public LoginLogServiceImpl(AdminLoginLogMapper adminLoginLogMapper,
                                LogExportSupport logExportSupport) {
        this.adminLoginLogMapper = adminLoginLogMapper;
        this.logExportSupport = logExportSupport;
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

    @Override
    public PageVO<AdminLoginLogVO> page(AdminLoginLogQuery query) {
        AdminLoginLogQuery q = query.normalize();
        List<AdminLoginLogVO> list = adminLoginLogMapper.list(q).stream().map(this::toVO).toList();
        long total = adminLoginLogMapper.count(q);
        return new PageVO<>(list, total, q.getPage(), q.getPageSize());
    }

    @Override
    @OperationLog(module = "system", action = "LOG_EXPORT")
    public void export(AdminLoginLogQuery query, String format, HttpServletResponse response) {
        AdminLoginLogQuery q = query.normalize();
        long total = adminLoginLogMapper.count(q);
        // 超过上限直接 400：让用户缩小时间范围，而不是把几十万行拉进内存
        if (total > LogExportSupport.MAX_EXPORT_ROWS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "导出数据量超过 " + LogExportSupport.MAX_EXPORT_ROWS + " 条，请缩小时间范围后重试");
        }
        List<String> headers = List.of("ID", "管理员ID", "登录名", "类型", "结果",
                "失败原因", "IP", "User-Agent", "发生时间");
        // 分批拉取：每次 1000 行，写完释放，不在内存里攒全量
        LogExportSupport.ChunkFetcher fetcher = (offset, limit) -> {
            q.setOffset(offset);
            q.setPageSize(limit);
            return adminLoginLogMapper.list(q).stream().map(this::toRow).toList();
        };
        String displayName = "登录日志_" + FILE_TIME.format(q.getStartTime())
                + "_" + FILE_TIME.format(q.getEndTime());
        LogDetail.add("导出登录日志 " + total + " 条，时间范围 "
                + q.getStartTime() + " ~ " + q.getEndTime());
        if ("xlsx".equalsIgnoreCase(format)) {
            logExportSupport.writeExcel(response, displayName, "admin_login_log",
                    "登录日志", headers, total, fetcher);
        } else {
            logExportSupport.writeCsv(response, displayName, "admin_login_log",
                    headers, total, fetcher);
        }
    }

    // ==================== 内部方法 ====================

    private AdminLoginLogVO toVO(AdminLoginLog entity) {
        return AdminLoginLogVO.builder()
                .id(entity.getId())
                .adminId(entity.getAdminId())
                .username(entity.getUsername())
                .loginType(entity.getLoginType())
                .resultCode(entity.getResultCode())
                .reason(entity.getReason())
                .ip(entity.getIp())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /** 导出行：全部转成字符串，公式注入防护由 LogExportSupport 统一处理 */
    private List<String> toRow(AdminLoginLog entity) {
        return List.of(
                str(entity.getId()),
                str(entity.getAdminId()),
                str(entity.getUsername()),
                str(entity.getLoginType()),
                str(entity.getResultCode()),
                str(entity.getReason()),
                str(entity.getIp()),
                str(entity.getUserAgent()),
                str(entity.getCreatedAt()));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
