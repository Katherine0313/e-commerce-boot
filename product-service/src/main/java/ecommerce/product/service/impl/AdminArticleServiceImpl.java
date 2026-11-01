package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminArticleDTO;
import ecommerce.product.entity.Article;
import ecommerce.product.mapper.ArticleMapper;
import ecommerce.product.service.AdminArticleService;
import ecommerce.product.vo.ArticleVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台文章管理实现。
 * <p>
 * 标题唯一：查全量（含已逻辑删除的记录），因为小程序按标题查详情，重名会查错。
 */
@Service
public class AdminArticleServiceImpl implements AdminArticleService {

    private final ArticleMapper articleMapper;

    public AdminArticleServiceImpl(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    public PageVO<ArticleVO> list(int page, int pageSize, String keyword, Long categoryId, Integer status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        int offset = (safePage - 1) * safePageSize;

        List<ArticleVO> list = articleMapper.listAdmin(safeKeyword, categoryId, status, offset, safePageSize)
                .stream().map(this::toVO).toList();
        long total = articleMapper.countAdmin(safeKeyword, categoryId, status);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public ArticleVO get(Long id) {
        return toVO(requireArticle(id));
    }

    @Override
    @Transactional
    public Long create(AdminArticleDTO dto) {
        validate(dto, null);
        Article article = Article.builder()
                .categoryId(dto.getCategoryId() == null ? 0L : dto.getCategoryId())
                .title(dto.getTitle().trim())
                .author(dto.getAuthor() == null ? "" : dto.getAuthor().trim())
                .cover(dto.getCover() == null ? "" : dto.getCover().trim())
                .content(dto.getContent())
                .introduction(dto.getIntroduction() == null ? "" : dto.getIntroduction().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                // 新建默认禁用，避免半成品文章直接对外可见，运营确认后再启用
                .status(dto.getStatus() == null ? 0 : dto.getStatus())
                .recommendHot(dto.getRecommendHot() == null ? 0 : dto.getRecommendHot())
                .recommendBanner(dto.getRecommendBanner() == null ? 0 : dto.getRecommendBanner())
                .spuId(dto.getSpuId() == null ? 0L : dto.getSpuId())
                .isDeleted(0)
                .createTime(LocalDateTime.now())
                .build();
        articleMapper.insert(article);
        return article.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminArticleDTO dto) {
        requireArticle(id);
        validate(dto, id);
        Article article = Article.builder()
                .id(id)
                .categoryId(dto.getCategoryId() == null ? 0L : dto.getCategoryId())
                .title(dto.getTitle().trim())
                .author(dto.getAuthor() == null ? "" : dto.getAuthor().trim())
                .cover(dto.getCover() == null ? "" : dto.getCover().trim())
                .content(dto.getContent())
                .introduction(dto.getIntroduction() == null ? "" : dto.getIntroduction().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 0 : dto.getStatus())
                .recommendHot(dto.getRecommendHot() == null ? 0 : dto.getRecommendHot())
                .recommendBanner(dto.getRecommendBanner() == null ? 0 : dto.getRecommendBanner())
                .spuId(dto.getSpuId() == null ? 0L : dto.getSpuId())
                .updateTime(LocalDateTime.now())
                .build();
        articleMapper.update(article);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        requireArticle(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
        articleMapper.updateStatus(id, status, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireArticle(id);
        articleMapper.softDelete(id, LocalDateTime.now());
    }

    private Article requireArticle(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 表单校验：标题必填且唯一（查全量，含已删除的记录）、正文必填。
     */
    private void validate(AdminArticleDTO dto, Long excludeId) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入标题");
        }
        String title = dto.getTitle().trim();
        if (title.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标题不能超过 128 个字符");
        }
        if (articleMapper.countAliveByTitle(title, excludeId) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标题已存在，请换一个");
        }
        if (articleMapper.countByTitle(title, excludeId) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标题已被删除的文章占用，请换一个");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写正文内容");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
