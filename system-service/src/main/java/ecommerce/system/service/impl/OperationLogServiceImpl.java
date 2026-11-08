package ecommerce.system.service.impl;

import ecommerce.system.dto.AdminOperationLogQuery;
import ecommerce.system.entity.AdminOperationLog;
import ecommerce.system.mapper.AdminOperationLogMapper;
import ecommerce.system.service.OperationLogService;
import ecommerce.system.support.LogActionDictionary;
import ecommerce.system.support.LogExportSupport;
import ecommerce.system.vo.AdminOperationLogVO;
import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.OperationLog;
import ecommerce.utils.IpMasker;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 后台操作日志查询服务实现。
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    /** 导出文件名里的时间格式 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final LogExportSupport logExportSupport;

    public OperationLogServiceImpl(AdminOperationLogMapper adminOperationLogMapper,
                                   LogExportSupport logExportSupport) {
        this.adminOperationLogMapper = adminOperationLogMapper;
        this.logExportSupport = logExportSupport;
    }

    @Override
    public PageVO<AdminOperationLogVO> page(AdminOperationLogQuery query) {
        AdminOperationLogQuery q = query.normalize();
        List<AdminOperationLogVO> list = adminOperationLogMapper.list(q).stream()
                .map(this::toVO).toList();
        long total = adminOperationLogMapper.count(q);
        return new PageVO<>(list, total, q.getPage(), q.getPageSize());
    }

    @Override
    @OperationLog(module = "system", action = "LOG_EXPORT")
    public void export(AdminOperationLogQuery query, String format, HttpServletResponse response) {
        AdminOperationLogQuery q = query.normalize();
        long total = adminOperationLogMapper.count(q);
        // 超过上限直接 400：让用户缩小时间范围，而不是把几十万行拉进内存
        if (total > LogExportSupport.MAX_EXPORT_ROWS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "导出数据量超过 " + LogExportSupport.MAX_EXPORT_ROWS + " 条，请缩小时间范围后重试");
        }
        List<String> headers = List.of("ID", "操作人ID", "操作人", "身份类型", "模块", "动作",
                "业务对象ID", "变更详情", "请求方法", "请求路径", "IP", "请求参数",
                "结果", "错误信息", "耗时(ms)", "发生时间");
        // 分批拉取：每次 1000 行，写完释放，不在内存里攒全量
        LogExportSupport.ChunkFetcher fetcher = (offset, limit) -> {
            q.setOffset(offset);
            q.setPageSize(limit);
            return adminOperationLogMapper.list(q).stream().map(this::toRow).toList();
        };
        String displayName = "操作日志_" + FILE_TIME.format(q.getStartTime())
                + "_" + FILE_TIME.format(q.getEndTime());
        LogDetail.add("导出操作日志 " + total + " 条，时间范围 "
                + q.getStartTime() + " ~ " + q.getEndTime());
        if ("xlsx".equalsIgnoreCase(format)) {
            logExportSupport.writeExcel(response, displayName, "admin_operation_log",
                    "操作日志", headers, total, fetcher);
        } else {
            logExportSupport.writeCsv(response, displayName, "admin_operation_log",
                    headers, total, fetcher);
        }
    }

    // ==================== 内部方法 ====================

    private AdminOperationLogVO toVO(AdminOperationLog entity) {
        return AdminOperationLogVO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userType(entity.getUserType())
                .username(entity.getUsername())
                .module(entity.getModule())
                .action(entity.getAction())
                // 英文动作码统一由字典翻译，避免前端直接渲染 USER_ADD 这类值
                .actionName(LogActionDictionary.label(entity.getAction()))
                .bizId(entity.getBizId())
                .detail(entity.getDetail())
                .method(entity.getMethod())
                .uri(entity.getUri())
                // 展示脱敏，库里仍保留完整 IP 供排查
                .ip(IpMasker.mask(entity.getIp()))
                .params(entity.getParams())
                // 表里是 tinyint，转成布尔给前端，前端直接渲染成「成功/失败」
                .success(entity.getSuccess() != null && entity.getSuccess() == 1)
                .errorMsg(entity.getErrorMsg())
                .costMs(entity.getCostMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /** 导出行：全部转成字符串，公式注入防护由 LogExportSupport 统一处理 */
    private List<String> toRow(AdminOperationLog entity) {
        return List.of(
                str(entity.getId()),
                str(entity.getUserId()),
                str(entity.getUsername()),
                str(entity.getUserType()),
                str(entity.getModule()),
                str(LogActionDictionary.label(entity.getAction())),
                str(entity.getBizId()),
                str(entity.getDetail()),
                str(entity.getMethod()),
                str(entity.getUri()),
                str(IpMasker.mask(entity.getIp())),
                str(entity.getParams()),
                entity.getSuccess() != null && entity.getSuccess() == 1 ? "成功" : "失败",
                str(entity.getErrorMsg()),
                str(entity.getCostMs()),
                str(entity.getCreatedAt()));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
