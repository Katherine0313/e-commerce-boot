package ecommerce.product.service;

import ecommerce.common.PageVO;
import ecommerce.product.dto.AdminArticleDTO;
import ecommerce.product.vo.ArticleVO;

/**
 * 后台文章管理服务。
 */
public interface AdminArticleService {

    /** 分页列表（标题 / 分类 / 状态筛选） */
    PageVO<ArticleVO> list(int page, int pageSize, String keyword, Long categoryId, Integer status);

    /** 详情（编辑回填，含正文） */
    ArticleVO get(Long id);

    /** 新建，返回文章 ID */
    Long create(AdminArticleDTO dto);

    /** 编辑 */
    void update(Long id, AdminArticleDTO dto);

    /** 启用 / 禁用 */
    void updateStatus(Long id, Integer status);

    /** 逻辑删除 */
    void delete(Long id);
}
