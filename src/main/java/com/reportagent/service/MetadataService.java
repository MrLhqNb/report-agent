package com.reportagent.service;

import com.reportagent.entity.DbConfig;
import com.reportagent.service.impl.DbConfigServiceImpl;
import com.reportagent.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final DbConfigService dbConfigService;
    private final AesUtil aesUtil;

    public List<Map<String, Object>> getTables(Long dbConfigId) {
        log.info("  → getTables: dbConfigId={}", dbConfigId);
        DbConfig config = dbConfigService.getById(dbConfigId);
        if (config == null) { log.warn("  → dbConfig 不存在: id={}", dbConfigId); return List.of(); }
        log.info("  → 连接数据库: {}/{}, host={}:{}", config.getDbType(), config.getDatabaseName(), config.getHost(), config.getPort());

        try (Connection conn = getConnection(config)) {
            log.info("  → 已连接, 读取表列表...");
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();
            String catalog = config.getDatabaseName();
            if ("oracle".equals(config.getDbType().toLowerCase())) catalog = config.getUsername().toUpperCase();

            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("tableName", rs.getString("TABLE_NAME"));
                    table.put("tableComment", rs.getString("REMARKS"));
                    tables.add(table);
                }
            }
            log.info("  → 读取到 {} 个表", tables.size());
            return tables;
        } catch (Exception e) {
            log.error("  → 读取表失败: {}", e.getMessage());
            throw new RuntimeException("Failed to read tables: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getColumns(Long dbConfigId, String tableName) {
        DbConfig config = dbConfigService.getById(dbConfigId);
        if (config == null) return List.of();

        try (Connection conn = getConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> columns = new ArrayList<>();
            String catalog = config.getDatabaseName();
            if ("oracle".equals(config.getDbType().toLowerCase())) catalog = config.getUsername().toUpperCase();

            // Get primary keys
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet pkRs = meta.getPrimaryKeys(catalog, null, tableName)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // Get columns
            try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("columnName", rs.getString("COLUMN_NAME"));
                    col.put("columnType", rs.getString("TYPE_NAME"));
                    col.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.put("columnComment", rs.getString("REMARKS"));
                    col.put("isPrimaryKey", primaryKeys.contains(rs.getString("COLUMN_NAME")));
                    columns.add(col);
                }
            }
            return columns;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read columns: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getForeignKeys(Long dbConfigId, String tableName) {
        DbConfig config = dbConfigService.getById(dbConfigId);
        if (config == null) return List.of();

        try (Connection conn = getConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> fks = new ArrayList<>();
            String catalog = config.getDatabaseName();
            if ("oracle".equals(config.getDbType().toLowerCase())) catalog = config.getUsername().toUpperCase();

            try (ResultSet rs = meta.getImportedKeys(catalog, null, tableName)) {
                while (rs.next()) {
                    Map<String, Object> fk = new LinkedHashMap<>();
                    fk.put("fkColumn", rs.getString("FKCOLUMN_NAME"));
                    fk.put("pkTable", rs.getString("PKTABLE_NAME"));
                    fk.put("pkColumn", rs.getString("PKCOLUMN_NAME"));
                    fks.add(fk);
                }
            }
            return fks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read foreign keys: " + e.getMessage(), e);
        }
    }

    private Connection getConnection(DbConfig config) throws Exception {
        String type = config.getDbType().toLowerCase();
        Class.forName(DbConfigServiceImpl.getDriverClass(type));

        String url = buildUrl(config);
        return DriverManager.getConnection(url, config.getUsername(),
                aesUtil.decrypt(config.getPassword()));
    }

    private String buildUrl(DbConfig config) {
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
