package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.service.MemberService;
import ecommerce.user.vo.MemberLevelVO;
import ecommerce.user.vo.MemberProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端会员接口（需登录）。
 */
@Tag(name = "会员", description = "会员中心：等级、成长值、等级列表")
@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "我的会员档案（顺带刷新成长值与等级）")
    @GetMapping("/profile")
    public Result<MemberProfileVO> profile() {
        return Result.success(memberService.getProfile());
    }

    @Operation(summary = "会员等级列表")
    @GetMapping("/levels")
    public Result<List<MemberLevelVO>> levels() {
        return Result.success(memberService.listLevels());
    }
}
