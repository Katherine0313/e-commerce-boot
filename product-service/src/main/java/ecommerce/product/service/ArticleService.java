package ecommerce.product.service;

import ecommerce.product.vo.ArticleVO;

import java.util.List;

/**
 * 文章 C 端服务（PC 商城文章详情页 / 小程序富文本页，免登录）。
 */
public interface ArticleService {

    /** 按 ID 取启用中的文章详情 */
    ArticleVO getById(Long id);

    /** 按标题取启用中的文章详情（小程序富文本页用） */
    ArticleVO getByTitle(String title);

    /** 启用中的文章列表（可按热门 / 轮播过滤） */
    List<ArticleVO> listEnabled(Integer recommendHot, Integer recommendBanner, Integer limit);

    /** 浏览量 +1 */
    void browse(Long id);
}
