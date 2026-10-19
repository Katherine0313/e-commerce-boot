package ecommerce.product.service;

import ecommerce.common.PageVO;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.vo.AdminProductVO;

import java.util.List;

/**
 * 后台商品管理 Service（/api/admin/products）。
 */
public interface AdminProductService {

    /** 商品列表（分页 + 关键词/分类筛选，含下架、含成本价） */
    PageVO<AdminProductVO> list(int page, int pageSize, String search, Long categoryId);

    /** 商品详情（含成本价） */
    AdminProductVO get(Long id);

    /** 新增商品 */
    AdminProductVO create(AdminProductDTO dto);

    /** 更新商品（局部更新，只更新非 null 字段） */
    AdminProductVO update(Long id, AdminProductDTO dto);

    /** 删除商品 */
    void delete(Long id);

    /** 品牌列表（去重、非空） */
    List<String> brands();
}
