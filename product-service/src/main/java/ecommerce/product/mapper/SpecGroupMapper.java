package ecommerce.product.mapper;

import ecommerce.product.entity.SpecGroup;
import ecommerce.product.entity.SpecValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售规格字典（规格名 + 候选值）。
 */
@Mapper
public interface SpecGroupMapper {
    List<SpecGroup> listGroups();

    /** 按分类可选过滤：categoryIds 是包含自身 + 所有祖先的 id 列表，匹配 category_id IS NULL（全局）或 category_id IN (...) */
    List<SpecGroup> listGroupsByCategory(@Param("categoryIds") List<Long> categoryIds);

    List<SpecValue> listValues();

    SpecGroup findGroupById(@Param("id") Long id);

    int countGroupByName(@Param("categoryId") Long categoryId, @Param("name") String name);

    /** 是否有商品的 sku_specs 引用了该规格名 */
    int countProductsReferencingGroup(@Param("groupId") Long groupId);

    int insertGroup(SpecGroup group);

    int updateGroup(SpecGroup group);

    int deleteGroup(@Param("id") Long id);

    int deleteValuesByGroupId(@Param("groupId") Long groupId);

    int countValue(@Param("groupId") Long groupId, @Param("value") String value);

    int insertValue(SpecValue value);

    int deleteValue(@Param("id") Long id);
}
