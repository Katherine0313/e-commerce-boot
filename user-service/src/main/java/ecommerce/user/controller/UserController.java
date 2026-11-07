package ecommerce.user.controller;


import ecommerce.common.Result;
import ecommerce.user.dto.PhoneDTO;
import ecommerce.user.dto.UpdateNameDTO;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.UserProfileVO;
import ecommerce.utils.UrlUtil;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "小程序用户资料", description = "微信小程序用户资料相关接口")
//接口前缀
@RequestMapping("/api/user")
@RestController
public class UserController {
    private final UserService userService;

    // 构造方法：构造器注入
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 获取用户资料
    @Operation(summary = "获取用户资料")
    @GetMapping("/profile")
    public Result<UserProfileVO> profile(HttpServletRequest request) {
        // 从上下文获取本次请求登录的用户ID
        Long userId = UserContext.getUserId();
        // 根据ID查询用户资料，组装VO
        UserProfileVO vo = userService.getProfile(userId);
        // 数据库存头像相对路径,拼接域名
        vo.setAvatar(fullUrl(request, vo.getAvatar()));
        // 返回统一成功响应
        return Result.success(vo);
    }
    /**
     * 这是java的多行注释
     * 可以注释多行文本
     */

    @Operation(summary = "上传头像")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       HttpServletRequest request) {
        // 1. 从上下文拿到id
        Long userId = UserContext.getUserId();
        // 2. 调用业务层，执行业务逻辑
        String relativeUrl = userService.uploadAvatar(userId, file);
        // 3. 调用工具方法拼接完整Url
        return Result.success(fullUrl(request, relativeUrl));
    }

    // 设置昵称：更新用户昵称，返回去除首尾空格后的昵称
    @Operation(summary = "设置昵称")
    @PostMapping("/name")
    public Result<String> updateName(@RequestBody UpdateNameDTO dto) {
        // 从上下文拿到当前登录用户 ID，交给业务层处理
        Long userId = UserContext.getUserId();
        return Result.success(userService.updateName(userId, dto.getName()));
    }

    // 绑定手机号：用 getPhoneNumber 按钮授权拿到的 code 换手机号并写库
    @Operation(summary = "绑定手机号")
    @PostMapping("/phone")
    public Result<String> bindPhone(@RequestBody PhoneDTO dto) {
        // 从上下文拿到当前登录用户 ID，交给业务层处理
        Long userId = UserContext.getUserId();
        return Result.success(userService.bindPhone(userId, dto.getPhone(), dto.getCode()));
    }

    // 注销账号：软删除当前用户（is_deleted=1）并清空购物车，同一事务
    @Operation(summary = "注销账号")
    @PostMapping("/deactivate")
    public Result<Void> deactivate() {
        userService.deactivate(UserContext.getUserId());
        return Result.success();
    }

    public String fullUrl(HttpServletRequest request, String relativeUrl) {
        // 拼域名统一走 UrlUtil（经网关时 Host 会被改写，详见该类注释）
        return UrlUtil.fullUrl(request, relativeUrl);
    }

}
