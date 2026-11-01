package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.entity.Article;
import ecommerce.product.mapper.ArticleMapper;
import ecommerce.product.service.ArticleService;
import ecommerce.product.vo.ArticleVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章 C 端服务实现（PC 商城文章详情页 / 小程序富文本页，免登录）。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

    public ArticleServiceImpl(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    public ArticleVO getById(Long id) {
        Article article = articleMapper.findEnabledById(id);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文章不存在或已下架");
        }
        return toVO(article);
    }

    @Override
    public ArticleVO getByTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少文章标题");
        }
        Article article = articleMapper.findEnabledByTitle(title.trim());
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文章不存在或已下架");
        }
        return toVO(article);
    }

    @Override
    public List<ArticleVO> listEnabled(Integer recommendHot, Integer recommendBanner, Integer limit) {
        return articleMapper.listEnabled(recommendHot, recommendBanner, limit).stream().map(this::toVO).toList();
    }

    @Override
    public void browse(Long id) {
        // 记录不存在（已删除）时更新影响 0 行，直接忽略
        articleMapper.increaseBrowseCount(id);
    }

    private ArticleVO toVO(Article a) {
        return ArticleVO.builder()
                .id(a.getId())
                .categoryId(a.getCategoryId())
                .categoryName(a.getCategoryName())
                .title(a.getTitle())
                .author(a.getAuthor())
                .cover(a.getCover())
                .content(a.getContent())
                .introduction(a.getIntroduction())
                .sortOrder(a.getSortOrder())
                .status(a.getStatus())
                .statusDesc(a.getStatus() != null && a.getStatus() == 1 ? "启用" : "禁用")
                .recommendHot(a.getRecommendHot())
                .recommendBanner(a.getRecommendBanner())
                .browseCount(a.getBrowseCount())
                .spuId(a.getSpuId())
                .createTime(a.getCreateTime())
                .updateTime(a.getUpdateTime())
                .build();
    }
}
