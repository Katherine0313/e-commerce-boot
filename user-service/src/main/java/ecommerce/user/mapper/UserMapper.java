package ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ecommerce.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 按时间桶统计新增用户数（后台工作台「用户增长」折线用）。
     * format 传 DATE_FORMAT 格式：'%H' 小时 / '%Y-%m-%d' 日 / '%Y-%m' 月；
     * 返回 [{bucket: '...', cnt: N}] 升序，缺失桶由 Service 补 0。
     */
    @Select("SELECT DATE_FORMAT(create_time, #{format}) AS bucket, COUNT(*) AS cnt " +
            "FROM `user` WHERE is_deleted = 0 " +
            "AND create_time >= #{start} AND create_time < #{end} " +
            "GROUP BY DATE_FORMAT(create_time, #{format}) ORDER BY bucket ASC")
    List<Map<String, Object>> countNewUsersByBucket(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("format") String format);

    /** 用户总量（工作台统计卡用） */
    @Select("SELECT COUNT(*) FROM `user` WHERE is_deleted = 0")
    long countTotalUsers();

    /**
     * PC 端登录/注册查用户：按手机号取一条正常账号，并带出密码哈希。
     * phone 没有唯一索引（历史数据可能重复），固定取最早注册的一条。
     */
    @Select("SELECT id, openid, name, username, phone, avatar, password FROM `user` " +
            "WHERE phone = #{phone} AND is_deleted = 0 ORDER BY id ASC LIMIT 1")
    User findForLoginByPhone(@Param("phone") String phone);

    /**
     * PC 端按用户名查用户：带出密码哈希。
     * username 有唯一索引（uk_user_username），最多一条。
     */
    @Select("SELECT id, openid, name, username, phone, avatar, password FROM `user` " +
            "WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    User findForLoginByUsername(@Param("username") String username);

    /**
     * 用户名是否已被占用（注册查重，给友好提示而不是等唯一索引报错）。
     * 已注销（is_deleted=1）的账号不占用用户名。
     */
    @Select("SELECT COUNT(*) FROM `user` WHERE username = #{username} AND is_deleted = 0")
    int countByUsername(@Param("username") String username);
}
