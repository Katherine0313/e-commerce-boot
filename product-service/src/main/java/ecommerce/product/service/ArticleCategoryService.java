package ecommerce.product.service;

import ecommerce.product.dto.AdminArticleCategoryDTO;
import ecommerce.product.vo.ArticleCategoryVO;

import java.util.List;

/**
 * 文章分类服务（只在后台用，C 端不展示分类）。
 */
public interface ArticleCategoryService {

    /** 全部分类（带文章数） */
    List<ArticleCategoryVO> list();

    /** 详情 */
    ArticleCategoryVO get(Long id);

    /** 新建，返回分类 ID */
    Long create(AdminArticleCategoryDTO dto);

    /** 编辑 */
    void update(Long id, AdminArticleCategoryDTO dto);

    /** 逻辑删除（分类下还有文章时不允许删） */
    void delete(Long id);
}
