package ecommerce.auth.controller.system;

import ecommerce.auth.dto.DbTablesDTO;
import ecommerce.auth.service.DatabaseAdminService;
import ecommerce.auth.service.DatabaseBackupService;
import ecommerce.auth.vo.DbBackupTaskVO;
import ecommerce.auth.vo.DbBackupVO;
import ecommerce.auth.vo.DbColumnVO;
import ecommerce.auth.vo.DbTableVO;
import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.log.OperationLog;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 系统管理 - 数据库管理 */
@Tag(name = "系统管理-数据库管理")
@RestController
@RequestMapping("/api/admin/system/database")
public class AdminDatabaseController {

    private final DatabaseAdminService databaseAdminService;
    private final DatabaseBackupService databaseBackupService;

    public AdminDatabaseController(DatabaseAdminService databaseAdminService,
                                   DatabaseBackupService databaseBackupService) {
        this.databaseAdminService = databaseAdminService;
        this.databaseBackupService = databaseBackupService;
    }

    @Operation(summary = "数据库表清单")
    @RequirePerm("system:db:list")
    @GetMapping("/tables")
    public Result<PageVO<DbTableVO>> listTables(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.success(databaseAdminService.listTables(keyword, page, pageSize));
    }

    @Operation(summary = "表字段列表")
    @RequirePerm("system:db:list")
    @GetMapping("/tables/{table}/columns")
    public Result<List<DbColumnVO>> listColumns(@PathVariable String table) {
        return Result.success(databaseAdminService.listColumns(table));
    }

    @Operation(summary = "批量优化表")
    @RequirePerm("system:db:optimize")
    @OperationLog(module = "system", action = "DB_OPTIMIZE")
    @PostMapping("/tables/optimize")
    public Result<List<String>> optimize(@RequestBody(required = false) DbTablesDTO dto) {
        List<String> tables = dto == null ? null : dto.getTables();
        return Result.success(databaseAdminService.optimizeTables(tables));
    }

    @Operation(summary = "发起备份")
    @RequirePerm("system:db:backup")
    @OperationLog(module = "system", action = "DB_BACKUP")
    @PostMapping("/backups")
    public Result<DbBackupTaskVO> backup(@RequestBody(required = false) DbTablesDTO dto) {
        List<String> tables = dto == null ? null : dto.getTables();
        return Result.success(databaseBackupService.start(tables, UserContext.getUserId()));
    }

    @Operation(summary = "备份任务进度")
    @RequirePerm("system:db:list")
    @GetMapping("/tasks/{taskId}")
    public Result<DbBackupTaskVO> task(@PathVariable String taskId) {
        return Result.success(databaseBackupService.getTask(taskId));
    }

    @Operation(summary = "备份记录")
    @RequirePerm("system:db:list")
    @GetMapping("/backups")
    public Result<List<DbBackupVO>> backups() {
        return Result.success(databaseBackupService.listBackups());
    }

    @Operation(summary = "下载备份")
    @RequirePerm("system:db:download")
    @GetMapping("/backups/{fileName}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        FileSystemResource resource = databaseBackupService.download(fileName);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "删除备份")
    @RequirePerm("system:db:delete")
    @OperationLog(module = "system", action = "DB_DELETE")
    @DeleteMapping("/backups/{fileName}")
    public Result<Void> delete(@PathVariable String fileName) {
        databaseBackupService.deleteBackup(fileName);
        return Result.success();
    }
}
