package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.AdminReviewDTO;
import ecommerce.order.dto.AdminReviewQueryDTO;
import ecommerce.order.service.AdminReviewService;
import ecommerce.order.vo.AdminReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台商品评价管理接口（/api/admin/reviews）。
 * <p>
 * 商家对评价的操作：查、回复、显示/隐藏、删除、导出；**新增和编辑只对虚拟评价开放**，
 * 买家真实评价整条只读（Service 层拦 + SQL 的 is_virtual = 1 再兜一道）。
 */
@Tag(name = "后台商品评价管理", description = "评价列表 + 回复/隐藏/删除/导出 + 虚拟评价维护")
@RestController
@RequirePerm("product:review:list")
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    @Operation(summary = "评价列表（分页 + 商品名/订单号/星级/类型/显示状态/时间筛选）")
    @GetMapping
    public Result<PageVO<AdminReviewVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "13") int pageSize,
                                               @ModelAttribute AdminReviewQueryDTO query) {
        return Result.success(adminReviewService.list(page, pageSize, query));
    }

    @Operation(summary = "评价详情")
    @GetMapping("/{id}")
    public Result<AdminReviewVO> get(@PathVariable Long id) {
        return Result.success(adminReviewService.detail(id));
    }

    @Operation(summary = "新增虚拟评价（不挂真实订单，商品名/图取商品表快照）")
    @RequirePerm("product:review:add")
    @PostMapping
    public Result<AdminReviewVO> create(@RequestBody AdminReviewDTO dto) {
        return Result.success(adminReviewService.createVirtual(dto));
    }

    @Operation(summary = "编辑虚拟评价（买家真实评价不可编辑）")
    @RequirePerm("product:review:edit")
    @PutMapping("/{id}")
    public Result<AdminReviewVO> update(@PathVariable Long id, @RequestBody AdminReviewDTO dto) {
        return Result.success(adminReviewService.update(id, dto));
    }

    @Operation(summary = "回复评价（一条评价只保留一条回复，再次回复覆盖）")
    @RequirePerm("product:review:reply")
    @PutMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminReviewService.reply(id, body == null ? null : body.get("reply"));
        return Result.success();
    }

    @Operation(summary = "显示/隐藏评价（visible=1 显示 0 隐藏）")
    @RequirePerm("product:review:edit")
    @PutMapping("/{id}/visible")
    public Result<Void> updateVisible(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminReviewService.updateVisible(id, body == null ? null : body.get("visible"));
        return Result.success();
    }

    @Operation(summary = "删除评价（逻辑删除）")
    @RequirePerm("product:review:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminReviewService.delete(id);
        return Result.success();
    }

    @Operation(summary = "导出评价 Excel（与列表同一套筛选条件）")
    @RequirePerm("product:review:export")
    @GetMapping("/export")
    public void export(HttpServletResponse response, @ModelAttribute AdminReviewQueryDTO query) {
        adminReviewService.exportExcel(response, query);
    }
}
