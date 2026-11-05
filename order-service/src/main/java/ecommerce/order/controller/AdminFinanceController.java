package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.FinanceAccountDTO;
import ecommerce.order.dto.FinanceAccountQueryDTO;
import ecommerce.order.dto.FinanceReceiptQueryDTO;
import ecommerce.order.service.FinanceAccountService;
import ecommerce.order.service.FinanceReceiptService;
import ecommerce.order.vo.FinanceAccountVO;
import ecommerce.order.vo.FinanceReceiptSummaryVO;
import ecommerce.order.vo.FinanceReceiptVO;
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

import java.util.List;
import java.util.Map;

/**
 * 后台财务管理接口（/api/admin/finance）。
 * <p>
 * 收款单由支付成功自动生成，本控制器只负责查、审、导出；
 * 收款账户单独维护，用于按渠道归集。
 */
@Tag(name = "后台财务管理", description = "收款单审核/导出 + 收款账户维护")
@RestController
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {

    private final FinanceReceiptService financeReceiptService;
    private final FinanceAccountService financeAccountService;

    public AdminFinanceController(FinanceReceiptService financeReceiptService,
                                  FinanceAccountService financeAccountService) {
        this.financeReceiptService = financeReceiptService;
        this.financeAccountService = financeAccountService;
    }

    // ==================== 收款单 ====================

    @Operation(summary = "收款单列表（分页 + 单号/订单号/买家/渠道/状态/时间筛选）")
    @RequirePerm("finance:receipt:list")
    @GetMapping("/receipts")
    public Result<PageVO<FinanceReceiptVO>> listReceipts(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "13") int pageSize,
                                                         @ModelAttribute FinanceReceiptQueryDTO query) {
        return Result.success(financeReceiptService.list(page, pageSize, query));
    }

    @Operation(summary = "收款单统计卡（已审核实收 / 退款 / 净收款 / 待审核笔数）")
    @RequirePerm("finance:receipt:list")
    @GetMapping("/receipts/summary")
    public Result<FinanceReceiptSummaryVO> summary(@ModelAttribute FinanceReceiptQueryDTO query) {
        return Result.success(financeReceiptService.summary(query));
    }

    @Operation(summary = "导出收款单 Excel（与列表同一套筛选条件）")
    @RequirePerm("finance:receipt:export")
    @GetMapping("/receipts/export")
    public void exportReceipts(HttpServletResponse response, @ModelAttribute FinanceReceiptQueryDTO query) {
        financeReceiptService.exportExcel(response, query);
    }

    @Operation(summary = "收款单详情（单据 + 订单摘要 + 退款标记）")
    @RequirePerm("finance:receipt:detail")
    @GetMapping("/receipts/{id}")
    public Result<FinanceReceiptVO> getReceipt(@PathVariable Long id) {
        return Result.success(financeReceiptService.detail(id));
    }

    @Operation(summary = "审核收款单（仅待审核可审核，审核后为终态）")
    @RequirePerm("finance:receipt:audit")
    @PutMapping("/receipts/{id}/audit")
    public Result<Void> auditReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        financeReceiptService.audit(id, body == null ? null : body.get("remark"));
        return Result.success();
    }

    @Operation(summary = "修改收款单备注（已审核只读）")
    @RequirePerm("finance:receipt:detail")
    @PutMapping("/receipts/{id}/remark")
    public Result<Void> updateReceiptRemark(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        financeReceiptService.updateRemark(id, body == null ? null : body.get("remark"));
        return Result.success();
    }

    // ==================== 收款账户 ====================

    @Operation(summary = "收款账户列表（分页 + 渠道/状态/关键字筛选）")
    @RequirePerm("finance:account:list")
    @GetMapping("/accounts")
    public Result<PageVO<FinanceAccountVO>> listAccounts(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @ModelAttribute FinanceAccountQueryDTO query) {
        return Result.success(financeAccountService.list(page, pageSize, query));
    }

    @Operation(summary = "启用中的收款账户（下拉用）")
    @RequirePerm("finance:account:list")
    @GetMapping("/accounts/enabled")
    public Result<List<FinanceAccountVO>> listEnabledAccounts() {
        return Result.success(financeAccountService.listEnabled());
    }

    @Operation(summary = "新增收款账户")
    @RequirePerm("finance:account:add")
    @PostMapping("/accounts")
    public Result<FinanceAccountVO> createAccount(@RequestBody FinanceAccountDTO dto) {
        return Result.success(financeAccountService.create(dto));
    }

    @Operation(summary = "编辑收款账户（名称 / 商户号 / 备注）")
    @RequirePerm("finance:account:edit")
    @PutMapping("/accounts/{id}")
    public Result<FinanceAccountVO> updateAccount(@PathVariable Long id, @RequestBody FinanceAccountDTO dto) {
        return Result.success(financeAccountService.update(id, dto));
    }

    @Operation(summary = "启停收款账户（status=1 启用 0 停用）")
    @RequirePerm("finance:account:edit")
    @PutMapping("/accounts/{id}/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        financeAccountService.updateStatus(id, body == null ? null : body.get("status"));
        return Result.success();
    }

    @Operation(summary = "设为同渠道默认账户（互斥）")
    @RequirePerm("finance:account:edit")
    @PutMapping("/accounts/{id}/default")
    public Result<Void> setDefaultAccount(@PathVariable Long id) {
        financeAccountService.setDefault(id);
        return Result.success();
    }

    @Operation(summary = "删除收款账户（软删除，历史单据仍可查）")
    @RequirePerm("finance:account:remove")
    @DeleteMapping("/accounts/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        financeAccountService.delete(id);
        return Result.success();
    }
}
