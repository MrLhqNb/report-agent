package com.reportagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportagent.entity.TableColumnOverride;
import com.reportagent.entity.TableRelationship;
import com.reportagent.mapper.TableColumnOverrideMapper;
import com.reportagent.mapper.TableRelationshipMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RelationshipService {

    private final TableRelationshipMapper relationshipMapper;
    private final TableColumnOverrideMapper columnOverrideMapper;

    public RelationshipService(TableRelationshipMapper relationshipMapper,
                               TableColumnOverrideMapper columnOverrideMapper) {
        this.relationshipMapper = relationshipMapper;
        this.columnOverrideMapper = columnOverrideMapper;
    }

    // ---- Table Relationships ----

    public List<TableRelationship> listRelationships(Long dbConfigId) {
        return relationshipMapper.selectList(
                new LambdaQueryWrapper<TableRelationship>()
                        .eq(TableRelationship::getDbConfigId, dbConfigId));
    }

    public TableRelationship createRelationship(TableRelationship rel) {
        relationshipMapper.insert(rel);
        return rel;
    }

    public TableRelationship updateRelationship(Long id, TableRelationship rel) {
        rel.setId(id);
        relationshipMapper.updateById(rel);
        return rel;
    }

    public boolean deleteRelationship(Long id) {
        return relationshipMapper.deleteById(id) > 0;
    }

    // ---- Column Overrides ----

    public List<TableColumnOverride> listColumnOverrides(Long dbConfigId) {
        return columnOverrideMapper.selectList(
                new LambdaQueryWrapper<TableColumnOverride>()
                        .eq(TableColumnOverride::getDbConfigId, dbConfigId));
    }

    public TableColumnOverride createColumnOverride(TableColumnOverride override) {
        columnOverrideMapper.insert(override);
        return override;
    }

    public TableColumnOverride updateColumnOverride(Long id, TableColumnOverride override) {
        override.setId(id);
        columnOverrideMapper.updateById(override);
        return override;
    }

    public boolean deleteColumnOverride(Long id) {
        return columnOverrideMapper.deleteById(id) > 0;
    }

    // ---- Priority Merge ----
    // Manual config > DB native comment

    public Map<String, String> getEffectiveColumnComments(Long dbConfigId, String tableName,
                                                           Map<String, String> dbComments) {
        // Start with DB-native comments
        Map<String, String> effective = new java.util.LinkedHashMap<>(dbComments);

        // Apply manual overrides (higher priority)
        List<TableColumnOverride> overrides = columnOverrideMapper.selectList(
                new LambdaQueryWrapper<TableColumnOverride>()
                        .eq(TableColumnOverride::getDbConfigId, dbConfigId)
                        .eq(TableColumnOverride::getTableName, tableName));
        for (TableColumnOverride ov : overrides) {
            if (ov.getColumnComment() != null && !ov.getColumnComment().isEmpty()) {
                effective.put(ov.getColumnName(), ov.getColumnComment());
            }
        }

        return effective;
    }

    public List<TableRelationship> getEffectiveRelationships(Long dbConfigId,
                                                              List<Map<String, Object>> dbForeignKeys) {
        // Manual config takes full priority over DB foreign keys
        List<TableRelationship> manualRels = listRelationships(dbConfigId);
        if (!manualRels.isEmpty()) {
            return manualRels;
        }

        // Fallback: convert DB foreign keys to TableRelationship format
        return dbForeignKeys.stream().map(fk -> {
            TableRelationship rel = new TableRelationship();
            rel.setTableName((String) fk.getOrDefault("tableName", ""));
            rel.setRelatedTable((String) fk.get("pkTable"));
            rel.setJoinCondition(fk.get("fkColumn") + " = " + fk.get("pkColumn"));
            rel.setRelationType("MANY_TO_ONE");
            return rel;
        }).toList();
    }
}
