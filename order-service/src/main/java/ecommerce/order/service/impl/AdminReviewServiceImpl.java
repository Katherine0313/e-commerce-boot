package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AdminReviewDTO;
import ecommerce.order.dto.AdminReviewQueryDTO;
import ecommerce.order.entity.ProductReview;
import ecommerce.order.mapper.ProductReviewMapper;
import ecommerce.order.service.AdminReviewService;
import ecommerce.order.support.ReviewTags;
import ecommerce.order.vo.AdminReviewVO;
import ecommerce.order.vo.ProductSnapshotVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 后台商品评价管理 Service 实现。
 * <p>
 * 三条硬规则：
 * <ol>
 *   <li><b>买家真实评价整条只读</b> —— 只能回复 / 隐藏 / 删除，不能改正文；SQL 层再带 {@code is_virtual = 1} 兜一道。</li>
 *   <li><b>商品名/图不信任前端</b> —— 从 products 表快照取，跟买家端从 order_items 取快照一个道理。</li>
 *   <li><b>时间全部 Java 传参</b> —— 不用 MySQL NOW()，数据库时区是 UTC 会差 8 小时。</li>
 * </ol>
 */
@Slf4j
@Service
public class AdminReviewServiceImpl implements AdminReviewService {

    /** 单次导出上限，防止一次拉爆内存 */
    private static final int EXPORT_MAX_ROWS = 5000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 列表页最大每页条数 */
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductReviewMapper productReviewMapper;

    public AdminReviewServiceImpl(ProductReviewMapper productReviewMapper) {
        this.productReviewMapper = productReviewMapper;
    }

    @Override
    public PageVO<AdminReviewVO> list(int page, int pageSize, AdminReviewQueryDTO query) {
        AdminReviewQueryDTO q = normalize(query);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        List<AdminReviewVO> list = productReviewMapper.listAdmin(offset, safeSize,
                q.getKeyword(), q.getOrderNo(), q.getRating(), q.getIsVirtual(), q.getVisible(),
                q.getStart(), q.getEnd());
        list.forEach(vo -> {
            fillStatusDesc(vo);
            fillTags(vo);
        });
        long total = productReviewMapper.countAdmin(q.getKeyword(), q.getOrderNo(), q.getRating(),
                q.getIsVirtual(), q.getVisible(), q.getStart(), q.getEnd());
        return new PageVO<>(list, total, safePage, safeSize);
    }

    @Override
    public AdminReviewVO detail(Long id) {
        AdminReviewVO vo = productReviewMapper.findAdminById(id);
        if (vo == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "评价不存在");
        }
        fillStatusDesc(vo);
        fillTags(vo);
        return vo;
    }

    @Override
    @Transactional
    public AdminReviewVO createVirtual(AdminReviewDTO dto) {
        validate(dto);
        // 商品必须真实存在：不存在就不能建评价（否则前台会出现空商品名）
        ProductSnapshotVO snapshot = productReviewMapper.findProductSnapshot(dto.getProductId());
        if (snapshot == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品不存在，无法创建评价");
        }
        LocalDateTime now = LocalDateTime.now();
        ProductReview review = ProductReview.builder()
                // 虚拟评价不挂真实订单和用户，用 0 占位；买家端 findByOrderId 只查 order_id >= 1 的真实订单，不会撞上
                .orderId(0L)
                .userId(0L)
                .productId(snapshot.getId())
                .productName(snapshot.getTitle())
                .productImage(snapshot.getImage())
                .rating(dto.getRating())
                .tags(ReviewTags.toJson(ReviewTags.normalize(dto.getTags())))
                .content(dto.getContent().trim())
                .images(dto.getImages())
                .status(1)
                .isDeleted(0)
                .isVirtual(1)
                .createdBy(UserContext.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        productReviewMapper.insertVirtual(review);
        return detail(review.getId());
    }

    @Override
    @Transactional
    public AdminReviewVO update(Long id, AdminReviewDTO dto) {
        validate(dto);
        AdminReviewVO exist = detail(id);
        // 第一道防线：买家真实评价整条只读
        if (!Integer.valueOf(1).equals(exist.getIsVirtual())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "买家真实评价不可编辑");
        }
        // 商品名/图重新从 products 表取，保证与商品现状一致
        ProductSnapshotVO snapshot = productReviewMapper.findProductSnapshot(dto.getProductId());
        if (snapshot == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品不存在，无法保存评价");
        }
        LocalDateTime now = LocalDateTime.now();
        ProductReview update = ProductReview.builder()
                .id(id)
                .productId(snapshot.getId())
                .productName(snapshot.getTitle())
                .productImage(snapshot.getImage())
                .rating(dto.getRating())
                .tags(ReviewTags.toJson(ReviewTags.normalize(dto.getTags())))
                .content(dto.getContent().trim())
                .images(dto.getImages())
                .updatedAt(now)
                .build();
        // 第二道防线在 SQL：updateByAdmin 的 WHERE 带 is_virtual = 1
        productReviewMapper.updateByAdmin(update);
        return detail(id);
    }

    @Override
    @Transactional
    public void reply(Long id, String reply) {
        detail(id);
        String content = reply == null ? null : reply.trim();
        if (content == null || content.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "回复内容不能为空");
        }
        if (content.length() > 1000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "回复内容不能超过 1000 字");
        }
        LocalDateTime now = LocalDateTime.now();
        // 一条评价只保留一条回复，再次回复覆盖并刷新 reply_time
        productReviewMapper.updateReply(id, content, now);
    }

    @Override
    @Transactional
    public void updateVisible(Long id, Integer visible) {
        detail(id);
        if (visible == null || (visible != 0 && visible != 1)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "显示状态只能是 1（显示）或 0（隐藏）");
        }
        productReviewMapper.updateStatus(id, visible, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        detail(id);
        // 逻辑删除，不物理删：万一误删还能从库里捞回来
        productReviewMapper.softDelete(id, LocalDateTime.now());
    }

    @Override
    public void exportExcel(HttpServletResponse response, AdminReviewQueryDTO query) {
        AdminReviewQueryDTO q = normalize(query);
        long total = productReviewMapper.countAdmin(q.getKeyword(), q.getOrderNo(), q.getRating(),
                q.getIsVirtual(), q.getVisible(), q.getStart(), q.getEnd());
        int rows = (int) Math.min(total, EXPORT_MAX_ROWS);
        List<AdminReviewVO> list = rows == 0 ? List.of()
                : productReviewMapper.listAdmin(0, rows, q.getKeyword(), q.getOrderNo(), q.getRating(),
                q.getIsVirtual(), q.getVisible(), q.getStart(), q.getEnd());

        try (XSSFWorkbook excel = new XSSFWorkbook()) {
            Sheet sheet = excel.createSheet("商品评价");
            CellStyle headerStyle = buildHeaderStyle(excel);
            String[] headers = {"评价ID", "商品ID", "商品名", "星级", "标签", "评价内容",
                    "类型", "评价人", "订单号", "显示状态", "商家回复", "评价时间", "回复时间"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (AdminReviewVO item : list) {
                // 列表 SQL 只带回 tags_json，这里补一次解析，否则取不到 tags
                fillTags(item);
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, item.getId());
                setCell(row, 1, item.getProductId());
                setCell(row, 2, item.getProductName());
                setCell(row, 3, item.getRating());
                List<String> tags = item.getTags();
                setCell(row, 4, tags == null || tags.isEmpty() ? null : String.join("、", tags));
                setCell(row, 5, item.getContent());
                setCell(row, 6, Integer.valueOf(1).equals(item.getIsVirtual()) ? "虚拟" : "真实");
                setCell(row, 7, item.getUserName());
                setCell(row, 8, item.getOrderNo());
                setCell(row, 9, Integer.valueOf(1).equals(item.getStatus()) ? "显示" : "隐藏");
                setCell(row, 10, item.getReply());
                setCell(row, 11, item.getCreatedAt() == null ? null : item.getCreatedAt().format(DATE_FMT));
                setCell(row, 12, item.getReplyTime() == null ? null : item.getReplyTime().format(DATE_FMT));
            }

            // 中文文件名按 RFC 5987 编码，避免浏览器乱码
            String filename = "商品评价列表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            excel.write(response.getOutputStream());
        } catch (IOException e) {
            log.error("商品评价导出失败", e);
            throw new BusinessException("导出评价异常");
        }
    }

    // ==================== 内部工具 ====================

    /** 筛选条件归一化：空串当没填，星级/虚拟/状态越界直接报错（免得筛选不到又不知道为什么） */
    private AdminReviewQueryDTO normalize(AdminReviewQueryDTO query) {
        AdminReviewQueryDTO q = query == null ? new AdminReviewQueryDTO() : query;
        q.setKeyword(blankToNull(q.getKeyword()));
        q.setOrderNo(blankToNull(q.getOrderNo()));
        q.setStart(parseDateTime(q.getStartDate()));
        q.setEnd(parseDateTime(q.getEndDate()));
        if (q.getRating() != null && (q.getRating() < 1 || q.getRating() > 5)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "星级只能是 1~5");
        }
        if (q.getIsVirtual() != null && q.getIsVirtual() != 0 && q.getIsVirtual() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "评价类型只能是 0（真实）或 1（虚拟）");
        }
        if (q.getVisible() != null && q.getVisible() != 0 && q.getVisible() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "显示状态只能是 1（显示）或 0（隐藏）");
        }
        return q;
    }

    /** 解析 yyyy-MM-dd HH:mm 为 LocalDateTime，空或格式错返回 null（不筛） */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 建/编辑评价的参数校验 */
    private void validate(AdminReviewDTO dto) {
        if (dto == null || dto.getProductId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择商品");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择 1~5 星评分");
        }
        if (dto.getContent() == null || dto.getContent().trim().length() < 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "评价内容至少 5 个字");
        }
    }

    private void fillStatusDesc(AdminReviewVO vo) {
        vo.setStatusDesc(Integer.valueOf(1).equals(vo.getStatus()) ? "显示" : "隐藏");
    }

    /** 把库里存的 JSON 标签数组解析成前端可直接渲染的数组 */
    private void fillTags(AdminReviewVO vo) {
        vo.setTags(ReviewTags.parse(vo.getTagsJson()));
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

    /**
     * 写单元格。字符串首字符是 = + - @ 时补一个单引号，
     * 防止 Excel 把用户填的内容当公式执行（公式注入）。
     */
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

    /** Excel 公式注入防护：= + - @ 开头的内容前置单引号 */
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
