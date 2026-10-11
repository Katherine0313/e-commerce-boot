package ecommerce.user.mapper;

import ecommerce.user.entity.User;

import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    // 根据 openid 查询用户
    User findByOpenid(@Param("openid") String openid);

    // 新增用户
    int insertUser(User user);

    // 根据 id 查询用户
    User findById(@Param("id") Long id);

    // 更新头像
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    // 更新昵称
    int updateName(@Param("id") Long id, @Param("name") String name);

    // 更新手机号
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    // 软删除（注销）：is_deleted 置 1，不物理删（orders.user_id 是 RESTRICT）
    int softDelete(@Param("id") Long id);

}
