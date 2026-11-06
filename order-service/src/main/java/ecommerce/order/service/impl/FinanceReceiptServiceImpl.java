package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.OperationLog;
import ecommerce.order.dto.FinanceReceiptQueryDTO;
import ecommerce.order.entity.FinanceReceipt;
import ecommerce.order.entity.Order;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.FinanceAccountMapper;
import ecommerce.order.mapper.FinanceReceiptMapper;
import ecommerce.order.service.FinanceReceiptService;
import ecommerce.order.vo.FinanceReceiptSummaryVO;
import ecommerce.order.vo.FinanceReceiptVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 收款单 Service 实现。
 * <p>
 * 三个关键点：
 * <ol>
 *   <li><b>一笔订单只有一张单据</b> —— 生成前先查，order_id 唯一索引兜底，重复直接吞掉。</li>
 *   <li><b>审核即终态</b> —— 条件更新 WHERE status = 0，影响行数为 0 时提示已审核。</li>
 *   <li><b>退款只读售后表</b> —— 财务表不落退款冗余，统计时实时取 after_sales。</li>
 * </ol>
 */
@Slf4j
@Service
public class FinanceReceiptServiceImpl implements FinanceReceiptService {

    /** 单次导出上限，防止一次拉爆内存 */
    private static final int EXPORT_MAX_ROWS = 10000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter RECEIPT_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 渠道缺失时的兜底值，保证「每笔支付都有单据」这条不变量不被打破 */
    private static final String UNKNOWN_CHANNEL = "Unknown";

    private final FinanceReceiptMapper financeReceiptMapper;
    private final FinanceAccountMapper financeAccountMapper;

    public FinanceReceiptServiceImpl(FinanceReceiptMapper financeReceiptMapper,
                                     FinanceAccountMapper financeAccountMapper) {
        this.financeReceiptMapper = financeReceiptMapper;
        this.financeAccountMapper = financeAccountMapper;
    }

    @Override
    public PageVO<FinanceReceiptVO> list(int page, int pageSize, FinanceReceiptQueryDTO query) {
        FinanceReceiptQueryDTO q = normalize(query);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        List<FinanceReceiptVO> list = financeReceiptMapper.listAdmin(offset, safeSize,
                q.getReceiptNo(), q.getOrderNo(), q.getUserId(), q.getChannel(), q.getStatus(),
                q.getStart(), q.getEnd());
        list.forEach(this::fillDesc);
        long total = financeReceiptMapper.countAdmin(q.getReceiptNo(), q.getOrderNo(), q.getUserId(),
                q.getChannel(), q.getStatus(), q.getStart(), q.getEnd());
        return new PageVO<>(list, total, safePage, safeSize);
    }

    @Override
    public FinanceReceiptVO detail(Long id) {
        FinanceReceiptVO vo = id == null ? null : financeReceiptMapper.findVOById(id);
        if (vo == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "收款单不存在");
        }
        fillDesc(vo);
        return vo;
    }

    @Override
    @Transactional
    @OperationLog(module = "finance", action = "RECEIPT_AUDIT", bizId = "#id")
    public void audit(Long id, String remark) {
        FinanceReceipt exist = requireReceipt(id);
        if (Integer.valueOf(1).equals(exist.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "单据已审核，请刷新");
        }
        int rows = financeReceiptMapper.audit(id, UserContext.getUserId(), UserContext.getUsername(),
                LocalDateTime.now(), blankToNull(remark));
        if (rows == 0) {
            // 并发下被别人抢先审核
            throw new BusinessException(HttpStatus.CONFLICT, "单据已审核，请刷新");
        }
    }

    @Override
    @OperationLog(module = "finance", action = "RECEIPT_REMARK", bizId = "#id")
    public void updateRemark(Long id, String remark) {
        FinanceReceipt exist = requireReceipt(id);
        if (Integer.valueOf(1).equals(exist.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已审核单据的备注不可修改");
        }
        financeReceiptMapper.updateRemark(id, remark == null ? null : remark.trim(), LocalDateTime.now());
    }

    @Override
    public FinanceReceiptSummaryVO summary(FinanceReceiptQueryDTO query) {
        FinanceReceiptQueryDTO q = normalize(query);
        BigDecimal audited = nullToZero(financeReceiptMapper.sumAuditedAmount(q.getStart(), q.getEnd()));
        BigDecimal refund = nullToZero(financeReceiptMapper.sumRefundAmount(q.getStart(), q.getEnd()));
        return FinanceReceiptSummaryVO.builder()
                .auditedAmount(audited)
                .refundAmount(refund)
                .netAmount(audited.subtract(refund))
                .pendingCount(financeReceiptMapper.countPending())
                .build();
    }

    @Override
    public void exportExcel(HttpServletResponse response, FinanceReceiptQueryDTO query) {
        FinanceReceiptQueryDTO q = normalize(query);
        long total = financeReceiptMapper.countAdmin(q.getReceiptNo(), q.getOrderNo(), q.getUserId(),
                q.getChannel(), q.getStatus(), q.getStart(), q.getEnd());
        int rows = (int) Math.min(total, EXPORT_MAX_ROWS);
        List<FinanceReceiptVO> list = rows == 0 ? List.of()
                : financeReceiptMapper.listAdmin(0, rows, q.getReceiptNo(), q.getOrderNo(), q.getUserId(),
                q.getChannel(), q.getStatus(), q.getStart(), q.getEnd());
        list.forEach(this::fillDesc);

        try (XSSFWorkbook excel = new XSSFWorkbook()) {
            Sheet sheet = excel.createSheet("收款单");
            CellStyle headerStyle = buildHeaderStyle(excel);
            String[] headers = {"收款单号", "订单号", "买家ID", "买家", "渠道", "收款账户", "收款金额",
                    "收款时间", "状态", "审核人", "审核时间", "是否退款", "退款金额", "退款时间", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (FinanceReceiptVO item : list) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, item.getReceiptNo());
                setCell(row, 1, item.getOrderNo());
                setCell(row, 2, item.getUserId());
                setCell(row, 3, item.getUserName());
                setCell(row, 4, item.getChannelDesc());
                setCell(row, 5, item.getAccountName());
                setCell(row, 6, item.getAmount());
                setCell(row, 7, item.getPaidAt() == null ? null : item.getPaidAt().format(DATE_FMT));
                setCell(row, 8, item.getStatusDesc());
                setCell(row, 9, item.getAuditName());
                setCell(row, 10, item.getAuditTime() == null ? null : item.getAuditTime().format(DATE_FMT));
                setCell(row, 11, Boolean.TRUE.equals(item.getRefunded()) ? "已退款" : "否");
                setCell(row, 12, item.getRefundAmount());
                setCell(row, 13, item.getRefundedAt() == null ? null : item.getRefundedAt().format(DATE_FMT));
                setCell(row, 14, item.getRemark());
            }

            // 中文文件名按 RFC 5987 编码，避免浏览器乱码
            String filename = "收款单列表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            excel.write(response.getOutputStream());
        } catch (IOException e) {
            log.error("收款单导出失败", e);
            throw new BusinessException("导出收款单异常");
        }
    }

    @Override
    @Transactional
    public void createForPaidOrder(Order order, String payType, LocalDateTime paidAt) {
        if (order == null || order.getId() == null || order.getOrderNo() == null) {
            return;
        }
        // 先查一次：正常重试直接跳过，唯一索引只做最终防线
        if (financeReceiptMapper.countByOrderId(order.getId()) > 0) {
            return;
        }
        String channel = resolveChannel(payType);
        Long accountId = financeAccountMapper.findDefaultIdByChannel(channel);
        if (accountId == null) {
            log.warn("渠道 {} 未配置启用的收款账户，收款单 account_id 留空，orderId={}", channel, order.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        FinanceReceipt receipt = FinanceReceipt.builder()
                .receiptNo(generateReceiptNo(now))
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .channel(channel)
                .accountId(accountId)
                .amount(order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount())
                .paidAt(paidAt == null ? now : paidAt)
                .status(0)
                .isDeleted(0)
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            financeReceiptMapper.insert(receipt);
        } catch (DuplicateKeyException e) {
            // 并发下另一笔已生成，视为成功；不能把支付回滚掉
            log.warn("收款单已存在，跳过生成 orderId={}", order.getId());
        }
    }

    // ==================== 内部工具 ====================

    private FinanceReceipt requireReceipt(Long id) {
        FinanceReceipt receipt = id == null ? null : financeReceiptMapper.findById(id);
        if (receipt == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "收款单不存在");
        }
        return receipt;
    }

    /** 渠道收敛成 PayTypeEnum 的 code；未知/缺失时用 Unknown 兜底，保证单据一定生成 */
    private String resolveChannel(String payType) {
        String value = blankToNull(payType);
        if (value == null) {
            return UNKNOWN_CHANNEL;
        }
        PayTypeEnum type = PayTypeEnum.fromCode(value);
        return type == null ? value : type.getCode();
    }

    private String generateReceiptNo(LocalDateTime now) {
        return "FIN" + now.format(RECEIPT_NO_FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 筛选条件归一化：空串当没填，状态越界报错，时间解析失败当不筛 */
    private FinanceReceiptQueryDTO normalize(FinanceReceiptQueryDTO query) {
        FinanceReceiptQueryDTO q = query == null ? new FinanceReceiptQueryDTO() : query;
        q.setReceiptNo(blankToNull(q.getReceiptNo()));
        q.setOrderNo(blankToNull(q.getOrderNo()));
        String channel = blankToNull(q.getChannel());
        if (channel != null) {
            PayTypeEnum type = PayTypeEnum.fromCode(channel);
            if (type == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "渠道只能是 WeChat / Alipay / BankCard");
            }
            channel = type.getCode();
        }
        q.setChannel(channel);
        if (q.getStatus() != null && q.getStatus() != 0 && q.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（待审核）或 1（已审核）");
        }
        q.setStart(parseDateTime(q.getStartDate(), false));
        q.setEnd(parseDateTime(q.getEndDate(), true));
        return q;
    }

    /** 解析 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd；空或格式错返回 null（不筛） */
    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return LocalDateTime.of(date, endOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIN);
            }
            String normalized = text.length() == 16 ? text + ":00" : text;
            return LocalDateTime.parse(normalized, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private void fillDesc(FinanceReceiptVO vo) {
        vo.setStatusDesc(Integer.valueOf(1).equals(vo.getStatus()) ? "已审核" : "待审核");
        PayTypeEnum type = PayTypeEnum.fromCode(vo.getChannel());
        vo.setChannelDesc(type == null ? (UNKNOWN_CHANNEL.equals(vo.getChannel()) ? "未知渠道" : vo.getChannel())
                : type.getDesc());
        vo.setRefunded(Boolean.TRUE.equals(vo.getRefunded()));
        vo.setRefundAmount(nullToZero(vo.getRefundAmount()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 表头样式：加粗 + 居中 */
    private CellStyle buildHeaderStyle(XSSFWorkbook excel) {
        Font font = excel.createFont();
        font.setBold(true);
        CellStyle style = excel.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /** 写单元格；字符串以 = + - @ 开头时补单引号，防 Excel 公式注入 */
    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(escapeFormula(String.valueOf(value)));
    }

    private String escapeFormula(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + text;
        }
        return text;
    }
}
