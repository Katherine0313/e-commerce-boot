package ecommerce.product.controller;

import ecommerce.common.Result;
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
 * C 端图片/视频上传接口（/api/upload）。
 * <p>
 * 需登录（不在 WebMvcConfig 免登录白名单里，JwtInterceptor 按 user 令牌校验），
 * 供评价晒图等 C 端场景上传图片/视频；type 分流目录（review=评价晒图）。
 * 与 AdminUploadController（/api/admin/upload，后台商品图）共用 UploadService。
 */
@Tag(name = "C端上传", description = "评价晒图等 C 端图片/视频上传（需登录）")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "上传图片/视频")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String type) {
        return Result.success(uploadService.upload(file, type));
    }
}
