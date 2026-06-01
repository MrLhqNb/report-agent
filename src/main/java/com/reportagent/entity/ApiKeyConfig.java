package com.reportagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_key_config")
public class ApiKeyConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String apiBase;
    private String apiKey;
    private String model;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
