package ecommerce.auth.support;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * 日志导出（CSV / Excel）的公共写出逻辑。
 * <p>
 * 为什么不放在 Service 里各写一份：CSV 的转义、Excel 的公式注入防护、下载头、
 * 分批拉取这几段逻辑两边完全一样，抽出来只留一份，避免改了一处忘了另一处。
 * <p>
 * 内存口径：<b>不把整份结果一次性读进 List</b>。调用方提供 {@link ChunkFetcher}，
 * 这里每次只取 1000 行、写完就释放；Excel 用 SXSSF（滑动窗口 100 行）流式写盘。
 */
@Slf4j
@Component
public class LogExportSupport {

    /** 单次导出的最大行数：超过就提示缩小时间范围，避免把大表拉进内存 */
    public static final int MAX_EXPORT_ROWS = 50_000;
    /** 分批拉取的行数 */
    public static final int CHUNK_SIZE = 1_000;
    /** Excel 流式写盘的滑动窗口行数 */
    private static final int EXCEL_ROW_WINDOW = 100;
    /** Excel 的 Content-Type */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 分批拉取行数据的回调。
     *
     * @param offset 起始行（0 基）
     * @param limit  最多取多少行
     */
    @FunctionalInterface
    public interface ChunkFetcher {
        List<List<String>> fetch(int offset, int limit);
    }

    /**
     * 写 CSV。
     * <p>
     * 带 UTF-8 BOM：Excel 直接双击打开 CSV 时，没有 BOM 会按本地编码解析成乱码。
     */
    public void writeCsv(HttpServletResponse response, String displayName, String asciiName,
                         List<String> headers, long total, ChunkFetcher fetcher) {
        response.setContentType("text/csv;charset=UTF-8");
        setDownloadHeader(response, displayName + ".csv", asciiName + ".csv");
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF'); // UTF-8 BOM
            writeCsvRow(writer, headers);
            forEachChunk(total, fetcher, chunk -> {
                for (List<String> row : chunk) {
                    writeCsvRow(writer, row);
                }
            });
            writer.flush();
        } catch (Exception e) {
            log.error("导出 CSV 失败: {}", displayName, e);
            throw new IllegalStateException("导出失败", e);
        }
    }

    /**
     * 写 Excel（SXSSF 流式，边查边写，不在内存里攒全量）。
     */
    public void writeExcel(HttpServletResponse response, String displayName, String asciiName,
                           String sheetName, List<String> headers, long total, ChunkFetcher fetcher) {
        response.setContentType(XLSX_CONTENT_TYPE);
        setDownloadHeader(response, displayName + ".xlsx", asciiName + ".xlsx");
        // SXSSF 会把写过的行落到临时文件，dispose() 负责删掉它们
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(EXCEL_ROW_WINDOW)) {
            try {
                SXSSFSheet sheet = workbook.createSheet(sheetName);
                Row header = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    header.createCell(i).setCellValue(headers.get(i));
                }
                int[] rowIndex = {1};
                forEachChunk(total, fetcher, chunk -> {
                    for (List<String> values : chunk) {
                        Row row = sheet.createRow(rowIndex[0]++);
                        for (int i = 0; i < values.size(); i++) {
                            row.createCell(i).setCellValue(guardFormula(values.get(i)));
                        }
                    }
                });
                workbook.write(response.getOutputStream());
                response.getOutputStream().flush();
            } finally {
                workbook.dispose();
            }
        } catch (Exception e) {
            log.error("导出 Excel 失败: {}", displayName, e);
            throw new IllegalStateException("导出失败", e);
        }
    }

    /**
     * 分批取数：每次取 CHUNK_SIZE 行，取完一批写一批，不攒全量。
     */
    private void forEachChunk(long total, ChunkFetcher fetcher,
                              Consumer<List<List<String>>> consumer) {
        int offset = 0;
        while (offset < total) {
            int limit = (int) Math.min(CHUNK_SIZE, total - offset);
            List<List<String>> chunk = fetcher.fetch(offset, limit);
            if (chunk == null || chunk.isEmpty()) {
                break;
            }
            consumer.accept(chunk);
            offset += chunk.size();
        }
    }

    /** 写一行 CSV：含逗号/引号/换行的值用双引号包起来，内部引号翻倍 */
    private void writeCsvRow(PrintWriter writer, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(i)));
        }
        writer.write("\r\n");
    }

    private String escapeCsv(String value) {
        String text = guardFormula(value);
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /**
     * 公式注入防护：以 = + - @ 开头的文本前面加一个单引号。
     * <p>
     * 日志里的 detail / params / uri 都可能是用户可控内容，
     * 直接丢进 Excel/CSV 会被当成公式执行（=cmd|'/c calc'!A1 之类）。
     */
    public static String guardFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    /**
     * 下载头。
     * <p>
     * 中文文件名按 RFC 5987 编码（filename*），同时给一个纯 ASCII 的 filename 兜底，
     * 老浏览器只认 filename 时也不会拿到乱码。
     */
    private void setDownloadHeader(HttpServletResponse response, String displayName, String asciiName) {
        String encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
