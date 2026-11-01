package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.service.UploadService;
import ecommerce.product.vo.UploadVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片/视频上传实现（后台商品图与 C 端评价晒图共用）。
 * <p>
 * 落盘规则与现有数据对齐：商品主图 → upload/image/upload/，富文本正文图 → upload/image/body/，
 * 分类图 → upload/image/category/，售后凭证图 → upload/image/after-sale/，评价晒图/视频 → upload/image/review/，
 * 意见反馈截图 → upload/image/feedback/，Banner 图 → upload/image/banner/，文章封面图 → upload/image/article/。
 * 磁盘根目录与 WebMvcConfig 的 /image/** → file:upload/image/ 静态映射一致，存完返回 /image/{sub}/{文件名}
 * 相对路径，前端直接存库，展示走网关 /image/** 托管。
 */
@Service
public class UploadServiceImpl implements UploadService {

    /** 图片上传根目录（相对运行目录），对应 WebMvcConfig 的 /image/** → file:upload/image/ */
    private static final String IMAGE_DIR = "upload/image/";

    /** type → 子目录；缺省或未知一律归商品主图 upload/ */
    private static final Map<String, String> TYPE_SUBDIR = Map.of(
            "body", "body",
            "category", "category",
            "after-sale", "after-sale",
            "review", "review",
            "feedback", "feedback",
            "banner", "banner",
            "article", "article"
    );

    /**
     * 允许上传的媒体类型 → 落盘后缀（图片 + 视频）。
     * <p>
     * 用白名单而非 contentType.startsWith("image/")：后者会放行 image/svg+xml，
     * SVG 能内嵌脚本，配上静态直接访问就是 XSS；同时白名单直接给出规范后缀，
     * 避免从 Content-Type 里切出 "svg+xml" 这类非法扩展名。与头像上传同款约束。
     * 视频只放行 mp4（小程序 chooseVideo 产物，兼容性与体积最稳）。
     */
    private static final Map<String, String> ALLOWED_MEDIA_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "video/mp4", "mp4"
    );

    @Override
    public UploadVO upload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }
        // 白名单校验；Content-Type 由客户端提供，大小写和参数都可能不规范，先归一化
        String contentType = normalizeContentType(file.getContentType());
        String suffix = contentType == null ? null : ALLOWED_MEDIA_TYPES.get(contentType);
        if (suffix == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只支持 png / jpg / gif / webp 图片或 mp4 视频");
        }

        // Map.of 是不可变 Map，key 为 null 时 get/getOrDefault 会抛 NPE；
        // 商品主图上传不带 type，这里先判空，缺省归到 upload/
        String sub = (type == null || type.isBlank()) ? "upload" : TYPE_SUBDIR.getOrDefault(type, "upload");
        String dirPath = IMAGE_DIR + sub + "/";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // UUID 文件名：与 body 图一致，避免数字编号覆盖冲突
        String filename = UUID.randomUUID() + "." + suffix;
        try {
            file.transferTo(new File(dir, filename).toPath());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
        return UploadVO.builder().url("/image/" + sub + "/" + filename).build();
    }

    /** 归一化 Content-Type：统一小写、去掉「;」后面的参数（如 charset），便于按白名单比对；空值原样返回 */
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        return semicolon > 0 ? normalized.substring(0, semicolon).trim() : normalized;
    }
}
