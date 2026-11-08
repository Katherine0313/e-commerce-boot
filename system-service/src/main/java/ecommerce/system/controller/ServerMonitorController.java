package ecommerce.system.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.system.service.ServerMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统监控-服务器", description = "主机、JVM、容器与中间件健康")
@RestController
@RequestMapping("/api/admin/monitor")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;

    public ServerMonitorController(ServerMonitorService serverMonitorService) {
        this.serverMonitorService = serverMonitorService;
    }

    /** 主机 / JVM / 容器 / 中间件健康 */
    @Operation(summary = "服务器状态")
    @GetMapping("/server")
    @RequirePerm("monitor:server:list")
    public Result<Map<String, Object>> server() {
        return Result.success(serverMonitorService.overview());
    }
}
