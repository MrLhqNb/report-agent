package com.reportagent.controller;

import com.reportagent.dto.DbConfigDTO;
import com.reportagent.entity.DbConfig;
import com.reportagent.service.DbConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db-configs")
@RequiredArgsConstructor
public class DbConfigController {

    private final DbConfigService dbConfigService;

    @GetMapping
    public List<DbConfig> list() {
        List<DbConfig> list = dbConfigService.list();
        list.forEach(c -> c.setPassword(null));
        return list;
    }

    @GetMapping("/{id}")
    public DbConfig getById(@PathVariable Long id) {
        DbConfig config = dbConfigService.getById(id);
        if (config != null) config.setPassword(null);
        return config;
    }

    @PostMapping
    public DbConfig create(@RequestBody DbConfigDTO dto) {
        return dbConfigService.create(dto);
    }

    @PutMapping("/{id}")
    public DbConfig update(@PathVariable Long id, @RequestBody DbConfigDTO dto) {
        return dbConfigService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return dbConfigService.removeById(id);
    }

    @PostMapping("/test")
    public Map<String, Object> testConnection(@RequestBody DbConfigDTO dto) {
        boolean ok = dbConfigService.testConnection(dto);
        return Map.of("success", ok, "message", ok ? "连接成功" : "连接失败");
    }
}
