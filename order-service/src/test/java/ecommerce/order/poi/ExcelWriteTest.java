package ecommerce.order.poi;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 2 步：POI 写入 Excel（入门）
 * 流程：新建工作簿 -> 建 sheet -> 建行 -> 建单元格 -> 设值 -> write 落盘
 */
class ExcelWriteTest {

    private static final String OUTPUT_PATH = "target/test-output.xlsx";

    @Test
    void shouldWriteExcel() throws Exception {
        // 1. 无参构造 = 在内存里创建一个空工作簿
        // 2. 文件输出流，write 时把内存中的工作簿写到磁盘
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(OUTPUT_PATH)) {

            // 3. 创建 sheet 页（名字就是 Excel 底部的标签）
            Sheet sheet = workbook.createSheet("学员信息");

            // 4. 创建第 0 行（表头），逐列 setCellValue
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("爱好");

            // 5. 写入 3 行数据
            String[][] data = {
                    {"张三", "篮球"},
                    {"李四", "足球"},
                    {"王五", "羽毛球"}
            };
            for (int i = 0; i < data.length; i++) {
                // 行号 = 数组下标 + 1，因为第 0 行已经被表头占用了
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(data[i][0]);
                row.createCell(1).setCellValue(data[i][1]);
            }

            // 6. 把工作簿写出到文件
            workbook.write(fos);
        }

        // 验证文件确实生成了
        assertTrue(Files.exists(Path.of(OUTPUT_PATH)));
        System.out.println("已生成: " + Path.of(OUTPUT_PATH).toAbsolutePath());
    }
}
