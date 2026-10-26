package ecommerce.order.poi;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.stream.FileImageOutputStream;
import java.io.FileOutputStream;

@SpringBootTest
public class ExcelWriteTest {
    private static final String OUTPUT_PATH = "target/output.xlsx";

    @Test
    void writeExcel() throws Exception {

        //1. 创建工作簿、文件输出流
        XSSFWorkbook workbook = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(OUTPUT_PATH);
        // 2.创建工作表，添加表头
        Sheet sheet = workbook.createSheet("学员信息");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("姓名");
        header.createCell(1).setCellValue("爱好");
        // 3.for循环遍历数组，填充每行单元格数据
        String[][] data = {{"张三", "篮球"}, {"李四", "足球"}, {"王五", "游泳"}};
        for (int i = 0; i < data.length; i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(data[i][0]);
            row.createCell(1).setCellValue(data[i][1]);
        }
        // 4.写入文件
        workbook.write(fos);
        // 5.手动关闭工作簿和输出流，释放资源
        workbook.close();
        fos.close();
    }


}
