package ecommerce.order.poi;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;

@SpringBootTest
public class ExcelReadTest {
    private static final String FILE_PATH = "D:/study/my-workspace/wechat-workspace/test.xlsx";

    @Test
    void shouldReadAllRows() throws Exception {
        // 1. 创建文件输入流，建立和文件的连接
        FileInputStream fis = new FileInputStream(FILE_PATH);
        // 2. 用输入流构建工作簿对象
        XSSFWorkbook workbook = new XSSFWorkbook(fis);
        // 3.通过 getSheetAt() 选定要读取的工作表
        XSSFSheet sheet = workbook.getSheetAt(0);
        // 4.通过 getLastRowNum() 读该表有多少行，确定循环结束位置
        int lastRow = sheet.getLastRowNum();
        // 5.for 循环遍历每一行，用 getCell() 获取单元格数据，输出
        for (int i = 1; i <= lastRow; i++) {
            // 5.1 根据行号，取出一整行
            XSSFRow row = sheet.getRow(i);
            // 5.2 取出列A
            XSSFCell cellName = row.getCell(0);
            String name = cellName.getStringCellValue();
            // 5.3 取出列B
            XSSFCell cellHobby = row.getCell(1);
            String hobby = cellHobby.getStringCellValue();
            // 5.4 输出
            System.out.println(name + "," + hobby);
        }
        // 6.关闭工作簿，释放资源
        workbook.close();
        // 7. 关闭文件输入流，避免内存泄露
        fis.close();

    }
}
