package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.dto.AdminMemberLevelDTO;
import ecommerce.user.service.MemberService;
import ecommerce.user.vo.MemberLevelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台会员等级配置。
 */
@Tag(name = "后台会员等级", description = "会员等级名称 / 成长值门槛 / 权益文案")
@RestController
@RequestMapping("/api/admin/member-levels")
public class AdminMemberLevelController {

    private final MemberService memberService;

    public AdminMemberLevelController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "等级列表")
    @GetMapping
    public Result<List<MemberLevelVO>> list() {
        return Result.success(memberService.listLevels());
    }

    @Operation(summary = "编辑等级")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminMemberLevelDTO dto) {
        memberService.updateLevel(id, dto);
        return Result.success();
    }
}
