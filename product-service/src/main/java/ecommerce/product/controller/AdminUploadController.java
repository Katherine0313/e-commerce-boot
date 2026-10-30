package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.service.UploadService;
import ecommerce.product.vo.UploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台图片上传接口（/api/admin/upload）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 * 前端商品主图（a-upload，缺省 type）、富文本正文图（type=body）都走这里。
 */
@Tag(name = "后台图片上传", description = "商品主图/正文图/分类图上传（管理员）")
@RestController
@RequirePerm("product:product:add")
@RequestMapping("/api/admin/upload")
public class AdminUploadController {
    private final UploadService uploadService;

    public AdminUploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "上传图片")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String type) {
        return Result.success(uploadService.upload(file, type));
    }
}
