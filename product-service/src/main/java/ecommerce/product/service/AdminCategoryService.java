package ecommerce.product.service;

import ecommerce.product.dto.AdminCategoryDTO;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.CategoryVO;

import java.util.List;

/**
 * 后台分类管理 Service（/api/admin/categories）。
 */
public interface AdminCategoryService {

    /** 分类树（一级父分类含 children，后台管理展示用） */
    List<CategoryTreeVO> listTree();

    /** 分类扁平列表（新增二级分类选父类用） */
    List<CategoryVO> listFlat();

    /** 新增分类 */
    CategoryVO create(AdminCategoryDTO dto);

    /** 更新分类（局部更新） */
    CategoryVO update(Long id, AdminCategoryDTO dto);

    /** 删除分类 */
    void delete(Long id);

    /** 同级内上移/下移一位 */
    void move(Long id, String direction);
}
