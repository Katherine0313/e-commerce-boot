package ecommerce.user.mapper;

import ecommerce.user.vo.AdminMemberVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会员统计 / 后台会员列表 Mapper（跨表只读 orders / after_sales，项目共享库模式）。
 */
public interface MemberMapper {

    // 已支付订单：实付合计 + 订单数（排除取消/未支付/已删除）
    @Select("SELECT COALESCE(SUM(o.pay_amount), 0) AS paid_amount, COUNT(*) AS paid_count " +
            "FROM orders o WHERE o.user_id = #{userId} AND o.pay_at IS NOT NULL " +
            "AND o.is_deleted = 0 AND o.order_status <> 6")
    Map<String, Object> paidStats(@Param("userId") Long userId);

    // 已退款金额：按售后单归属，只统计已退款状态
    @Select("SELECT COALESCE(SUM(i.refund_amount), 0) " +
            "FROM after_sale_items i " +
            "JOIN after_sales a ON a.id = i.after_sale_id AND a.is_deleted = 0 " +
            "JOIN orders o ON o.id = a.order_id " +
            "WHERE o.user_id = #{userId} AND a.status = 'refunded' AND i.is_deleted = 0")
    BigDecimal sumRefunded(@Param("userId") Long userId);

    // 回写用户会员字段
    // update_time 由 Java 传入：MySQL 服务器时区是 UTC，不能用 NOW()
    @Update("UPDATE `user` SET member_level_id = #{levelId}, growth_value = #{growthValue}, " +
            "total_paid_amount = #{paidAmount}, paid_order_count = #{paidCount}, update_time = #{now} " +
            "WHERE id = #{userId} AND is_deleted = 0")
    int updateMemberStats(@Param("userId") Long userId, @Param("levelId") Long levelId,
                          @Param("growthValue") Integer growthValue,
                          @Param("paidAmount") BigDecimal paidAmount,
                          @Param("paidCount") Integer paidCount,
                          @Param("now") LocalDateTime now);

    // 后台会员列表
    @Select("<script>" +
            "SELECT u.id, u.name, u.phone, u.avatar, u.member_level_id, l.name AS level_name, " +
            "u.growth_value, u.total_paid_amount, u.paid_order_count, u.create_time " +
            "FROM `user` u LEFT JOIN member_level l ON l.id = u.member_level_id " +
            "WHERE u.is_deleted = 0 " +
            "<if test='levelId != null'> AND u.member_level_id = #{levelId}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR u.phone LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            " ORDER BY u.id DESC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<AdminMemberVO> listMembers(@Param("levelId") Long levelId, @Param("keyword") String keyword,
                                    @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>" +
            "SELECT COUNT(*) FROM `user` u WHERE u.is_deleted = 0 " +
            "<if test='levelId != null'> AND u.member_level_id = #{levelId}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR u.phone LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            "</script>")
    long countMembers(@Param("levelId") Long levelId, @Param("keyword") String keyword);
}
