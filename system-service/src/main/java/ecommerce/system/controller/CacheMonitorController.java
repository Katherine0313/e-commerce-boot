package ecommerce.system.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import ecommerce.system.service.CacheMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统监控-缓存", description = "Redis 概览、Key 查询与删除")
@RestController
@RequestMapping("/api/admin/monitor")
public class CacheMonitorController {

    private final CacheMonitorService cacheMonitorService;

    public CacheMonitorController(CacheMonitorService cacheMonitorService) {
        this.cacheMonitorService = cacheMonitorService;
    }

    /** Redis 概览（INFO server/clients/memory/stats/keyspace） */
    @Operation(summary = "缓存概览")
    @GetMapping("/cache/overview")
    @RequirePerm("monitor:cache:list")
    public Result<Map<String, Object>> overview() {
        return Result.success(cacheMonitorService.overview());
    }

    /** SCAN 游标分页查询 key */
    @Operation(summary = "缓存 Key 列表")
    @GetMapping("/cache/keys")
    @RequirePerm("monitor:cache:list")
    public Result<Map<String, Object>> keys(@RequestParam(required = false) String prefix,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "0") long cursor,
                                            @RequestParam(defaultValue = "100") int pageSize) {
        return Result.success(cacheMonitorService.keys(prefix, keyword, cursor, pageSize));
    }

    /** 查看完整 key：列表只给脱敏值，这里单独授权并留操作日志 */
    @Operation(summary = "缓存 Key 详情")
    @GetMapping("/cache/key")
    @RequirePerm("monitor:cache:key:view")
    @OperationLog(module = "monitor", action = "CACHE_KEY_VIEW")
    public Result<String> reveal(@RequestParam String token) {
        return Result.success(cacheMonitorService.reveal(token));
    }

    /** 删除单个 key（按列表返回的 token 定位；危险 key 后端直接拒绝） */
    @Operation(summary = "删除缓存 Key")
    @DeleteMapping("/cache/key")
    @RequirePerm("monitor:cache:delete")
    @OperationLog(module = "monitor", action = "CACHE_DELETE")
    public Result<Boolean> delete(@RequestParam String token) {
        return Result.success(cacheMonitorService.deleteByToken(token));
    }
}
