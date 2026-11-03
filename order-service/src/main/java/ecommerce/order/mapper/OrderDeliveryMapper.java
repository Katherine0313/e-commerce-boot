package ecommerce.order.mapper;

import ecommerce.order.entity.OrderDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单包裹 Mapper（order_delivery 表）。
 */
@Mapper
public interface OrderDeliveryMapper {

    /** 新增包裹（回填自增 id） */
    int insert(OrderDelivery delivery);

    /** 按 ID 查（不含软删除） */
    OrderDelivery findById(@Param("id") Long id);

    /** 按包裹编号查（不含软删除） */
    OrderDelivery findByPackageNo(@Param("packageNo") String packageNo);

    /** 某订单的全部包裹（按发货时间正序） */
    List<OrderDelivery> listByOrderId(@Param("orderId") Long orderId);

    /**
     * 后台包裹列表（分页 + 订单号/单号模糊 + 快递公司 + 发货类型筛选）。
     * <p>
     * 同时带出订单的收货信息与状态（consignee/phone/region/detail_address/remark/
     * order_status/sign_status/after_sale），供列表直接展示，不必再查订单。
     */
    List<OrderDelivery> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                  @Param("orderNo") String orderNo,
                                  @Param("trackingNumber") String trackingNumber,
                                  @Param("expressCode") String expressCode,
                                  @Param("deliveryType") Integer deliveryType);

    /** 后台总数（与 listAdmin 同筛选条件） */
    long countAdmin(@Param("orderNo") String orderNo,
                    @Param("trackingNumber") String trackingNumber,
                    @Param("expressCode") String expressCode,
                    @Param("deliveryType") Integer deliveryType);

    /** 修改包裹的快递信息（改单号/备注） */
    int update(@Param("id") Long id,
               @Param("expressCode") String expressCode,
               @Param("expressName") String expressName,
               @Param("trackingNumber") String trackingNumber,
               @Param("remark") String remark,
               @Param("now") LocalDateTime now);
}
