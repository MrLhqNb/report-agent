package com.reportagent.controller;

import com.reportagent.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db-configs/{dbConfigId}")
@RequiredArgsConstructor
public class TableMetaController {

    private final MetadataService metadataService;

    @GetMapping("/tables")
    public List<Map<String, Object>> getTables(@PathVariable Long dbConfigId) {
        return metadataService.getTables(dbConfigId);
    }

    @GetMapping("/tables/{tableName}/columns")
    public List<Map<String, Object>> getColumns(@PathVariable Long dbConfigId,
                                                 @PathVariable String tableName) {
        return metadataService.getColumns(dbConfigId, tableName);
    }

    @GetMapping("/tables/{tableName}/foreign-keys")
    public List<Map<String, Object>> getForeignKeys(@PathVariable Long dbConfigId,
                                                     @PathVariable String tableName) {
        return metadataService.getForeignKeys(dbConfigId, tableName);
    }
}
