package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.exception.BusinessException;
import ecommerce.product.service.UploadService;
import ecommerce.product.vo.UploadVO;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 后台图片上传接口（/api/admin/upload）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 * 前端商品主图（a-upload，缺省 type）、富文本正文图（type=body）都走这里。
 * <p>
 * 权限按上传类型区分：Banner 图要 business:banner:add，文章封面图要 business:article:add，
 * 其余（商品主图等）沿用 product:product:add —— 运营角色没有商品权限时也能传 Banner 图。
 */
@Tag(name = "后台图片上传", description = "商品主图/正文图/分类图/Banner图/文章封面图上传（管理员）")
@RestController
@RequestMapping("/api/admin/upload")
public class AdminUploadController {

    /** 超级管理员权限标记 */
    private static final String ALL_PERMISSION = "*:*:*";

    private final UploadService uploadService;

    public AdminUploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "上传图片")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String type) {
        assertPermission(type);
        return Result.success(uploadService.upload(file, type));
    }

    /** 按上传类型校验权限 */
    private void assertPermission(String type) {
        List<String> allowed = switch (type == null ? "" : type) {
            case "banner" -> List.of("business:banner:add");
            case "article" -> List.of("business:article:add");
            // 正文图：商品详情与文章正文共用 type=body，两边的编辑权限都能传
            case "body" -> List.of("product:product:add", "business:article:add");
            // 评价晒图：后台评价模块上传的图，只给评价权限的角色
            case "review" -> List.of("product:review:add", "product:review:edit");
            // 快递公司 logo：发货管理的字典维护权限
            case "express" -> List.of("order:express:save");
            default -> List.of("product:product:add");
        };
        List<String> permissions = UserContext.getPermissions();
        if (permissions.contains(ALL_PERMISSION)) {
            return;
        }
        for (String perm : allowed) {
            if (permissions.contains(perm)) {
                return;
            }
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问该功能");
    }
}
