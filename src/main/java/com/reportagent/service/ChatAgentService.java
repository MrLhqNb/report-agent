package com.reportagent.service;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportagent.entity.ApiKeyConfig;
import com.reportagent.entity.TableRelationship;
import com.reportagent.util.AesUtil;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatAgentService {

    private final MetadataService metadataService;
    private final RelationshipService relationshipService;
    private final ApiKeyService apiKeyService;
    private final DbConfigService dbConfigService;
    private final AesUtil aesUtil;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Simple in-memory session store (sessionId -> conversation history)
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    public ChatAgentService(MetadataService metadataService, RelationshipService relationshipService,
                            ApiKeyService apiKeyService, DbConfigService dbConfigService,
                            AesUtil aesUtil, ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.relationshipService = relationshipService;
        this.apiKeyService = apiKeyService;
        this.dbConfigService = dbConfigService;
        this.aesUtil = aesUtil;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Map<String, Object> chat(Long dbConfigId, String message, String sessionId) {
        log.info("══ /api/chat 开始: dbConfigId={}, message={}", dbConfigId, message);
        try {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("  新会话: sessionId={}", sessionId);
        }

        // 1. Build schema context
        log.info("[1/6] 读取数据库表结构...");
        String schemaContext = buildSchemaContext(dbConfigId);
        log.info("[1/6] 完成, schema 长度={} 字符", schemaContext.length());

        // 2. Get conversation history
        log.info("[2/6] 获取对话历史...");
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        log.info("[2/6] 历史消息数={}", history.size());

        // 3. Build messages for LLM
        log.info("[3/6] 构建 LLM 消息...");
        List<Map<String, Object>> messages = buildMessages(schemaContext, message, history);
        log.info("[3/6] 消息数={}", messages.size());

        // 4. Call LLM
        log.info("[4/6] 获取 API Key 配置...");
        ApiKeyConfig apiConfig = apiKeyService.getActive();
        if (apiConfig == null) {
            log.warn("[4/6] 无活跃 API Key, 返回提示");
            return Map.of("error", "请先配置 API Key", "sessionId", sessionId);
        }
        log.info("[4/6] 调用 LLM: provider={}, model={}", apiConfig.getProvider(), apiConfig.getModel());

        String llmResponse;
        try {
            llmResponse = callLlm(apiConfig, messages);
            log.info("[4/6] LLM 返回, 长度={} 字符", llmResponse.length());
        } catch (Exception e) {
            log.error("[4/6] LLM 调用失败: {}", e.getMessage());
            return Map.of("error", "LLM 调用失败: " + e.getMessage(), "sessionId", sessionId);
        }

        // 5. Extract SQL from LLM response
        log.info("[5/6] 提取 SQL...");
        String sql = extractSql(llmResponse);
        log.info("[5/6] SQL = {}", sql != null ? sql.substring(0, Math.min(sql.length(), 200)) : "null");

        // 6. Execute SQL if present
        List<Map<String, Object>> data = null;
        String execError = null;
        if (sql != null && !sql.isEmpty()) {
            log.info("[6/6] 执行 SQL...");
            try {
                data = executeSql(dbConfigId, sql);
                log.info("[6/6] 返回 {} 行数据", data.size());
            } catch (Exception e) {
                execError = e.getMessage();
                log.error("[6/6] SQL 执行失败: {}", execError);
            }
        }

        // 7. Save to history
        history.add(Map.of("role", "user", "content", message));
        history.add(Map.of("role", "assistant", "content", llmResponse));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", llmResponse);
        result.put("sql", sql);
        result.put("data", data);
        result.put("sessionId", sessionId);
        if (execError != null) result.put("sqlError", execError);
        return result;
        } catch (Exception e) {
            return Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "sessionId", sessionId != null ? sessionId : UUID.randomUUID().toString());
        }
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @SuppressWarnings("unchecked")
    private String buildSchemaContext(Long dbConfigId) {
        StringBuilder sb = new StringBuilder();

        List<Map<String, Object>> tables = metadataService.getTables(dbConfigId);
        List<TableRelationship> manualRels = relationshipService.listRelationships(dbConfigId);

        sb.append("## 数据库表结构\n\n");

        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");
            String dbTableComment = (String) table.get("tableComment");

            // Priority: manual config > DB comment
            String effectiveComment = dbTableComment;
            for (TableRelationship rel : manualRels) {
                if (tableName.equals(rel.getTableName()) && rel.getTableComment() != null && !rel.getTableComment().isEmpty()) {
                    effectiveComment = rel.getTableComment();
                    break;
                }
            }

            sb.append("### 表: ").append(tableName);
            if (effectiveComment != null && !effectiveComment.isEmpty()) {
                sb.append(" (").append(effectiveComment).append(")");
            }
            sb.append("\n");

            // Columns
            List<Map<String, Object>> columns = metadataService.getColumns(dbConfigId, tableName);
            // Get effective column comments (manual override > DB native)
            Map<String, String> dbComments = new LinkedHashMap<>();
            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("columnName");
                String colComment = (String) col.get("columnComment");
                if (colComment != null) dbComments.put(colName, colComment);
            }
            Map<String, String> effectiveColComments = relationshipService.getEffectiveColumnComments(
                    dbConfigId, tableName, dbComments);

            sb.append("  字段:\n");
            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("columnName");
                String colType = (String) col.get("columnType");
                boolean isPk = Boolean.TRUE.equals(col.get("isPrimaryKey"));
                String comment = effectiveColComments.getOrDefault(colName, "");

                sb.append("    - ").append(colName).append(" (").append(colType).append(")");
                if (isPk) sb.append(" [主键]");
                if (comment != null && !comment.isEmpty()) sb.append(" -- ").append(comment);
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Relationships
        List<Map<String, Object>> dbKeys = new ArrayList<>();
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");
            List<Map<String, Object>> fks = metadataService.getForeignKeys(dbConfigId, tableName);
            for (Map<String, Object> fk : fks) {
                dbKeys.add(Map.of("tableName", tableName, "fkColumn", fk.get("fkColumn"),
                        "pkTable", fk.get("pkTable"), "pkColumn", fk.get("pkColumn")));
            }
        }
        List<TableRelationship> effectiveRels = relationshipService.getEffectiveRelationships(dbConfigId, dbKeys);

        if (!effectiveRels.isEmpty()) {
            sb.append("## 表关联关系\n\n");
            for (TableRelationship rel : effectiveRels) {
                sb.append("- ").append(rel.getTableName()).append(" ↔ ").append(rel.getRelatedTable());
                sb.append(" (").append(rel.getRelationType()).append(")");
                if (rel.getJoinCondition() != null && !rel.getJoinCondition().isEmpty()) {
                    sb.append(" ON ").append(rel.getJoinCondition());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String callLlm(ApiKeyConfig config, List<Map<String, Object>> messages) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 4096);

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiBase() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode() + " - " + response.body());
        }

        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM returned no choices");
        }
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    private String extractSql(String llmResponse) {
        // Extract SQL from markdown code blocks ```sql ... ``` or ``` ... ```
        if (llmResponse == null) return null;

        // Try ```sql ... ```
        int start = llmResponse.indexOf("```sql");
        if (start >= 0) {
            start = llmResponse.indexOf("\n", start) + 1;
            int end = llmResponse.indexOf("```", start);
            if (end > start) return llmResponse.substring(start, end).trim();
        }

        // Try generic ``` ... ```
        start = llmResponse.indexOf("```");
        if (start >= 0) {
            start = llmResponse.indexOf("\n", start) + 1;
            int end = llmResponse.indexOf("```", start);
            if (end > start) return llmResponse.substring(start, end).trim();
        }

        return null;
    }

    public List<Map<String, Object>> executeSql(Long dbConfigId, String sql) throws Exception {
        var config = dbConfigService.getById(dbConfigId);
        if (config == null) throw new RuntimeException("数据库配置不存在");

        String driver = com.reportagent.service.impl.DbConfigServiceImpl.getDriverClass(config.getDbType());
        Class.forName(driver);

        String url = buildUrl(config);
        String password = aesUtil.decrypt(config.getPassword());

        try (Connection conn = DriverManager.getConnection(url, config.getUsername(), password);
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(60);
            boolean isResultSet = stmt.execute(sql);

            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    return rows;
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                return List.of(Map.of("affectedRows", updateCount));
            }
        }
    }

    private List<Map<String, Object>> buildMessages(String schemaContext, String userMessage,
                                                      List<Map<String, String>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt
        String systemPrompt = """
                你是一个专业的 SQL 报表助手。用户会向你提问数据相关的问题。
                请根据提供的数据库表结构生成准确的 SQL 查询语句。

                规则:
                1. 先用文字解释你要查询什么，然后生成 SQL
                2. SQL 必须放在 ```sql ``` 代码块中
                3. 只生成 SELECT 查询，不要执行 INSERT/UPDATE/DELETE/DROP 等操作
                4. 如果用户问题不明确，可以追问澄清
                5. 生成的 SQL 要考虑性能，避免全表扫描
                6. 使用数据库注释中提供的中文含义来理解表和字段

                以下是数据库结构信息:

                """ + schemaContext;

        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Add history (last 10 turns)
        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            messages.add(Map.of("role", history.get(i).get("role"),
                    "content", history.get(i).get("content")));
        }

        // Add current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private String buildUrl(com.reportagent.entity.DbConfig config) {
        String type = config.getDbType().toLowerCase();
        return switch (type) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8",
                    config.getHost(), config.getPort(), config.getDatabaseName());
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s",
                    config.getHost(), config.getPort(), config.getDatabaseName());
            case "sqlite" -> String.format("jdbc:sqlite:%s", config.getDatabaseName());
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false",
                    config.getHost(), config.getPort(), config.getDatabaseName());
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    config.getHost(), config.getPort(), config.getDatabaseName());
            default -> throw new IllegalArgumentException("Unsupported db type: " + type);
        };
    }
}
