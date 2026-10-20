package ecommerce.product.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传结果。
 * <p>
 * 前端上传成功后从 {@code response.data.url} 取相对路径（如 /image/upload/xxx.webp），
 * 直接存进 products.image，展示时经网关 /image/** 静态托管。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadVO {
    /** 图片相对路径，如 /image/upload/uuid.webp、/image/body/uuid.webp */
    private String url;
}
