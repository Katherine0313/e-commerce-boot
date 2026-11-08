package ecommerce.task;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务内部的定时任务手动执行入口。
 * <p>
 * 后台页面统一走 system-service 的 {@code /api/admin/monitor/tasks/{name}/run}，
 * system-service 校验任务归属后，携带管理员令牌转发到对应服务的本接口。
 */
@RestController
@RequestMapping("/api/admin/tasks")
public class MonitoredTaskController {

    private final MonitoredTaskService monitoredTaskService;

    public MonitoredTaskController(MonitoredTaskService monitoredTaskService) {
        this.monitoredTaskService = monitoredTaskService;
    }

    /** 当前服务可手动执行的任务清单 */
    @GetMapping("/available")
    @RequirePerm("monitor:task:run")
    public Result<List<String>> available() {
        return Result.success(monitoredTaskService.supportedTasks());
    }

    /** 执行一次 */
    @PostMapping("/{name}/run")
    @RequirePerm("monitor:task:run")
    public Result<Void> run(@PathVariable String name) {
        monitoredTaskService.run(name);
        return Result.success();
    }
}
