package com.reportagent.controller;

import com.reportagent.entity.ScheduledTask;
import com.reportagent.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService taskService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @GetMapping
    public List<ScheduledTask> list() {
        return taskService.list();
    }

    @GetMapping("/{id}")
    public ScheduledTask getById(@PathVariable Long id) {
        return taskService.getById(id);
    }

    @PostMapping
    public ScheduledTask create(@RequestBody ScheduledTask task) {
        taskService.save(task);
        return task;
    }

    @PutMapping("/{id}")
    public ScheduledTask update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        taskService.updateById(task);
        return task;
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return taskService.removeById(id);
    }

    @PostMapping("/test-email")
    public Map<String, Object> testEmail(@RequestBody Map<String, String> req) {
        String to = req.getOrDefault("emailTo", "");

        if (mailSender == null) {
            return Map.of("success", false, "error", "mailSender 未注入，请检查 spring.mail 配置是否正确（缩进是否在 spring 下）");
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom("mrlhqa@163.com");
            mail.setTo(to);
            mail.setSubject("[报表Agent] 测试邮件");
            mail.setText("这是一封测试邮件，如果你收到此邮件，说明邮件配置正确。");
            mailSender.send(mail);
            return Map.of("success", true, "message", "测试邮件已发送到 " + to);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
