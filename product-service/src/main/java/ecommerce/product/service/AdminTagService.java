package ecommerce.product.service;

import ecommerce.product.dto.AdminTagDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.vo.TagVO;

import java.util.List;

/**
 * 后台标签管理 Service（/api/admin/tags）。
 */
public interface AdminTagService {

    /** 标签列表 */
    List<TagVO> list();

    /** 新增标签 */
    TagVO create(AdminTagDTO dto);

    /** 更新标签（局部更新） */
    TagVO update(Long id, AdminTagDTO dto);

    /** 删除标签 */
    void delete(Long id);

    /** 标签关联的商品列表 */
    List<Product> listTagProducts(Long tagId);

    /** 给一批商品打上标签 */
    void bindProductsToTag(Long tagId, List<Long> productIds);

    /** 解绑单个商品的标签 */
    void unbindProductTag(Long productId, Long tagId);

    /** 批量解绑标签下的商品 */
    void unbindTagProducts(Long tagId, List<Long> productIds);
}
