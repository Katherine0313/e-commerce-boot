package ecommerce.auth.controller;

import ecommerce.auth.dto.AdminLoginLogQuery;
import ecommerce.auth.dto.AdminOperationLogQuery;
import ecommerce.auth.service.LoginLogService;
import ecommerce.auth.service.OperationLogService;
import ecommerce.auth.vo.AdminLoginLogVO;
import ecommerce.auth.vo.AdminOperationLogVO;
import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台日志查询（系统管理 → 操作日志 / 登录日志）。
 * <p>
 * 类上放 system:log:list，方法上放各自的权限码：
 * PermissionInterceptor 优先读方法上的注解，所以查操作日志和导出操作日志可以分开授权。
 */
@Tag(name = "后台日志", description = "操作日志 / 登录日志的查询与导出")
@RestController
@RequirePerm("system:log:list")
@RequestMapping("/api/admin/system")
public class AdminLogController {

    /** 时间参数格式：与全局 Jackson 的 yyyy-MM-dd HH:mm:ss 保持一致 */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final OperationLogService operationLogService;
    private final LoginLogService loginLogService;

    public AdminLogController(OperationLogService operationLogService,
                              LoginLogService loginLogService) {
        this.operationLogService = operationLogService;
        this.loginLogService = loginLogService;
    }

    @Operation(summary = "操作日志列表")
    @GetMapping("/logs")
    public Result<PageVO<AdminOperationLogVO>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime,
            @RequestParam(required = false) String keyword) {
        AdminOperationLogQuery query = new AdminOperationLogQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setModule(module);
        query.setAction(action);
        query.setUsername(username);
        query.setSuccess(success);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);
        return Result.success(operationLogService.page(query));
    }

    @Operation(summary = "导出操作日志（csv / xlsx）")
    @RequirePerm("system:log:export")
    @GetMapping("/logs/export")
    public void exportLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) {
        AdminOperationLogQuery query = new AdminOperationLogQuery();
        query.setModule(module);
        query.setAction(action);
        query.setUsername(username);
        query.setSuccess(success);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);
        operationLogService.export(query, format, response);
    }

    @Operation(summary = "登录日志列表")
    @RequirePerm("system:log:login:list")
    @GetMapping("/login-logs")
    public Result<PageVO<AdminLoginLogVO>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime) {
        AdminLoginLogQuery query = new AdminLoginLogQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setUsername(username);
        query.setLoginType(loginType);
        query.setResultCode(resultCode);
        query.setIp(ip);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return Result.success(loginLogService.page(query));
    }

    @Operation(summary = "导出登录日志（csv / xlsx）")
    @RequirePerm("system:log:login:export")
    @GetMapping("/login-logs/export")
    public void exportLoginLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = TIME_PATTERN) LocalDateTime endTime,
            HttpServletResponse response) {
        AdminLoginLogQuery query = new AdminLoginLogQuery();
        query.setUsername(username);
        query.setLoginType(loginType);
        query.setResultCode(resultCode);
        query.setIp(ip);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        loginLogService.export(query, format, response);
    }
}
