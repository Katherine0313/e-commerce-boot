package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSale;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后单 Mapper（after_sales 表）。
 * <p>
 * 主表无 user_id，归属校验靠 LEFT JOIN orders 带出的 o.user_id，所以查询都 JOIN orders。
 */
public interface AfterSaleMapper {

    // 创建售后单（回填自增 id）
    int insert(AfterSale afterSale);

    // 查单条售后（JOIN orders，含 user_id + 收货信息，不含软删除）
    AfterSale findById(@Param("id") Long id);

    // 查某用户的售后列表（JOIN orders 按 user_id 隔离，可选状态/关键词/时间范围筛选，按创建时间倒序）
    List<AfterSale> listByUser(@Param("userId") Long userId,
                               @Param("status") String status, @Param("search") String search,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                               @Param("dateField") String dateField);

    // 后台列表：查所有售后（不限用户，可选状态/关键词/时间范围筛选）
    List<AfterSale> listByShop(@Param("status") String status, @Param("search") String search,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                               @Param("dateField") String dateField);

    // 按订单查最新一条售后（重复申请校验用，不含软删除）
    AfterSale findByOrderId(@Param("orderId") Long orderId);

    // 更新售后状态（主表只动 status + updated_at）
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    // 后台审核：更新状态 + 商家备注 + 更新时间
    int updateStatusAndRemark(@Param("id") Long id,
                              @Param("status") String status, @Param("merchantRemark") String merchantRemark,
                              @Param("updatedAt") LocalDateTime updatedAt);

    // 软删除主表（is_deleted = 1）
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);
}
