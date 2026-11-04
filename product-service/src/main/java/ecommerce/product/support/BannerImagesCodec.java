package ecommerce.product.support;

import ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Banner 图片列表的 JSON 编解码。
 * <p>
 * 数据库 promotion_banner.images 存 JSON 数组；image 字段保留首图，兼容只认单图的旧客户端。
 */
@Slf4j
@Component
public class BannerImagesCodec {

    /** 一条 Banner 最多 4 张图 */
    private static final int MAX_IMAGES = 4;
    /** 单张图片地址长度上限，防止脏数据撑爆列表响应 */
    private static final int MAX_PATH_LENGTH = 255;

    private final ObjectMapper objectMapper;

    public BannerImagesCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 后台入参 -> 数据库 JSON 字符串；同时校验 1~4 张、路径非空并去重 */
    public String encode(List<String> images) {
        List<String> normalized = normalize(images);
        if (normalized.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传 Banner 图");
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最多上传 " + MAX_IMAGES + " 张图");
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "图片列表格式不合法");
        }
    }

    /** 数据库 JSON -> 列表；JSON 为空时回落到单图字段；脏数据只记日志并返回空列表 */
    public List<String> decode(String json, String fallbackImage) {
        if (json == null || json.isBlank()) {
            return fallbackImage == null || fallbackImage.isBlank() ? List.of() : List.of(fallbackImage);
        }
        try {
            String[] array = objectMapper.readValue(json, String[].class);
            if (array == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>(array.length);
            for (String image : array) {
                if (image != null && !image.isBlank()) {
                    result.add(image.trim());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Banner images 反序列化失败，按空列表处理: {}", e.getMessage());
            return fallbackImage == null || fallbackImage.isBlank() ? List.of() : List.of(fallbackImage);
        }
    }

    /** 去空、去重（保持上传顺序），并校验单张路径长度 */
    private List<String> normalize(List<String> images) {
        if (images == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(images.size());
        for (String image : images) {
            if (image == null || image.isBlank()) {
                continue;
            }
            String trimmed = image.trim();
            if (trimmed.length() > MAX_PATH_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "图片地址过长");
            }
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
