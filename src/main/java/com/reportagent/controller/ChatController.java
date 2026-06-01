package com.reportagent.controller;

import com.reportagent.service.ChatAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatAgentService chatAgentService;

    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        Long dbConfigId = request.get("dbConfigId") != null
                ? ((Number) request.get("dbConfigId")).longValue() : null;
        String message = (String) request.get("message");
        String sessionId = (String) request.get("sessionId");
        System.out.println("11111111111111111111111");
        if (dbConfigId == null || message == null || message.isEmpty()) {
            log.warn("参数校验失败: dbConfigId={}, message={}", dbConfigId, message);
            return Map.of("error", "dbConfigId 和 message 不能为空");
        }
        log.debug("收到对话请求: dbConfigId={}, message={}, sessionId={}", dbConfigId, message, sessionId);
        Map<String, Object> result = chatAgentService.chat(dbConfigId, message, sessionId);
        log.debug("对话完成: sessionId={}", result.get("sessionId"));
        return result;
    }

    @PostMapping("/execute")
    public Map<String, Object> executeSql(@RequestBody Map<String, Object> request) {
        Long dbConfigId = request.get("dbConfigId") != null
                ? ((Number) request.get("dbConfigId")).longValue() : null;
        String sql = (String) request.get("sql");

        if (dbConfigId == null || sql == null || sql.isEmpty()) {
            return Map.of("error", "dbConfigId 和 sql 不能为空");
        }

        try {
            var data = chatAgentService.executeSql(dbConfigId, sql);
            return Map.of("data", data, "success", true);
        } catch (Exception e) {
            return Map.of("error", e.getMessage(), "success", false);
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        chatAgentService.clearSession(sessionId);
        return Map.of("success", true);
    }
}
