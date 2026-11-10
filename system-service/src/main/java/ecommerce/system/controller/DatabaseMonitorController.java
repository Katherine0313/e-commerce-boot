package ecommerce.system.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import ecommerce.system.service.DatabaseMonitorService;
import ecommerce.system.vo.DbTableSizeVO;
import ecommerce.system.vo.SlowSqlTopVO;
import ecommerce.system.vo.SlowSqlTrendVO;
import ecommerce.system.vo.SlowSqlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统监控 - 数据库监控。
 * <p>
 * 网关 /api/admin/monitor/** 已路由到 system-service，无需改网关。
 */
@Tag(name = "系统监控-数据库", description = "慢 SQL、连接池与库表容量")
@RestController
@RequestMapping("/api/admin/monitor/database")
public class DatabaseMonitorController {

    private final DatabaseMonitorService databaseMonitorService;

    public DatabaseMonitorController(DatabaseMonitorService databaseMonitorService) {
        this.databaseMonitorService = databaseMonitorService;
    }

    @Operation(summary = "数据库总览")
    @GetMapping("/overview")
    @RequirePerm("monitor:db:list")
    public Result<Map<String, Object>> overview() {
        return Result.success(databaseMonitorService.overview());
    }

    @Operation(summary = "慢 SQL 明细分页")
    @GetMapping("/slow-sqls")
    @RequirePerm("monitor:db:list")
    public Result<PageVO<SlowSqlVO>> slowSqls(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Integer minCost,
            @RequestParam(defaultValue = "24") Integer hours,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(databaseMonitorService.slowSqls(service, minCost, hours, page, pageSize));
    }

    @Operation(summary = "慢 SQL 指纹 Top N")
    @GetMapping("/slow-sqls/top")
    @RequirePerm("monitor:db:list")
    public Result<List<SlowSqlTopVO>> top(
            @RequestParam(defaultValue = "24") Integer hours,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(databaseMonitorService.topSlowSqls(hours, limit));
    }

    @Operation(summary = "慢 SQL 按小时趋势")
    @GetMapping("/slow-sqls/trend")
    @RequirePerm("monitor:db:list")
    public Result<List<SlowSqlTrendVO>> trend(@RequestParam(defaultValue = "24") Integer hours) {
        return Result.success(databaseMonitorService.slowSqlTrend(hours));
    }

    @Operation(summary = "表容量 Top N")
    @GetMapping("/tables")
    @RequirePerm("monitor:db:list")
    public Result<List<DbTableSizeVO>> tables(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(databaseMonitorService.tableSizes(limit));
    }

    @Operation(summary = "连接池状态（各服务 Hikari）")
    @GetMapping("/pools")
    @RequirePerm("monitor:db:list")
    public Result<List<Map<String, Object>>> pools() {
        return Result.success(databaseMonitorService.poolStatus());
    }

    @Operation(summary = "清空历史慢 SQL")
    @DeleteMapping("/slow-sqls")
    @RequirePerm("monitor:db:clear")
    @OperationLog(module = "monitor", action = "DB_SLOW_SQL_CLEAR")
    public Result<Map<String, Object>> clear(
            @RequestParam(defaultValue = "7") Integer beforeDays) {
        int deleted = databaseMonitorService.clear(beforeDays);
        return Result.success(Map.of("deleted", deleted));
    }
}
