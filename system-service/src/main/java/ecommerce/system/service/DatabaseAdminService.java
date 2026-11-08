package ecommerce.system.service;

import ecommerce.system.vo.DbColumnVO;
import ecommerce.system.vo.DbTableVO;
import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据库管理（只读 + 备份 + 优化）。
 * <p>
 * 安全边界：不接收任何 SQL 片段；表名一律先做正则校验，再查 information_schema 确认存在，
 * 最后才允许拼进 DDL / SELECT。
 */
@Slf4j
@Service
public class DatabaseAdminService {

    /** 合法表名：字母数字下划线，1~64 位 */
    private static final Pattern TABLE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /** 日志类表默认不展示、不参与全库备份，避免备份体积被日志撑爆 */
    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "admin_login_log", "admin_operation_log", "admin_monitor_task_log", "ai_call_log");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 表清单分页（按表名搜索） */
    public PageVO<DbTableVO> listTables(String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        String like = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        StringBuilder where = new StringBuilder(
                " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'"
                        + " AND TABLE_NAME NOT IN ('admin_login_log', 'admin_operation_log',"
                        + " 'admin_monitor_task_log', 'ai_call_log')");
        if (like != null) {
            where.append(" AND TABLE_NAME LIKE CONCAT('%', ?, '%')");
        }

        String countSql = "SELECT COUNT(*) FROM information_schema.TABLES" + where;
        Long total = like == null
                ? jdbcTemplate.queryForObject(countSql, Long.class)
                : jdbcTemplate.queryForObject(countSql, Long.class, like);
        long totalCount = total == null ? 0 : total;

        String listSql = "SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, DATA_LENGTH, TABLE_ROWS, UPDATE_TIME"
                + " FROM information_schema.TABLES" + where
                + " ORDER BY TABLE_NAME LIMIT ?, ?";
        int offset = (safePage - 1) * safeSize;
        Object[] args = like == null
                ? new Object[]{offset, safeSize}
                : new Object[]{like, offset, safeSize};

        List<DbTableVO> list = jdbcTemplate.query(listSql, (rs, rowNum) -> {
            DbTableVO vo = new DbTableVO();
            String name = rs.getString("TABLE_NAME");
            vo.setName(name);
            vo.setComment(rs.getString("TABLE_COMMENT"));
            vo.setEngine(rs.getString("ENGINE"));
            long dataLength = rs.getLong("DATA_LENGTH");
            vo.setDataLength(dataLength);
            vo.setDataLengthHuman(humanSize(dataLength));
            vo.setRows(rs.getLong("TABLE_ROWS"));
            Timestamp updateTime = rs.getTimestamp("UPDATE_TIME");
            vo.setUpdateTime(updateTime == null ? null : updateTime.toLocalDateTime());
            return vo;
        }, args);

        return PageVO.<DbTableVO>builder()
                .list(list)
                .total(totalCount)
                .page(safePage)
                .pageSize(safeSize)
                .build();
    }

    /** 表结构（字段列表） */
    public List<DbColumnVO> listColumns(String table) {
        String safeTable = requireTable(table);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW FULL COLUMNS FROM `" + safeTable + "`");
        List<DbColumnVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            DbColumnVO vo = new DbColumnVO();
            vo.setName(text(row, "Field"));
            vo.setType(text(row, "Type"));
            vo.setNullable("YES".equalsIgnoreCase(text(row, "Null")));
            vo.setDefaultValue(text(row, "Default"));
            vo.setExtra(text(row, "Extra"));
            vo.setColumnKey(text(row, "Key"));
            vo.setComment(text(row, "Comment"));
            list.add(vo);
        }
        return list;
    }

    /** 批量优化表：OPTIMIZE TABLE 会重建表，调用方需提示低峰执行 */
    public List<String> optimizeTables(List<String> tables) {
        List<String> safeTables = requireTables(tables);
        List<String> messages = new ArrayList<>();
        for (String table : safeTables) {
            try {
                List<Map<String, Object>> result = jdbcTemplate.queryForList(
                        "OPTIMIZE TABLE `" + table + "`");
                String msg = result.isEmpty() ? "ok" : String.valueOf(result.get(0).get("Msg_text"));
                messages.add(table + ": " + msg);
            } catch (Exception e) {
                log.warn("优化表失败: table={}, err={}", table, e.getMessage());
                messages.add(table + ": 失败（" + e.getMessage() + "）");
            }
        }
        return messages;
    }

    /** 全部参与备份的业务表（排除日志表） */
    public List<String> listBusinessTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'"
                        + " ORDER BY TABLE_NAME", String.class);
        tables.removeIf(EXCLUDED_TABLES::contains);
        return tables;
    }

    /** 校验并返回安全的表名列表；为空时回退到全部业务表 */
    public List<String> requireTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return listBusinessTables();
        }
        List<String> result = new ArrayList<>(tables.size());
        for (String table : tables) {
            if (EXCLUDED_TABLES.contains(table)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "日志表不参与该操作: " + table);
            }
            result.add(requireTable(table));
        }
        return result;
    }

    /** 表名校验：正则 + information_schema 存在性 */
    public String requireTable(String table) {
        if (table == null || !TABLE_PATTERN.matcher(table).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "表名不合法");
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME = ?",
                Long.class, table);
        if (count == null || count == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "表不存在: " + table);
        }
        return table;
    }

    /** 字节数格式化 */
    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    /** 从 SHOW FULL COLUMNS 的 Map 里按列名取值（兼容驱动大小写差异） */
    private String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value == null ? null : String.valueOf(value);
    }
}
