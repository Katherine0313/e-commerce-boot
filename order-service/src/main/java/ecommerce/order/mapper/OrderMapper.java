package ecommerce.order.mapper;

import ecommerce.order.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper（orders 表）。
 */
public interface OrderMapper {

    // 创建订单（回填自增 id）
    int insert(Order order);

    // 查单个订单（不含软删除）
    Order findById(@Param("id") Long id);

    // 查可推送未支付提醒的订单：待付款 + 已授权 + 未推送 + 创建时间早于阈值
    List<Order> findPendingUnpaidToNotify(@Param("status") Integer status,
                                          @Param("createdBefore") LocalDateTime createdBefore);

    // 标记未支付提醒已推送（带 unpaid_notify_sent=0 条件，重复调用不会二次更新）
    int markUnpaidNotifySent(@Param("id") Long id,
                             @Param("now") LocalDateTime now);

    // 查某用户订单列表（按创建时间倒序；keyword 非空时按订单号/收货人/商品名模糊搜索）
    List<Order> listByUser(@Param("userId") Long userId, @Param("keyword") String keyword);

    // C 端分页列表：status 为空查全部，keyword 非空时按订单号/商品名模糊搜索
    List<Order> listByUserPage(@Param("userId") Long userId, @Param("keyword") String keyword,
                               @Param("status") Integer status, @Param("offset") int offset,
                               @Param("pageSize") int pageSize);

    // C 端分页总数（与 listByUserPage 同筛选条件）
    long countByUser(@Param("userId") Long userId, @Param("keyword") String keyword,
                     @Param("status") Integer status);

    // C 端各状态订单数（订单 Tab 角标）
    ecommerce.order.vo.OrderCountsVO countsByUser(@Param("userId") Long userId);

    // 用户已支付订单数：为 0 视为「未下单过的新用户」，供新用户专享活动判定
    @Select("SELECT COUNT(*) FROM orders WHERE user_id = #{userId} AND pay_at IS NOT NULL AND is_deleted = 0")
    long countPaidByUser(@Param("userId") Long userId);

    /**
     * 状态流转：更新状态 + 自动记录对应时间戳。
     * status 用 OrderStatusEnum 的数字 code，按目标状态更新对应时间字段（见 XML 动态 SQL）。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status, @Param("payType") String payType,
                     @Param("courier") String courier, @Param("trackingNumber") String trackingNumber,
                     @Param("now") LocalDateTime now);

    /**
     * 条件状态流转：只有当前状态 == fromStatus 时才更新（用于发货，防并发双击）。
     * 返回影响行数，0 表示状态已被别人改过，调用方应抛 409。
     */
    int updateStatusIfCurrent(@Param("id") Long id,
                               @Param("fromStatus") Integer fromStatus,
                               @Param("status") Integer status,
                               @Param("courier") String courier,
                               @Param("trackingNumber") String trackingNumber,
                               @Param("now") LocalDateTime now);

    // 改包裹时同步订单上的快递信息（只改首包裹时调用，不动订单状态）
    int updateCourier(@Param("id") Long id, @Param("courier") String courier,
                      @Param("trackingNumber") String trackingNumber,
                      @Param("now") LocalDateTime now);

    // 软删除（is_deleted=1）
    int delete(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 查超时的待付款订单（定时关闭用）：status 且 created_at <= threshold 且未删除
    List<Order> findExpiredPendingPay(@Param("status") Integer status, @Param("threshold") LocalDateTime threshold);

    // 查超时未确认收货的已发货订单（定时自动完成用）：status=SHIPPED 且 ship_at <= threshold 且未删除
    List<Order> findExpiredShipped(@Param("status") Integer status, @Param("threshold") LocalDateTime threshold);

    // 系统自动完成（超时确认收货）：已发货 → 已完成，sign_status 记 auto 与用户主动签收(signed)区分
    int autoConfirm(@Param("id") Long id, @Param("now") LocalDateTime now);

    // ==================== 后台管理（/api/admin/orders） ====================

    // 后台订单列表：所有订单（不限用户），分页 + 可选状态/关键词/时间范围筛选
    List<Order> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                          @Param("status") Integer status, @Param("keyword") String keyword,
                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                          @Param("dateField") String dateField);

    // 后台订单总数（与 listAdmin 同筛选条件）
    long countAdmin(@Param("status") Integer status, @Param("keyword") String keyword,
                    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                    @Param("dateField") String dateField);

    // 更新订单信息（收货人/电话/地址/备注，动态 set）
    int update(Order order);

    // 营业额统计：某区间支付成功订单实收合计（pay_at 区间，含取消订单）
    BigDecimal sumPaidAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 营业额统计：某区间退款合计（退款跟着原订单 pay_at 归属，status=refunded，含取消订单退款）
    BigDecimal sumRefundAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 工作台顶部累计销售额：全时段实收合计（口径同 sumPaidAmount，不加时间条件）
    BigDecimal sumPaidAmountAll();

    // 工作台顶部累计销售额：全时段退款合计（口径同 sumRefundAmount，不加时间条件）
    BigDecimal sumRefundAmountAll();

    // 工作台顶部累计订单量：全时段、排除已取消（与前端原口径一致）
    @Select("SELECT COUNT(*) FROM orders WHERE is_deleted = 0 " +
            "AND order_status <> 6")
    long countAllCounted();

    // 营业额折线：某区间实收按天分组，返回 [{date_key, amount}]（date_key 形如 2026-10-01）
    List<Map<String, Object>> sumPaidAmountByDay(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    // 营业额折线：某区间退款按天分组（跟着原订单 pay_at）
    List<Map<String, Object>> sumRefundAmountByDay(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    // 工作台：某区间创建的订单量（按 created_at，排除已取消）
    @Select("SELECT COUNT(*) FROM orders WHERE is_deleted = 0 " +
            "AND order_status <> 6 AND created_at >= #{start} AND created_at < #{end}")
    long countCreatedBetween(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    // 工作台：某状态订单数（如待发货）
    @Select("SELECT COUNT(*) FROM orders WHERE is_deleted = 0 " +
            "AND order_status = #{status}")
    long countByStatus(@Param("status") Integer status);

    // ==================== 运营数据报表（本月导出） ====================

    // 当日有效支付订单数：当日 pay_at、未取消、累计退款 < 实付金额
    List<Map<String, Object>> countEffectivePaidByDay(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    // 当日提交订单数（created_at，含已取消）
    List<Map<String, Object>> countCreatedByDay(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    // 当日支付成功过的订单数（同批口径，pay_at 非空；后续退款不扣）
    List<Map<String, Object>> countCreatedPaidByDay(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    // 区间退款订单数（去重，退款按原订单 pay_at 归属）
    long countRefundOrders(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    // 当日新增用户数（user 表；与订单同库，直接查询）
    List<Map<String, Object>> countNewUsersByDay(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
