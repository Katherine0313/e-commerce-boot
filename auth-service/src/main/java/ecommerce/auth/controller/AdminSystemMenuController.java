package ecommerce.auth.controller;

import ecommerce.auth.service.AdminRoleService;
import ecommerce.auth.vo.MenuVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台菜单树（角色分配用）。
 */
@Tag(name = "后台菜单", description = "菜单/按钮树")
@RestController
@RequestMapping("/api/admin/system/menus")
public class AdminSystemMenuController {

    private final AdminRoleService adminRoleService;

    public AdminSystemMenuController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @Operation(summary = "全部菜单树（含按钮）")
    @RequirePerm("system:role:assign")
    @GetMapping("/tree")
    public Result<List<MenuVO>> tree() {
        return Result.success(adminRoleService.menuTree());
    }
}
