package ecommerce.product.mapper;

import ecommerce.product.entity.Tag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TagMapper {
    // 标签列表（product_count 读物化列，写操作维护）
    List<Tag> listTags(@Param("shopId") Long shopId);
}
