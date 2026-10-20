package ecommerce.product.service;

import ecommerce.product.vo.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台图片上传 Service。
 */
public interface UploadService {

    /**
     * 上传一张商品图片，按 type 分流到不同子目录，返回相对路径。
     *
     * @param file 上传的图片文件
     * @param type 图片用途：body=富文本正文图，category=分类图；缺省/其它=商品主图
     */
    UploadVO upload(MultipartFile file, String type);
}
