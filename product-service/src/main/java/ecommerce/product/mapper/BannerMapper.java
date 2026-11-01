package ecommerce.product.mapper;

import ecommerce.product.entity.Banner;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Banner Mapper。
 */
public interface BannerMapper {

    // 后台列表（分页 + 标题/位置/状态筛选）
    List<Banner> listAdmin(@Param("keyword") String keyword,
                           @Param("position") Integer position,
                           @Param("status") Integer status,
                           @Param("offset") int offset,
                           @Param("pageSize") int pageSize);

    // 后台列表总条数
    long countAdmin(@Param("keyword") String keyword,
                    @Param("position") Integer position,
                    @Param("status") Integer status);

    // 按 id 查单条
    Banner findById(@Param("id") Long id);

    // C 端：查某个位置上所有启用的 Banner（按 sort_order 升序）
    List<Banner> listEnabled(@Param("position") Integer position);

    // 新增
    int insert(Banner banner);

    // 更新（动态 set，只更新非 null 字段）
    int update(Banner banner);

    // 更新状态（启用/禁用）
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateTime") LocalDateTime updateTime);

    // 逻辑删除
    int softDelete(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    // 点击次数 +1
    int increaseBrowseCount(@Param("id") Long id);
}
