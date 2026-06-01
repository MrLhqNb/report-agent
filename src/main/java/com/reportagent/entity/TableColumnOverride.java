package com.reportagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_column_override")
public class TableColumnOverride {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dbConfigId;
    private String tableName;
    private String columnName;
    private String columnComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
