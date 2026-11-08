package ecommerce.system.controller.system;

import ecommerce.system.dto.ApiDocUpdateDTO;
import ecommerce.system.entity.AdminApiDoc;
import ecommerce.system.service.ApiDocService;
import ecommerce.system.service.ApiDocSyncService;
import ecommerce.system.vo.ApiGroupVO;
import ecommerce.system.vo.ApiSyncResultVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统管理 - 接口管理 */
@Tag(name = "系统管理-接口管理")
@RestController
@RequestMapping("/api/admin/system/api-docs")
public class AdminApiDocController {

    private final ApiDocService apiDocService;
    private final ApiDocSyncService apiDocSyncService;

    public AdminApiDocController(ApiDocService apiDocService, ApiDocSyncService apiDocSyncService) {
        this.apiDocService = apiDocService;
        this.apiDocSyncService = apiDocSyncService;
    }

    @Operation(summary = "已同步的服务列表")
    @RequirePerm("system:api:list")
    @GetMapping("/services")
    public Result<List<String>> services() {
        return Result.success(apiDocService.listServices());
    }

    @Operation(summary = "从网关 OpenAPI 同步接口")
    @RequirePerm("system:api:sync")
    @OperationLog(module = "system", action = "API_SYNC", recordParams = false)
    @PostMapping("/sync")
    public Result<ApiSyncResultVO> sync() {
        return Result.success(apiDocSyncService.sync());
    }

    @Operation(summary = "接口树")
    @RequirePerm("system:api:list")
    @GetMapping("/tree")
    public Result<List<ApiGroupVO>> tree(@RequestParam(required = false) String service,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(apiDocService.tree(service, keyword));
    }

    @Operation(summary = "接口详情")
    @RequirePerm("system:api:list")
    @GetMapping("/{id}")
    public Result<AdminApiDoc> detail(@PathVariable Long id) {
        return Result.success(apiDocService.detail(id));
    }

    @Operation(summary = "编辑接口人工字段")
    @RequirePerm("system:api:edit")
    @OperationLog(module = "system", action = "API_DOC_UPDATE", bizId = "#id")
    @PutMapping("/{id}")
    public Result<AdminApiDoc> update(@PathVariable Long id, @RequestBody ApiDocUpdateDTO dto) {
        return Result.success(apiDocService.update(id, dto));
    }
}
