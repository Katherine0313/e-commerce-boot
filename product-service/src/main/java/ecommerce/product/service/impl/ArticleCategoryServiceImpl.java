package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminArticleCategoryDTO;
import ecommerce.product.entity.ArticleCategory;
import ecommerce.product.mapper.ArticleCategoryMapper;
import ecommerce.product.mapper.ArticleMapper;
import ecommerce.product.service.ArticleCategoryService;
import ecommerce.product.vo.ArticleCategoryVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章分类服务实现（只在后台用）。
 */
@Service
public class ArticleCategoryServiceImpl implements ArticleCategoryService {

    private final ArticleCategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public ArticleCategoryServiceImpl(ArticleCategoryMapper categoryMapper, ArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    @Override
    public List<ArticleCategoryVO> list() {
        return categoryMapper.listAll().stream().map(this::toVO).toList();
    }

    @Override
    public ArticleCategoryVO get(Long id) {
        ArticleCategory category = requireCategory(id);
        // 单条详情也要带上文章数，与列表保持一致（列表用子查询实时算）
        category.setArticleCount((int) articleMapper.countByCategoryId(id));
        return toVO(category);
    }

    @Override
    @Transactional
    public Long create(AdminArticleCategoryDTO dto) {
        validate(dto);
        ArticleCategory category = ArticleCategory.builder()
                .name(dto.getName().trim())
                .picUrl(dto.getPicUrl() == null ? "" : dto.getPicUrl().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .isDeleted(0)
                .createTime(LocalDateTime.now())
                .build();
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminArticleCategoryDTO dto) {
        requireCategory(id);
        validate(dto);
        ArticleCategory category = ArticleCategory.builder()
                .id(id)
                .name(dto.getName().trim())
                .picUrl(dto.getPicUrl() == null ? "" : dto.getPicUrl().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .updateTime(LocalDateTime.now())
                .build();
        categoryMapper.update(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        long articleCount = articleMapper.countByCategoryId(id);
        if (articleCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "该分类下还有 " + articleCount + " 篇文章，请先移走或删除这些文章");
        }
        categoryMapper.softDelete(id, LocalDateTime.now());
    }

    private ArticleCategory requireCategory(Long id) {
        ArticleCategory category = categoryMapper.findById(id);
        if (category == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文章分类不存在");
        }
        return category;
    }

    private void validate(AdminArticleCategoryDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入分类名称");
        }
        if (dto.getName().trim().length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "分类名称不能超过 64 个字符");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
    }

    private ArticleCategoryVO toVO(ArticleCategory c) {
        return ArticleCategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .picUrl(c.getPicUrl())
                .sortOrder(c.getSortOrder())
                .status(c.getStatus())
                .statusDesc(c.getStatus() != null && c.getStatus() == 1 ? "启用" : "禁用")
                .articleCount(c.getArticleCount())
                .createTime(c.getCreateTime())
                .build();
    }
}
