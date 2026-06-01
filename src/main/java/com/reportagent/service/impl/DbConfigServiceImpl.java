package com.reportagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportagent.dto.DbConfigDTO;
import com.reportagent.entity.DbConfig;
import com.reportagent.mapper.DbConfigMapper;
import com.reportagent.service.DbConfigService;
import com.reportagent.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;

@Service
@RequiredArgsConstructor
public class DbConfigServiceImpl extends ServiceImpl<DbConfigMapper, DbConfig> implements DbConfigService {

    private final AesUtil aesUtil;

    @Override
    public DbConfig create(DbConfigDTO dto) {
        DbConfig entity = toEntity(dto);
        entity.setPassword(aesUtil.encrypt(dto.getPassword()));
        save(entity);
        entity.setPassword(null);
        return entity;
    }

    @Override
    public DbConfig update(Long id, DbConfigDTO dto) {
        DbConfig entity = toEntity(dto);
        entity.setId(id);
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPassword(aesUtil.encrypt(dto.getPassword()));
        }
        updateById(entity);
        entity.setPassword(null);
        return entity;
    }

    @Override
    public boolean testConnection(DbConfigDTO dto) {
        try {
            Class.forName(getDriverClass(dto.getDbType()));
            String url = buildJdbcUrl(dto);
            try (Connection conn = DriverManager.getConnection(url, dto.getUsername(), dto.getPassword())) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private DbConfig toEntity(DbConfigDTO dto) {
        DbConfig entity = new DbConfig();
        entity.setName(dto.getName());
        entity.setDbType(dto.getDbType());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setDatabaseName(dto.getDatabaseName());
        entity.setUsername(dto.getUsername());
        return entity;
    }

    public static String buildJdbcUrl(DbConfigDTO dto) {
        String type = dto.getDbType().toLowerCase();
        return switch (type) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8",
                    dto.getHost(), dto.getPort(), dto.getDatabaseName());
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s",
                    dto.getHost(), dto.getPort(), dto.getDatabaseName());
            case "sqlite" -> String.format("jdbc:sqlite:%s", dto.getDatabaseName());
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false",
                    dto.getHost(), dto.getPort(), dto.getDatabaseName());
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    dto.getHost(), dto.getPort(), dto.getDatabaseName());
            default -> throw new IllegalArgumentException("Unsupported db type: " + type);
        };
    }

    public static String getDriverClass(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "sqlite" -> "org.sqlite.JDBC";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalArgumentException("Unsupported db type: " + dbType);
        };
    }
}
