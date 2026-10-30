package ecommerce.order.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员只读 Mapper：优惠券按会员等级校验时读取用户当前等级。
 */
public interface MemberMapper {

    @Select("SELECT member_level_id FROM `user` WHERE id = #{userId} AND is_deleted = 0")
    Long findMemberLevelId(@Param("userId") Long userId);
}
