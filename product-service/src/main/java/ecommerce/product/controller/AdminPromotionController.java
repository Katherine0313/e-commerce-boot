package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminPromotionDTO;
import ecommerce.product.service.AdminPromotionService;
import ecommerce.product.vo.AdminPromotionVO;
import ecommerce.product.vo.PromotionStatsVO;
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
 * 后台促销活动管理接口。
 */
@Tag(name = "后台促销管理", description = "促销活动的创建、编辑、提前结束与效果统计")
@RestController
@RequirePerm("business:promotion:list")
@RequestMapping("/api/admin/promotions")
public class AdminPromotionController {

    private final AdminPromotionService adminPromotionService;

    public AdminPromotionController(AdminPromotionService adminPromotionService) {
        this.adminPromotionService = adminPromotionService;
    }

    @Operation(summary = "活动列表（分页 + 名称/状态筛选）")
    @GetMapping
    public Result<PageVO<AdminPromotionVO>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return Result.success(adminPromotionService.list(page, pageSize, keyword, status));
    }

    @Operation(summary = "活动详情（含商品、阶梯与效果统计）")
    @GetMapping("/{id}")
    public Result<AdminPromotionVO> get(@PathVariable Long id) {
        return Result.success(adminPromotionService.get(id));
    }

    @Operation(summary = "新建活动")
    @RequirePerm("business:promotion:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminPromotionDTO dto) {
        return Result.success(adminPromotionService.create(dto));
    }

    @Operation(summary = "编辑活动（仅未开始）")
    @RequirePerm("business:promotion:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminPromotionDTO dto) {
        adminPromotionService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "提前结束活动（仅进行中）")
    @RequirePerm("business:promotion:end")
    @PostMapping("/{id}/end")
    public Result<Void> end(@PathVariable Long id) {
        adminPromotionService.end(id);
        return Result.success();
    }

    @Operation(summary = "删除活动（仅未开始，逻辑删除）")
    @RequirePerm("business:promotion:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminPromotionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "活动效果统计")
    @GetMapping("/{id}/stats")
    public Result<PromotionStatsVO> stats(@PathVariable Long id) {
        return Result.success(adminPromotionService.stats(id));
    }
}
