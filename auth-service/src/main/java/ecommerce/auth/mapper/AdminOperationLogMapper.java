package ecommerce.auth.mapper;

import ecommerce.auth.dto.AdminOperationLogQuery;
import ecommerce.auth.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台操作日志 Mapper（operation_log 表的读侧）。
 * <p>
 * 写入方是 common 的 JdbcOperationLogRecorder（JdbcTemplate 直写、异步），
 * 本 Mapper 只负责查询：后台「操作日志」页面与导出都走这里。
 */
public interface AdminOperationLogMapper {

    /** 按条件分页查询（offset/pageSize 由调用方算好塞进 query） */
    List<AdminOperationLog> list(AdminOperationLogQuery query);

    /** 按条件统计总数 */
    long count(AdminOperationLogQuery query);

    /**
     * 分批删除过期日志。
     * <p>
     * 一次最多删 limit 条，靠外层循环删干净：一次性 DELETE 几万行会长时间锁表。
     */
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
