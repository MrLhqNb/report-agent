package com.reportagent.controller;

import com.reportagent.entity.ApiKeyConfig;
import com.reportagent.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/api-key-config")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public List<ApiKeyConfig> list() {
        return apiKeyService.list();
    }

    @PostMapping
    public ApiKeyConfig create(@RequestBody ApiKeyConfig config) {
        return apiKeyService.create(config);
    }

    @PutMapping("/{id}")
    public ApiKeyConfig update(@PathVariable Long id, @RequestBody ApiKeyConfig config) {
        return apiKeyService.update(id, config);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return apiKeyService.delete(id);
    }

    @PostMapping("/{id}/activate")
    public Map<String, Object> activate(@PathVariable Long id) {
        boolean ok = apiKeyService.activate(id);
        return Map.of("success", ok);
    }
}
