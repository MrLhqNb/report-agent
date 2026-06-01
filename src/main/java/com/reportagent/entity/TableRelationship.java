package com.reportagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_relationship")
public class TableRelationship {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dbConfigId;
    private String tableName;
    private String tableComment;
    private String relatedTable;
    private String relationType;
    private String joinCondition;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
