package ecommerce.member.mapper;

import ecommerce.member.entity.Member;

import org.apache.ibatis.annotations.Param;

public interface MemberMapper {
    // 根据 openid 查询用户
    Member findByOpenid(@Param("openid") String openid);

    // 新增用户
    int insertMember(Member member);

    // 根据 id 查询用户
    Member findById(@Param("id") Long id);

    // 更新头像
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);
}
