package ecommerce.product.service;

import ecommerce.product.vo.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片/视频上传 Service（后台商品图与 C 端评价晒图共用）。
 */
public interface UploadService {

    /**
     * 上传一个图片/视频文件，按 type 分流到不同子目录，返回相对路径。
     *
     * @param file 上传的图片/视频文件
     * @param type 用途：body=富文本正文图，category=分类图，after-sale=售后凭证图，review=评价晒图；缺省/其它=商品主图
     */
    UploadVO upload(MultipartFile file, String type);
}
