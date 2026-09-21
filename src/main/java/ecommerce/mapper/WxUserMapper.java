package ecommerce.mapper;

import ecommerce.pojo.WxUser;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WxUserMapper {
    /** 按 openid 查询用户，查不到返回 null */
    WxUser findByOpenid(@Param("openid") String openid);

    /** 新增用户，自增主键回填到 user.id */
    int insertWxUser(WxUser user);
}
