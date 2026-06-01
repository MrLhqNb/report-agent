package com.reportagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("db_config")
public class DbConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
