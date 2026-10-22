package ecommerce.order.mapper;

import ecommerce.order.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 取消订单自动退款 Mapper：共享数据库直写售后三表，与订单取消同一事务。
 * <p>
 * 不引入 aftersale-service 的 entity，用注解 + @Param / Map 直连售后表（仿 UserMapper 注解风格）。
 * 自动退款只处理「仅退款 + 直接已退款」这一种固定形态，字段值写死在 SQL 里。
 */
public interface AfterSaleRefundMapper {

    /** 该订单是否已存在未删除售后单（防重复自动退款） */
    @Select("SELECT COUNT(*) FROM after_sales WHERE order_id = #{orderId} AND shop_id = #{shopId} AND is_deleted = 0")
    int countAfterSale(@Param("orderId") Long orderId, @Param("shopId") Long shopId);

    /** 写售后主表（only_refund + refunded），自增 id 回填到 params 的 "id" */
    @Insert("INSERT INTO after_sales (shop_id, order_id, order_no, after_sale_no, after_sale_type, status, " +
            "goods_status, reason, remark, images, merchant_remark, is_deleted, created_at, updated_at) " +
            "VALUES (#{shopId}, #{orderId}, #{orderNo}, #{afterSaleNo}, 'only_refund', 'refunded', " +
            "'not_received', 'cancel', '', '[]', '', 0, #{now}, #{now})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAfterSale(Map<String, Object> params);

    /** 批量写售后明细（快照订单明细，refund_amount=sub_total，整单退） */
    @Insert("<script>" +
            "INSERT INTO after_sale_items (shop_id, after_sale_id, order_item_id, product_id, product_name, image, " +
            "sales_price, quantity, refund_amount, insurance_detail, insurance_fee, is_deleted, created_at, updated_at) VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{shopId}, #{afterSaleId}, #{it.id}, #{it.productId}, #{it.productName}, #{it.image}, #{it.salesPrice}, " +
            "#{it.quantity}, #{it.subTotal}, COALESCE(#{it.insuranceDetail}, '[]'), COALESCE(#{it.insuranceFee}, 0), 0, #{now}, #{now})" +
            "</foreach>" +
            "</script>")
    int insertItems(@Param("shopId") Long shopId, @Param("afterSaleId") Long afterSaleId,
                    @Param("items") List<OrderItem> items, @Param("now") LocalDateTime now);

    /** 写仅退款子表（refund_at / refunded_at = 取消时间，自动退款处理与到账同刻） */
    @Insert("INSERT INTO after_sale_only_refunds (shop_id, after_sale_id, refund_at, refunded_at, is_deleted, created_at, updated_at) " +
            "VALUES (#{shopId}, #{afterSaleId}, #{now}, #{now}, 0, #{now}, #{now})")
    int insertOnlyRefund(@Param("shopId") Long shopId, @Param("afterSaleId") Long afterSaleId, @Param("now") LocalDateTime now);
}
