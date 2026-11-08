package ecommerce.system.service;

import ecommerce.system.vo.DbBackupTaskVO;
import ecommerce.system.vo.DbBackupVO;
import ecommerce.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * 数据库备份服务：纯 JDBC 导出 SQL + gzip + 分卷，不依赖服务器上的 mysqldump。
 * <p>
 * 第一版不做恢复；备份任务异步执行，前端轮询进度。同一时间只允许一个任务。
 */
@Slf4j
@Service
public class DatabaseBackupService {

    /** 备份文件名：yyyyMMdd-HHmmss-<part>.sql.gz */
    private static final Pattern FILE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+\\.sql\\.gz$");

    /** 单分卷未压缩上限：20MB */
    private static final long PART_LIMIT_BYTES = 20L * 1024 * 1024;

    /** 每批写入的 INSERT 行数 */
    private static final int BATCH_LINES = 200;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final DatabaseAdminService databaseAdminService;

    /** 备份目录，生产必须挂持久卷 */
    @Value("${system.database.backup-dir:./backup/db}")
    private String backupDir;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "db-backup-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, DbBackupTaskVO> tasks = new ConcurrentHashMap<>();

    public DatabaseBackupService(JdbcTemplate jdbcTemplate,
                                 DataSource dataSource,
                                 DatabaseAdminService databaseAdminService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.databaseAdminService = databaseAdminService;
    }

    /** 发起备份；表列表为空表示全部业务表 */
    public DbBackupTaskVO start(List<String> tables, Long operatorId) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已有备份任务正在执行，请稍后再试");
        }
        List<String> safeTables;
        try {
            safeTables = databaseAdminService.requireTables(tables);
            if (safeTables.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "没有可备份的表");
            }
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        DbBackupTaskVO task = new DbBackupTaskVO();
        task.setTaskId(taskId);
        task.setStatus("RUNNING");
        task.setPercent(0);
        task.setTotalTables(safeTables.size());
        task.setDoneTables(0);
        task.setStartedAt(LocalDateTime.now());
        tasks.put(taskId, task);
        trimTasks();

        executor.submit(() -> runBackup(taskId, safeTables, operatorId));
        return task;
    }

    /** 查询任务进度 */
    public DbBackupTaskVO getTask(String taskId) {
        DbBackupTaskVO task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "备份任务不存在或已过期");
        }
        return task;
    }

    /** 备份记录（最近 100 条） */
    public List<DbBackupVO> listBackups() {
        return jdbcTemplate.query(
                "SELECT file_name, part, size_bytes, compressed, table_count, status, created_at"
                        + " FROM admin_db_backup ORDER BY created_at DESC LIMIT 100",
                (rs, rowNum) -> {
                    DbBackupVO vo = new DbBackupVO();
                    vo.setFileName(rs.getString("file_name"));
                    vo.setPart(rs.getInt("part"));
                    long size = rs.getLong("size_bytes");
                    vo.setSizeBytes(size);
                    vo.setSizeHuman(DatabaseAdminService.humanSize(size));
                    vo.setCompressed(rs.getInt("compressed") == 1);
                    vo.setTableCount(rs.getInt("table_count"));
                    vo.setStatus(rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    vo.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    return vo;
                });
    }

    /** 下载：返回文件资源，路径已做越界校验 */
    public FileSystemResource download(String fileName) {
        Path file = resolveBackupFile(fileName);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "备份文件不存在");
        }
        return new FileSystemResource(file);
    }

    /** 删除备份文件与记录 */
    public void deleteBackup(String fileName) {
        Path file = resolveBackupFile(fileName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "删除备份文件失败: " + e.getMessage());
        }
        jdbcTemplate.update("DELETE FROM admin_db_backup WHERE file_name = ?", fileName);
    }

    /** 文件名校验 + 目录越界校验 */
    private Path resolveBackupFile(String fileName) {
        if (fileName == null || !FILE_PATTERN.matcher(fileName).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "备份文件名不合法");
        }
        Path root = backupRoot();
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "备份文件名不合法");
        }
        return file;
    }

    private Path backupRoot() {
        return Paths.get(backupDir).toAbsolutePath().normalize();
    }

    /** 实际执行备份（在单线程池里跑） */
    private void runBackup(String taskId, List<String> tables, Long operatorId) {
        DbBackupTaskVO task = tasks.get(taskId);
        String baseName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        task.setFileNameBase(baseName);
        Path root = backupRoot();
        PartWriter writer = null;
        int done = 0;
        try {
            Files.createDirectories(root);
            writer = new PartWriter(baseName, 1, root);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                for (String table : tables) {
                    synchronized (task) {
                        task.setCurrentTable(table);
                    }
                    writeTable(connection, writer, table, baseName, root, operatorId, tables.size());
                    done++;
                    synchronized (task) {
                        task.setDoneTables(done);
                        task.setPercent((int) (done * 100L / tables.size()));
                    }
                }
            }
            writer.finish(baseName, root, tables.size(), operatorId);
            writer = null;

            synchronized (task) {
                task.setStatus("SUCCESS");
                task.setPercent(100);
                task.setCurrentTable(null);
                task.setFinishedAt(LocalDateTime.now());
            }
            log.info("数据库备份完成: task={}, base={}, tables={}", taskId, baseName, tables.size());
        } catch (Exception e) {
            log.error("数据库备份失败: task={}", taskId, e);
            if (writer != null) {
                try {
                    writer.finishFailed(baseName, root, tables.size(), operatorId);
                } catch (Exception ignored) {
                    // 失败记录本身失败时不再抛出，避免覆盖原始错误
                }
            }
            synchronized (task) {
                task.setStatus("FAILED");
                task.setCurrentTable(null);
                task.setError(e.getMessage());
                task.setFinishedAt(LocalDateTime.now());
            }
        } finally {
            running.set(false);
        }
    }

    /** 导出一张表：结构 + 数据 */
    private void writeTable(Connection connection, PartWriter writer, String table,
                            String baseName, Path root, Long operatorId, int totalTables) throws Exception {
        String createSql = null;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (rs.next()) {
                createSql = rs.getString(2);
            }
        }
        if (createSql == null) {
            throw new IllegalStateException("读取表结构失败: " + table);
        }
        writer.write("\nDROP TABLE IF EXISTS `" + table + "`;\n");
        writer.write(createSql + ";\n");

        try (Statement statement = connection.createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            // MySQL 流式读取，避免大表一次性进内存
            statement.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                String columns = buildColumns(meta, columnCount);
                List<String> batch = new ArrayList<>(BATCH_LINES);
                while (rs.next()) {
                    batch.add(buildInsert(table, columns, rs, columnCount));
                    if (batch.size() >= BATCH_LINES) {
                        writer.writeLines(batch);
                        batch.clear();
                    }
                    if (writer.overLimit()) {
                        writer.finish(baseName, root, totalTables, operatorId);
                        writer.rollToNextPart(baseName, root);
                    }
                }
                writer.writeLines(batch);
            }
        }
    }

    private String buildColumns(ResultSetMetaData meta, int columnCount) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append('`').append(meta.getColumnName(i)).append('`');
        }
        return sb.toString();
    }

    private String buildInsert(String table, String columns, ResultSet rs, int columnCount) throws Exception {
        StringBuilder sb = new StringBuilder(256);
        sb.append("INSERT INTO `").append(table).append("` (").append(columns).append(") VALUES (");
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                sb.append(", ");
            }
            Object value = rs.getObject(i);
            appendValue(sb, value);
        }
        sb.append(");\n");
        return sb.toString();
    }

    /** SQL 字面量转义；不使用 PreparedStatement 是因为导出文件本身就是 SQL 文本 */
    private void appendValue(StringBuilder sb, Object value) throws Exception {
        if (value == null) {
            sb.append("NULL");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            if (value instanceof Boolean bool) {
                sb.append(bool ? "1" : "0");
            } else {
                sb.append(value);
            }
            return;
        }
        if (value instanceof byte[] bytes) {
            sb.append("0x");
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return;
        }
        if (value instanceof Timestamp timestamp) {
            sb.append('\'').append(timestamp.toLocalDateTime().format(TS)).append('\'');
            return;
        }
        if (value instanceof java.sql.Date date) {
            sb.append('\'').append(date.toLocalDate()).append('\'');
            return;
        }
        if (value instanceof java.sql.Time time) {
            sb.append('\'').append(time.toLocalTime()).append('\'');
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            sb.append('\'').append(dateTime.format(TS)).append('\'');
            return;
        }
        if (value instanceof BigDecimal decimal) {
            sb.append(decimal.toPlainString());
            return;
        }
        sb.append(escapeSql(String.valueOf(value)));
    }

    private String escapeSql(String text) {
        return "'" + text
                .replace("\\", "\\\\")
                .replace("\0", "\\0")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u001a", "\\Z")
                .replace("'", "\\'")
                + "'";
    }

    /** 只保留最近 50 个任务，避免内存无限增长 */
    private void trimTasks() {
        if (tasks.size() <= 50) {
            return;
        }
        List<Map.Entry<String, DbBackupTaskVO>> entries = new ArrayList<>(tasks.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getValue().getStartedAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())));
        int remove = entries.size() - 50;
        for (int i = 0; i < remove; i++) {
            DbBackupTaskVO task = entries.get(i).getValue();
            if (!"RUNNING".equals(task.getStatus())) {
                tasks.remove(entries.get(i).getKey());
            }
        }
    }

    /** 写一条备份记录（每个分卷一条） */
    private void saveRecord(String fileName, int part, long sizeBytes,
                            int tableCount, String status, Long operatorId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO admin_db_backup (file_name, part, size_bytes, compressed, table_count, status, created_by, created_at)"
                            + " VALUES (?, ?, ?, 1, ?, ?, ?, NOW())"
                            + " ON DUPLICATE KEY UPDATE size_bytes = VALUES(size_bytes),"
                            + " table_count = VALUES(table_count), status = VALUES(status)",
                    fileName, part, sizeBytes, tableCount, status, operatorId);
        } catch (Exception e) {
            log.warn("写备份记录失败: file={}, err={}", fileName, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /** 分卷写入器 */
    private final class PartWriter implements Closeable {

        private String baseName;
        private int part;
        private Path file;
        private OutputStream output;
        private long bytes;

        private PartWriter(String baseName, int part, Path root) throws IOException {
            open(baseName, part, root);
        }

        private void open(String baseName, int part, Path root) throws IOException {
            this.baseName = baseName;
            this.part = part;
            this.file = root.resolve(baseName + "-" + part + ".sql.gz");
            this.output = new GZIPOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                    8192);
            this.bytes = 0;
            write("-- e-commerce db backup\n");
            write("-- generated at " + LocalDateTime.now().format(TS) + "\n");
            write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n");
        }

        private void write(String text) throws IOException {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            output.write(data);
            bytes += data.length;
        }

        private void writeLines(List<String> lines) throws IOException {
            if (lines.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder(lines.size() * 128);
            for (String line : lines) {
                sb.append(line);
            }
            write(sb.toString());
        }

        private boolean overLimit() {
            return bytes >= PART_LIMIT_BYTES;
        }

        private long size() {
            try {
                return Files.size(file);
            } catch (IOException e) {
                return 0;
            }
        }

        private void rollToNextPart(String baseName, Path root) throws IOException {
            open(baseName, part + 1, root);
        }

        /** 正常收尾并写记录 */
        private void finish(String baseName, Path root, int tableCount, Long operatorId) throws IOException {
            write("SET FOREIGN_KEY_CHECKS=1;\n");
            close();
            saveRecord(file.getFileName().toString(), part, size(), tableCount, "SUCCESS", operatorId);
        }

        /** 失败收尾：保留已写出的部分并标记 FAILED */
        private void finishFailed(String baseName, Path root, int tableCount, Long operatorId) {
            try {
                close();
            } catch (IOException ignored) {
                // 关闭失败也要把记录写掉
            }
            saveRecord(file.getFileName().toString(), part, size(), tableCount, "FAILED", operatorId);
        }

        @Override
        public void close() throws IOException {
            if (output != null) {
                output.close();
                output = null;
            }
        }
    }
}
