package ecommerce.auth.controller;

import ecommerce.auth.dto.AdminAccountDTO;
import ecommerce.auth.dto.ResetPasswordDTO;
import ecommerce.auth.service.AdminAccountService;
import ecommerce.auth.vo.AdminAccountVO;
import ecommerce.common.PageVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理员账号管理（系统管理 → 管理员账号）。
 */
@Tag(name = "后台账号管理", description = "管理员账号增删改查与角色分配")
@RestController
@RequirePerm("system:user:list")
@RequestMapping("/api/admin/system/users")
public class AdminSystemUserController {

    private final AdminAccountService adminAccountService;

    public AdminSystemUserController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Operation(summary = "账号列表")
    @GetMapping
    public Result<PageVO<AdminAccountVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(adminAccountService.list(page, pageSize, keyword, status));
    }

    @Operation(summary = "账号详情")
    @GetMapping("/{id}")
    public Result<AdminAccountVO> get(@PathVariable Long id) {
        return Result.success(adminAccountService.get(id));
    }

    @Operation(summary = "新增账号")
    @RequirePerm("system:user:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminAccountDTO dto) {
        return Result.success(adminAccountService.create(dto));
    }

    @Operation(summary = "编辑账号（显示名/手机号/状态/角色）")
    @RequirePerm("system:user:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminAccountDTO dto) {
        adminAccountService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除账号（软删：列表不再显示，账号立刻退出登录）")
    @RequirePerm("system:user:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminAccountService.delete(id);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @RequirePerm("system:user:resetPwd")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordDTO dto) {
        adminAccountService.resetPassword(id, dto.getPassword());
        return Result.success();
    }
}
