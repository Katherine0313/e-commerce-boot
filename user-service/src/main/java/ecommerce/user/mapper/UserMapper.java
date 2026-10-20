package ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ecommerce.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 按月统计新增用户数（近 N 月）：按 create_time 分月 count，返回 [{month: 'YYYY-MM', cnt: N}] 升序。
     * 后台工作台「用户增长」折线用，缺失的月份由 Service 补 0。
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS month, COUNT(*) AS cnt " +
            "FROM `user` WHERE shop_id = #{shopId} AND is_deleted = 0 " +
            "AND create_time >= #{startDate} " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m') ORDER BY month ASC")
    List<Map<String, Object>> countMonthlyNewUsers(@Param("shopId") Long shopId, @Param("startDate") String startDate);
}
