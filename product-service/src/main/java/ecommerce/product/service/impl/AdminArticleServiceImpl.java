package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
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
import java.util.Objects;

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
    @OperationLog(module = "promotion", action = "ARTICLE_ADD")
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
        // 新增没有「改前值」，只记关键识别信息，方便在日志里定位是哪篇文章
        LogDetail.add("新增文章：标题【" + article.getTitle() + "】");
        return article.getId();
    }

    @Override
    @Transactional
    @OperationLog(module = "promotion", action = "ARTICLE_EDIT", bizId = "#id")
    public void update(Long id, AdminArticleDTO dto) {
        Article old = requireArticle(id);
        validate(dto, id);
        // 变更内容必须在覆盖之前算，否则「改前值」就被冲掉了
        recordDiff(old, dto);
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
    @OperationLog(module = "promotion", action = "ARTICLE_STATUS", bizId = "#id")
    public void updateStatus(Long id, Integer status) {
        Article article = requireArticle(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
        // 上下架单独一条日志，能直接看出是上架还是下架
        LogDetail.add(LogDiff.of("状态", article.getStatus(), status,
                AdminArticleServiceImpl::contentStatusText));
        articleMapper.updateStatus(id, status, LocalDateTime.now());
    }

    @Override
    @Transactional
    @OperationLog(module = "promotion", action = "ARTICLE_REMOVE", bizId = "#id")
    public void delete(Long id) {
        Article article = requireArticle(id);
        LogDetail.add("删除文章：标题【" + article.getTitle() + "】");
        articleMapper.softDelete(id, LocalDateTime.now());
    }

    /**
     * 记录文章变更内容（写进 operation_log.detail）。
     * <p>
     * 文章是整体覆盖式更新：DTO 里没传的字段也会被写成默认值，
     * 所以这里比较的是「实际落库的值」，而不是 DTO 的原始值。
     * <p>
     * 正文是长 HTML，整段进日志会超长（detail 只有 1024），只记「改过」。
     */
    private void recordDiff(Article old, AdminArticleDTO dto) {
        LogDetail.add(LogDiff.of("标题", old.getTitle(),
                dto.getTitle() == null ? null : dto.getTitle().trim()));
        LogDetail.add(LogDiff.of("作者", old.getAuthor(),
                dto.getAuthor() == null ? "" : dto.getAuthor().trim()));
        LogDetail.add(LogDiff.of("封面图", old.getCover(),
                dto.getCover() == null ? "" : dto.getCover().trim()));
        LogDetail.add(LogDiff.of("简介", old.getIntroduction(),
                dto.getIntroduction() == null ? "" : dto.getIntroduction().trim()));
        LogDetail.add(LogDiff.of("所属分类", old.getCategoryId(),
                dto.getCategoryId() == null ? 0L : dto.getCategoryId()));
        LogDetail.add(LogDiff.of("排序", old.getSortOrder(),
                dto.getSortOrder() == null ? 0 : dto.getSortOrder()));
        LogDetail.add(LogDiff.of("状态", old.getStatus(),
                dto.getStatus() == null ? 0 : dto.getStatus(), AdminArticleServiceImpl::contentStatusText));
        LogDetail.add(LogDiff.of("是否热门", old.getRecommendHot(),
                dto.getRecommendHot() == null ? 0 : dto.getRecommendHot(),
                AdminArticleServiceImpl::yesNoText));
        LogDetail.add(LogDiff.of("是否轮播", old.getRecommendBanner(),
                dto.getRecommendBanner() == null ? 0 : dto.getRecommendBanner(),
                AdminArticleServiceImpl::yesNoText));
        LogDetail.add(LogDiff.of("关联商品", old.getSpuId(),
                dto.getSpuId() == null ? 0L : dto.getSpuId()));
        if (!Objects.equals(old.getContent(), dto.getContent())) {
            LogDetail.add("正文内容已修改");
        }
    }

    /** 展示状态翻译：1 已上架 / 0 已下架 */
    private static String contentStatusText(Object value) {
        if (value instanceof Integer status) {
            return status == 1 ? "已上架" : "已下架";
        }
        return String.valueOf(value);
    }

    /** 是否类字段翻译：1 是 / 0 否 */
    private static String yesNoText(Object value) {
        if (value instanceof Integer flag) {
            return flag == 1 ? "是" : "否";
        }
        return String.valueOf(value);
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
