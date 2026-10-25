package ecommerce.order.poi;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 3 步：单元格类型（进阶）
 * Excel 的单元格不止文本：还有数字、日期、布尔、公式、空值。
 * getStringCellValue() 只能读文本，其他类型要按 CellType 分支处理。
 */
class ExcelCellTypeTest {

    private static final String FILE_PATH = "target/test-cell-type.xlsx";

    @Test
    void shouldHandleDifferentCellTypes() throws Exception {
        // ===== 前半：先写一个包含多种类型的文件 =====
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(FILE_PATH)) {

            Sheet sheet = workbook.createSheet("类型示例");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("年龄");
            header.createCell(2).setCellValue("生日");
            header.createCell(3).setCellValue("会员");

            Row row = sheet.createRow(1);
            // 文本
            row.createCell(0).setCellValue("张三");
            // 数字
            row.createCell(1).setCellValue(25);

            // 日期：必须配好日期样式，否则 Excel 里只会显示一串数字
            Cell birthday = row.createCell(2);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            birthday.setCellStyle(dateStyle);
            birthday.setCellValue(LocalDate.of(2000, 1, 1));

            // 布尔
            row.createCell(3).setCellValue(true);

            workbook.write(fos);
        }

        // ===== 后半：读回来，按类型取值 =====
        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            DataFormatter formatter = new DataFormatter();

            // 1) 文本：getStringCellValue
            Cell nameCell = row.getCell(0);
            assertEquals(CellType.STRING, nameCell.getCellType());
            assertEquals("张三", nameCell.getStringCellValue());

            // 2) 数字：getNumericCellValue，返回 double
            Cell ageCell = row.getCell(1);
            assertEquals(CellType.NUMERIC, ageCell.getCellType());
            assertEquals(25d, ageCell.getNumericCellValue());

            // 3) 日期：底层也是数字，要先用 DateUtil 判断它是不是日期格式
            Cell birthdayCell = row.getCell(2);
            assertTrue(DateUtil.isCellDateFormatted(birthdayCell));
            assertEquals(LocalDate.of(2000, 1, 1),
                    birthdayCell.getLocalDateTimeCellValue().toLocalDate());

            // 4) 布尔：getBooleanCellValue
            Cell vipCell = row.getCell(3);
            assertEquals(CellType.BOOLEAN, vipCell.getCellType());
            assertTrue(vipCell.getBooleanCellValue());

            // 5) DataFormatter：不管什么类型，统一转成「Excel 里看到的样子」
            for (Cell cell : row) {
                System.out.print(formatter.formatCellValue(cell) + " | ");
            }
            System.out.println();

            // 6) 典型的错误读法：对数字单元格调 getStringCellValue 会抛异常
            assertThrows(IllegalStateException.class, ageCell::getStringCellValue);
        }
    }
}
