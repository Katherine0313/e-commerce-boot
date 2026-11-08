package ecommerce.system.service;

import ecommerce.system.dto.AdminOperationLogQuery;
import ecommerce.system.vo.AdminOperationLogVO;
import ecommerce.common.PageVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 后台操作日志查询服务（operation_log 的读侧）。
 * <p>
 * 写入侧在 common 的 JdbcOperationLogRecorder，这里只负责查和导出。
 */
public interface OperationLogService {

    /** 分页查询操作日志 */
    PageVO<AdminOperationLogVO> page(AdminOperationLogQuery query);

    /** 导出操作日志（format：csv / xlsx），直接写响应流 */
    void export(AdminOperationLogQuery query, String format, HttpServletResponse response);
}
