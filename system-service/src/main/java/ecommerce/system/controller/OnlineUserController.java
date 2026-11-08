package ecommerce.system.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import ecommerce.system.service.OnlineUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统监控-在线用户", description = "在线会话查询与强制下线")
@RestController
@RequestMapping("/api/admin/monitor")
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    public OnlineUserController(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    /** 在线会话列表（IP 已脱敏） */
    @Operation(summary = "在线用户列表")
    @GetMapping("/online")
    @RequirePerm("monitor:online:list")
    public Result<Map<String, Object>> online(@RequestParam(required = false) String username,
                                              @RequestParam(required = false) String ip) {
        return Result.success(onlineUserService.list(username, ip));
    }

    /** 强制下线：该账号全部会话 + 旧 token 立即失效 */
    @Operation(summary = "强制下线")
    @DeleteMapping("/online/{adminId}")
    @RequirePerm("monitor:online:logout")
    @OperationLog(module = "monitor", action = "FORCE_LOGOUT", bizId = "#adminId")
    public Result<Integer> forceLogout(@PathVariable Long adminId) {
        return Result.success(onlineUserService.forceLogout(adminId));
    }
}
