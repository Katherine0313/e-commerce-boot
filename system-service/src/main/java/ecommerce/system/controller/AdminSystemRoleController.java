package ecommerce.system.controller;

import ecommerce.system.dto.AdminRoleDTO;
import ecommerce.system.dto.AdminRoleMenuDTO;
import ecommerce.system.service.AdminRoleService;
import ecommerce.system.vo.AdminRoleVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台角色管理（系统管理 → 角色管理）。
 */
@Tag(name = "后台角色管理", description = "角色增删改查与菜单分配")
@RestController
@RequirePerm("system:role:list")
@RequestMapping("/api/admin/system/roles")
public class AdminSystemRoleController {

    private final AdminRoleService adminRoleService;

    public AdminSystemRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @Operation(summary = "角色列表")
    @GetMapping
    public Result<List<AdminRoleVO>> list() {
        return Result.success(adminRoleService.list());
    }

    @Operation(summary = "角色详情（含菜单 ID）")
    @GetMapping("/{id}")
    public Result<AdminRoleVO> get(@PathVariable Long id) {
        return Result.success(adminRoleService.get(id));
    }

    @Operation(summary = "新增角色")
    @RequirePerm("system:role:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminRoleDTO dto) {
        return Result.success(adminRoleService.create(dto));
    }

    @Operation(summary = "编辑角色")
    @RequirePerm("system:role:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminRoleDTO dto) {
        adminRoleService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除角色（有账号使用中不可删）")
    @RequirePerm("system:role:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "分配菜单")
    @RequirePerm("system:role:assign")
    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody AdminRoleMenuDTO dto) {
        adminRoleService.assignMenus(id, dto.getMenuIds());
        return Result.success();
    }
}
