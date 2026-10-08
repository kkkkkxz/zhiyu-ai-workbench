package com.zwskchat.zwskchat.ai.hub;

import com.zwskchat.zwskchat.ai.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextToSqlEngine {

    private final AiService aiService;
    private final DataSource dataSource;

    private static final int MAX_ROWS = 500;
    private static final int TIMEOUT_SECONDS = 5;

    private static final String SQL_GEN_PROMPT = """
            你是一个 SQL 生成器。根据用户的自然语言问题和以下数据库表结构，生成对应的 MySQL SELECT 语句。
            只返回 SQL 语句本身，不要包含解释或 markdown 标记。
            严格只生成 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP/ALTER/CREATE。
            如果无法生成合理的 SQL，返回 "CANNOT_GENERATE"。
            
            可用表结构：
            %s
            
            用户问题：%s
            
            SQL:""";

    public SqlQueryResult execute(String question, String schemaInfo) {
        return execute(question, schemaInfo, null, null);
    }

    public SqlQueryResult execute(String question, String schemaInfo, Predicate<String> sqlValidator, String rejectionMessage) {
        String sql = generateSql(question, schemaInfo);
        if ("CANNOT_GENERATE".equals(sql)) {
            return SqlQueryResult.error("无法根据问题生成 SQL");
        }
        if (!isSelectOnly(sql)) {
            log.warn("[TextToSQL] rejected non-SELECT sql: {}", sql);
            return SqlQueryResult.error("仅支持查询操作");
        }
        if (sqlValidator != null && !sqlValidator.test(sql)) {
            log.warn("[TextToSQL] rejected unsafe sql: {}", sql);
            return SqlQueryResult.error(rejectionMessage == null ? "查询未通过安全校验" : rejectionMessage);
        }
        return runSql(sql);
    }

    private String generateSql(String question, String schemaInfo) {
        String prompt = String.format(SQL_GEN_PROMPT, schemaInfo, question);
        String result = aiService.processTextWithAi(prompt, null).trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("```(?:sql)?", "").trim();
        }
        log.info("[TextToSQL] question='{}' -> sql='{}'", question, result);
        return result;
    }

    private boolean isSelectOnly(String sql) {
        String upper = sql.trim().toUpperCase();
        if (!upper.startsWith("SELECT")) return false;
        String[] forbidden = {";", "--", "/*", "*/", "#", " UNION ", " INSERT ", " UPDATE ", " DELETE ",
                " DROP ", " ALTER ", " CREATE ", " TRUNCATE ", " GRANT ", " REVOKE ", " INTO OUTFILE ",
                " LOAD_FILE(", " SLEEP(", " BENCHMARK("};
        for (String kw : forbidden) {
            if (upper.contains(kw)) return false;
        }
        return true;
    }

    private SqlQueryResult runSql(String sql) {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(TIMEOUT_SECONDS);
            stmt.setMaxRows(MAX_ROWS);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }
                long elapsed = System.currentTimeMillis() - start;
                log.info("[TextToSQL] executed ok, rows={}, ms={}", rows.size(), elapsed);
                return SqlQueryResult.success(sql, columns, rows, (int) elapsed);
            }
        } catch (SQLTimeoutException e) {
            log.warn("[TextToSQL] timeout: {}", sql);
            return SqlQueryResult.error("查询超时（限制 " + TIMEOUT_SECONDS + " 秒）");
        } catch (SQLException e) {
            log.warn("[TextToSQL] sql error: {} - {}", sql, e.getMessage());
            return SqlQueryResult.error("SQL 执行出错: " + e.getMessage());
        }
    }

    public static class SqlQueryResult {
        public boolean success;
        public String sql;
        public List<String> columns;
        public List<Map<String, Object>> rows;
        public int executionMs;
        public String error;

        public static SqlQueryResult success(String sql, List<String> columns, List<Map<String, Object>> rows, int ms) {
            SqlQueryResult r = new SqlQueryResult();
            r.success = true;
            r.sql = sql;
            r.columns = columns;
            r.rows = rows;
            r.executionMs = ms;
            return r;
        }

        public static SqlQueryResult error(String error) {
            SqlQueryResult r = new SqlQueryResult();
            r.success = false;
            r.error = error;
            return r;
        }
    }
}
