package ecommerce.system.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import ecommerce.system.service.TaskMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitor")
public class TaskMonitorController {

    private final TaskMonitorService taskMonitorService;

    public TaskMonitorController(TaskMonitorService taskMonitorService) {
        this.taskMonitorService = taskMonitorService;
    }

    /** 任务清单 + 最近执行 */
    @GetMapping("/tasks")
    @RequirePerm("monitor:task:list")
    public Result<List<Map<String, Object>>> tasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(taskMonitorService.list(keyword, status));
    }

    @PostMapping("/tasks")
    @RequirePerm("monitor:task:add")
    @OperationLog(module = "monitor", action = "TASK_ADD")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.success(taskMonitorService.create(body));
    }

    @PutMapping("/tasks/{id}")
    @RequirePerm("monitor:task:edit")
    @OperationLog(module = "monitor", action = "TASK_EDIT", bizId = "#id")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.success(taskMonitorService.update(id, body));
    }

    @DeleteMapping("/tasks/{id}")
    @RequirePerm("monitor:task:delete")
    @OperationLog(module = "monitor", action = "TASK_DELETE", bizId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        taskMonitorService.delete(id);
        return Result.success();
    }

    /** 启停：调度器 30 秒内生效 */
    @PutMapping("/tasks/{id}/status")
    @RequirePerm("monitor:task:edit")
    @OperationLog(module = "monitor", action = "TASK_STATUS", bizId = "#id")
    public Result<Map<String, Object>> changeStatus(@PathVariable Long id,
                                                    @RequestParam int status) {
        return Result.success(taskMonitorService.changeStatus(id, status));
    }

    /** 执行记录（默认最近 50 条） */
    @GetMapping("/tasks/{id}/logs")
    @RequirePerm("monitor:task:list")
    public Result<List<Map<String, Object>>> logs(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return Result.success(taskMonitorService.logs(id, limit));
    }

    /** 白名单任务手动执行一次 */
    @PostMapping("/tasks/{name}/run")
    @RequirePerm("monitor:task:run")
    @OperationLog(module = "monitor", action = "TASK_RUN", bizId = "#name")
    public Result<Map<String, Object>> run(@PathVariable String name, HttpServletRequest request) {
        return Result.success(taskMonitorService.run(name, request.getHeader("Authorization")));
    }
}
