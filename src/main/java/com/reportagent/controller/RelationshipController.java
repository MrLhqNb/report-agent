package com.reportagent.controller;

import com.reportagent.entity.TableColumnOverride;
import com.reportagent.entity.TableRelationship;
import com.reportagent.service.RelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/db-configs/{dbConfigId}")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    // ---- Table Relationships ----

    @GetMapping("/relationships")
    public List<TableRelationship> listRelationships(@PathVariable Long dbConfigId) {
        return relationshipService.listRelationships(dbConfigId);
    }

    @PostMapping("/relationships")
    public TableRelationship create(@PathVariable Long dbConfigId, @RequestBody TableRelationship rel) {
        rel.setDbConfigId(dbConfigId);
        return relationshipService.createRelationship(rel);
    }

    @PutMapping("/relationships/{id}")
    public TableRelationship update(@PathVariable Long dbConfigId, @PathVariable Long id,
                                     @RequestBody TableRelationship rel) {
        return relationshipService.updateRelationship(id, rel);
    }

    @DeleteMapping("/relationships/{id}")
    public boolean deleteRelationship(@PathVariable Long dbConfigId, @PathVariable Long id) {
        return relationshipService.deleteRelationship(id);
    }

    // ---- Column Overrides ----

    @GetMapping("/column-overrides")
    public List<TableColumnOverride> listColumnOverrides(@PathVariable Long dbConfigId) {
        return relationshipService.listColumnOverrides(dbConfigId);
    }

    @PostMapping("/column-overrides")
    public TableColumnOverride createColumnOverride(@PathVariable Long dbConfigId,
                                                     @RequestBody TableColumnOverride override) {
        override.setDbConfigId(dbConfigId);
        return relationshipService.createColumnOverride(override);
    }

    @PutMapping("/column-overrides/{id}")
    public TableColumnOverride updateColumnOverride(@PathVariable Long dbConfigId, @PathVariable Long id,
                                                     @RequestBody TableColumnOverride override) {
        return relationshipService.updateColumnOverride(id, override);
    }

    @DeleteMapping("/column-overrides/{id}")
    public boolean deleteColumnOverride(@PathVariable Long dbConfigId, @PathVariable Long id) {
        return relationshipService.deleteColumnOverride(id);
    }
}
