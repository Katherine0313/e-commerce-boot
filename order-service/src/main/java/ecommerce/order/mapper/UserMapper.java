package ecommerce.order.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单服务读用户表（同库）：订阅消息需要 openid。
 * <p>
 * 项目约定同库可直接查询，不走跨服务 Feign（见 docs/planning/findings.md「同库直接 join」）。
 */
public interface UserMapper {

    // 按用户 ID 查 openid（只读，不涉及用户资料修改）
    @Select("SELECT openid FROM `user` WHERE id = #{userId}")
    String findOpenidById(@Param("userId") Long userId);
}
