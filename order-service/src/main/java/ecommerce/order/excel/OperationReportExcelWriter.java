package ecommerce.order.excel;

import ecommerce.order.vo.OperationReportRowVO;
import ecommerce.order.vo.OperationReportVO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 运营数据报表 Excel 生成（POI）。
 * <p>
 * 只有一个 sheet：顶部大标题、概览两行、明细表 30 行。
 * 不依赖 Spring，方便单独测试。
 */
public final class OperationReportExcelWriter {

    /** 明细列数：日期、当日销售额、有效支付订单、支付转化率、平均客单价、新增用户数 */
    private static final int COLUMN_COUNT = 6;

    private OperationReportExcelWriter() {
    }

    public static byte[] write(OperationReportVO report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("运营数据报表");
            Styles styles = new Styles(workbook);

            int rowIndex = 0;

            // 1. 大标题
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("运营数据报表");
            titleCell.setCellStyle(styles.title);
            merge(sheet, rowIndex - 1);

            // 2. 概览板块标题
            Row overviewTitle = sheet.createRow(rowIndex++);
            Cell overviewTitleCell = overviewTitle.createCell(0);
            overviewTitleCell.setCellValue("概览数据（近 30 天汇总）");
            overviewTitleCell.setCellStyle(styles.section);
            merge(sheet, rowIndex - 1);

            // 3. 概览第一行：总销售额 / 支付转化率 / 新增用户数
            Row overview1 = sheet.createRow(rowIndex++);
            writeLabel(overview1, 0, "总销售额", styles);
            writeAmount(overview1, 1, report.getTotalSales(), styles);
            writeLabel(overview1, 2, "支付转化率", styles);
            writePercent(overview1, 3, report.getPaymentConversionRate(), styles);
            writeLabel(overview1, 4, "新增用户数", styles);
            writeInt(overview1, 5, report.getTotalNewUsers(), styles);

            // 4. 概览第二行：有效支付订单 / 平均客单价 / 退款订单数
            Row overview2 = sheet.createRow(rowIndex++);
            writeLabel(overview2, 0, "有效支付订单", styles);
            writeInt(overview2, 1, report.getTotalEffectivePaidOrders(), styles);
            writeLabel(overview2, 2, "平均客单价", styles);
            writeAmount(overview2, 3, report.getAverageOrderValue(), styles);
            writeLabel(overview2, 4, "退款订单数", styles);
            writeInt(overview2, 5, report.getTotalRefundOrders(), styles);

            // 5. 明细板块标题
            Row detailTitle = sheet.createRow(rowIndex++);
            Cell detailTitleCell = detailTitle.createCell(0);
            detailTitleCell.setCellValue("明细数据");
            detailTitleCell.setCellStyle(styles.section);
            merge(sheet, rowIndex - 1);

            // 6. 明细表头
            Row header = sheet.createRow(rowIndex++);
            String[] headers = {"日期", "当日销售额", "有效支付订单", "支付转化率", "平均客单价", "新增用户数"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.header);
            }

            // 7. 每日明细（按日期升序）
            List<OperationReportRowVO> rows = report.getRows();
            if (rows != null) {
                for (OperationReportRowVO item : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    writeText(row, 0, item.getDate() == null ? "" : item.getDate().toString(), styles);
                    writeAmount(row, 1, item.getSales(), styles);
                    writeInt(row, 2, item.getEffectivePaidOrders(), styles);
                    writePercent(row, 3, item.getPaymentConversionRate(), styles);
                    writeAmount(row, 4, item.getAverageOrderValue(), styles);
                    writeInt(row, 5, item.getNewUsers(), styles);
                }
            }

            // 8. 口径说明（放表格下方，避免运营看数时产生歧义）
            Row note = sheet.createRow(rowIndex);
            note.setHeightInPoints(34);
            Cell noteCell = note.createCell(0);
            noteCell.setCellValue("口径说明：销售额 = 实收 − 退款（退款按原订单支付日归属）；"
                    + "有效支付订单 = 当日支付且未全额退款的订单；"
                    + "支付转化率 = 当日提交订单中支付成功过的订单占比；"
                    + "平均客单价 = 销售额 ÷ 有效支付订单数；新增用户按注册日期统计。含今日实时数据。");
            noteCell.setCellStyle(styles.note);
            merge(sheet, rowIndex);

            // 列宽与冻结（冻结前 6 行：标题 + 概览 + 明细表头）
            int[] widths = {14, 18, 16, 14, 16, 14};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 6);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("运营数据报表生成失败", e);
        }
    }

    private static void merge(Sheet sheet, int rowIndex) {
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, COLUMN_COUNT - 1));
    }

    private static void writeLabel(Row row, int column, String value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.label);
    }

    private static void writeText(Row row, int column, String value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.text);
    }

    private static void writeAmount(Row row, int column, BigDecimal value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(styles.currency);
    }

    private static void writePercent(Row row, int column, double value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.percent);
    }

    private static void writeInt(Row row, int column, long value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.integer);
    }

    /** 一次性创建并复用所有单元格样式 */
    private static final class Styles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle label;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle currency;
        private final CellStyle percent;
        private final CellStyle integer;
        private final CellStyle note;

        private Styles(Workbook workbook) {
            title = base(workbook);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            section = base(workbook);
            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            section.setFont(sectionFont);
            section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            label = bordered(workbook);
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            label.setFont(labelFont);
            label.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            label.setAlignment(HorizontalAlignment.RIGHT);

            header = bordered(workbook);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            text = bordered(workbook);
            text.setAlignment(HorizontalAlignment.CENTER);

            currency = bordered(workbook);
            currency.setDataFormat(workbook.createDataFormat().getFormat("\"¥\"#,##0.00"));
            currency.setAlignment(HorizontalAlignment.RIGHT);

            percent = bordered(workbook);
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
            percent.setAlignment(HorizontalAlignment.RIGHT);

            integer = bordered(workbook);
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            integer.setAlignment(HorizontalAlignment.RIGHT);

            note = base(workbook);
            Font noteFont = workbook.createFont();
            noteFont.setFontHeightInPoints((short) 9);
            noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            note.setFont(noteFont);
            note.setAlignment(HorizontalAlignment.LEFT);
            note.setVerticalAlignment(VerticalAlignment.CENTER);
        }

        private static CellStyle base(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle bordered(Workbook workbook) {
            CellStyle style = base(workbook);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }
    }
}
