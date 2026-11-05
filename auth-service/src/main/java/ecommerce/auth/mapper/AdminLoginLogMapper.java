package ecommerce.auth.mapper;

import ecommerce.auth.dto.AdminLoginLogQuery;
import ecommerce.auth.entity.AdminLoginLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台登录/登出日志 Mapper（admin_login_log 表）。
 * <p>
 * 写入侧在 LoginLogServiceImpl（同步 insert，登录量小），
 * 读取侧给后台「登录日志」页面用。
 */
public interface AdminLoginLogMapper {

    /** 插入一条登录/登出日志 */
    int insert(AdminLoginLog log);

    /** 按条件分页查询（offset/pageSize 由调用方算好塞进 query） */
    List<AdminLoginLog> list(AdminLoginLogQuery query);

    /** 按条件统计总数 */
    long count(AdminLoginLogQuery query);

    /**
     * 分批删除过期日志。
     * <p>
     * 一次最多删 limit 条，靠外层循环删干净：一次性 DELETE 几万行会长时间锁表。
     */
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
