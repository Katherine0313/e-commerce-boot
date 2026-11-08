package ecommerce.system.controller;

import ecommerce.system.dto.AdminMenuDTO;
import ecommerce.system.service.AdminMenuService;
import ecommerce.system.service.AdminRoleService;
import ecommerce.system.vo.AdminMenuVO;
import ecommerce.system.vo.MenuVO;
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
 * 后台菜单：角色分配菜单树 + 菜单管理 CRUD。
 */
@Tag(name = "后台菜单", description = "菜单/按钮树与菜单维护")
@RestController
@RequestMapping("/api/admin/system/menus")
public class AdminSystemMenuController {

    private final AdminRoleService adminRoleService;
    private final AdminMenuService adminMenuService;

    public AdminSystemMenuController(AdminRoleService adminRoleService, AdminMenuService adminMenuService) {
        this.adminRoleService = adminRoleService;
        this.adminMenuService = adminMenuService;
    }

    @Operation(summary = "全部菜单树（含按钮，仅启用，用于角色分配）")
    @RequirePerm("system:role:assign")
    @GetMapping("/tree")
    public Result<List<MenuVO>> tree() {
        return Result.success(adminRoleService.menuTree());
    }

    @Operation(summary = "菜单管理树（含停用/隐藏/按钮）")
    @RequirePerm("system:menu:list")
    @GetMapping("/manage-tree")
    public Result<List<AdminMenuVO>> manageTree() {
        return Result.success(adminMenuService.manageTree());
    }

    @Operation(summary = "新增菜单")
    @RequirePerm("system:menu:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminMenuDTO dto) {
        return Result.success(adminMenuService.create(dto));
    }

    @Operation(summary = "编辑菜单")
    @RequirePerm("system:menu:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminMenuDTO dto) {
        adminMenuService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除菜单")
    @RequirePerm("system:menu:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminMenuService.delete(id);
        return Result.success();
    }
}
