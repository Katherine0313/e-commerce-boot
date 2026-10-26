package ecommerce.order.excel;

import ecommerce.order.vo.OperationReportRowVO;
import ecommerce.order.vo.OperationReportVO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运营数据报表 Excel 结构测试：标题、板块、表头、合并单元格、数字格式。
 */
class OperationReportExcelWriterTest {

    @Test
    void shouldWriteExpectedLayout() throws Exception {
        OperationReportVO report = OperationReportVO.builder()
                .begin(LocalDate.now().minusDays(29))
                .end(LocalDate.now())
                .totalSales(new BigDecimal("12345.67"))
                .totalEffectivePaidOrders(88)
                .paymentConversionRate(0.4567)
                .averageOrderValue(new BigDecimal("140.29"))
                .totalNewUsers(12)
                .totalRefundOrders(3)
                .rows(List.of(OperationReportRowVO.builder()
                        .date(LocalDate.now())
                        .sales(new BigDecimal("1000.00"))
                        .effectivePaidOrders(10)
                        .paymentConversionRate(0.5)
                        .averageOrderValue(new BigDecimal("100.00"))
                        .newUsers(2)
                        .build()))
                .build();

        byte[] data = OperationReportExcelWriter.write(report);
        assertTrue(data.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheet("运营数据报表");
            assertNotNull(sheet);

            // 大标题、概览、明细板块、表头
            assertEquals("运营数据报表", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("概览数据（近 30 天汇总）", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("总销售额", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("有效支付订单", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("明细数据", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("日期", sheet.getRow(5).getCell(0).getStringCellValue());

            // 三个板块标题 + 口径说明，共 4 个合并区域
            assertEquals(4, sheet.getNumMergedRegions());

            // 数据行：金额单元格是数值型，百分比单元格是 0~1 的小数
            assertEquals(LocalDate.now().toString(), sheet.getRow(6).getCell(0).getStringCellValue());
            assertEquals(1000d, sheet.getRow(6).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(0.5d, sheet.getRow(6).getCell(3).getNumericCellValue(), 0.0001);
        }

        // 留一份样本，方便用 Excel 打开检查样式
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/operation-report-sample.xlsx"), data);
    }
}
