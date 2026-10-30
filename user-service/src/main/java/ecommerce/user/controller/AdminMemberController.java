package ecommerce.user.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.user.service.MemberService;
import ecommerce.user.vo.AdminMemberVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台会员列表。
 */
@Tag(name = "后台会员", description = "会员列表（等级 / 成长值 / 累计消费）")
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final MemberService memberService;

    public AdminMemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "会员列表（分页 + 等级/昵称/手机号筛选）")
    @GetMapping
    public Result<PageVO<AdminMemberVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) Long levelId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return Result.success(memberService.listMembers(page, pageSize, levelId, keyword, status));
    }

    @Operation(summary = "会员详情（含已注销用户，只读）")
    @GetMapping("/{id}")
    public Result<AdminMemberVO> get(@PathVariable Long id) {
        return Result.success(memberService.getMember(id));
    }

    @Operation(summary = "刷新会员成长值/等级（按订单重新计算）")
    @PostMapping("/{id}/refresh")
    public Result<Void> refresh(@PathVariable Long id) {
        memberService.refreshMember(id);
        return Result.success();
    }
}
