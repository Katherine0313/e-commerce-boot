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
            "FROM `user` WHERE shop_id = #{shopId} AND is_deleted = 0 " +
            "AND create_time >= #{start} AND create_time < #{end} " +
            "GROUP BY DATE_FORMAT(create_time, #{format}) ORDER BY bucket ASC")
    List<Map<String, Object>> countNewUsersByBucket(@Param("shopId") Long shopId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("format") String format);

    /** 当前店铺用户总量（工作台统计卡用） */
    @Select("SELECT COUNT(*) FROM `user` WHERE shop_id = #{shopId} AND is_deleted = 0")
    long countTotalUsers(@Param("shopId") Long shopId);
}
