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
    AfterSale findById(@Param("id") Long id, @Param("shopId") Long shopId);

    // 查某用户的售后列表（JOIN orders 按 user_id 隔离，可选 status 筛选，按创建时间倒序）
    List<AfterSale> listByUser(@Param("userId") Long userId, @Param("shopId") Long shopId,
                               @Param("status") String status);

    // 按订单查最新一条售后（重复申请校验用，不含软删除）
    AfterSale findByOrderId(@Param("orderId") Long orderId, @Param("shopId") Long shopId);

    // 更新售后状态（主表只动 status + updated_at）
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId,
                     @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);
}
