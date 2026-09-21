package ecommerce.user.mapper;

import ecommerce.user.entity.WxUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 微信用户数据访问接口
 * <p>
 * 用 @Mapper 标注即可被 MyBatis 扫描到，不需要在 Application 上配 @MapperScan ——
 * 以后新增模块直接建 Mapper 接口加这个注解就行，不用回头改启动类。
 */
@Mapper
public interface WxUserMapper {
    /** 按 openid 查询用户，查不到返回 null */
    WxUser findByOpenid(@Param("openid") String openid);

    /** 新增用户，自增主键回填到 user.id */
    int insertWxUser(WxUser user);
}
