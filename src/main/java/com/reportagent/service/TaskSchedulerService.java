package com.reportagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportagent.entity.ScheduledTask;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskSchedulerService {

    private final ScheduledTaskService taskService;
    private final ChatAgentService chatAgentService;
    private final ChartService chartService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public TaskSchedulerService(ScheduledTaskService taskService, ChatAgentService chatAgentService, ChartService chartService) {
        this.taskService = taskService;
        this.chatAgentService = chatAgentService;
        this.chartService = chartService;
    }

    @PostConstruct
    public void init() {
        if (mailSender == null) {
            log.error("!!! mailSender 未注入, 请检查 spring.mail 配置 !!!");
        } else {
            log.info("✓ mailSender 已注入, 邮件功能正常");
        }
    }

    @Scheduled(fixedDelay = 30000) // check every 30 seconds
    public void checkAndRunTasks() {
        List<ScheduledTask> tasks = taskService.list(
                new LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getEnabled, true));

        for (ScheduledTask task : tasks) {
            try {
                if (shouldRun(task)) {
                    log.info("执行定时任务: id={}, name={}", task.getId(), task.getName());
                    runTask(task);
                    task.setLastRunAt(LocalDateTime.now());
                    taskService.updateById(task);
                }
            } catch (Exception e) {
                log.error("定时任务执行失败: id={}, name={}, error={}", task.getId(), task.getName(), e.getMessage());
            }
        }
    }

    private boolean shouldRun(ScheduledTask task) {
        if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) return false;
        LocalDateTime now = LocalDateTime.now();
        // Simple cron: minute hour dayOfMonth month dayOfWeek
        return cronMatches(task.getCronExpression().trim(), now);
    }

    @SuppressWarnings("unchecked")
    private boolean cronMatches(String cron, LocalDateTime now) {
        try {
            String[] parts = cron.split("\\s+");
            if (parts.length != 5) return false;

            int minute = now.getMinute();
            int hour = now.getHour();
            int dom = now.getDayOfMonth();
            int month = now.getMonthValue();
            int dow = now.getDayOfWeek().getValue() % 7; // Sunday=0

            return matchField(parts[0], minute, 0, 59)
                    && matchField(parts[1], hour, 0, 23)
                    && matchField(parts[2], dom, 1, 31)
                    && matchField(parts[3], month, 1, 12)
                    && matchField(parts[4], dow, 0, 7);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchField(String field, int value, int min, int max) {
        if (field.equals("*")) return true;
        // Exact match
        if (field.matches("\\d+")) {
            int target = Integer.parseInt(field);
            return target == value;
        }
        // */step
        if (field.startsWith("*/")) {
            int step = Integer.parseInt(field.substring(2));
            return value % step == 0;
        }
        // List: 1,2,3
        if (field.contains(",")) {
            for (String s : field.split(",")) {
                if (matchField(s.trim(), value, min, max)) return true;
            }
            return false;
        }
        // Range: 1-5
        if (field.contains("-")) {
            String[] range = field.split("-");
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);
            return value >= start && value <= end;
        }
        return false;
    }

    private void runTask(ScheduledTask task) {
        // 1. Run the chat query
        var result = chatAgentService.chat(task.getDbConfigId(), task.getQuestion(), null);
        Object data = result.get("data");
        String message = (String) result.get("message");

        // 2. Send email if configured
        if (task.getEmailTo() != null && !task.getEmailTo().isEmpty()) {
            sendEmail(task, message, data);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendEmail(ScheduledTask task, String message, Object data) {
        if (mailSender == null) {
            log.warn("邮件未配置, 跳过发送: task={}", task.getName());
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom("mrlhqa@163.com");
            helper.setTo(task.getEmailTo().split(","));
            helper.setSubject("[报表Agent] " + task.getName());

            // 生成图表图片 (必须在 setText 之前注册 inline 资源)
            List<Map<String, Object>> rows = (data instanceof List && ((List<?>) data).size() >= 2)
                    ? (List<Map<String, Object>>) data : null;

            byte[] barChartBytes = null;
            byte[] pieChartBytes = null;
            if (rows != null) {
                try {
                    barChartBytes = chartService.createBarChart(rows);
                } catch (Exception e) {
                    log.warn("柱状图生成失败: {}", e.getMessage());
                }
                try {
                    pieChartBytes = chartService.createPieChart(rows);
                } catch (Exception e) {
                    log.warn("饼图生成失败: {}", e.getMessage());
                }
            }

            // 注册内嵌图片 (必须在 setText 之前)
            if (barChartBytes != null) {
                helper.addInline("chartBar", new ByteArrayResource(barChartBytes), "image/png");
            }
            if (pieChartBytes != null) {
                helper.addInline("chartPie", new ByteArrayResource(pieChartBytes), "image/png");
            }

            // 邮件正文
            StringBuilder sb = new StringBuilder();
            sb.append("<h3>").append(task.getName()).append("</h3>");
            sb.append("<p><b>问题:</b> ").append(task.getQuestion()).append("</p>");
            sb.append("<p><b>AI 回复:</b><br/>").append(message != null ? message.replace("\n", "<br/>") : "无").append("</p>");
            sb.append("<p><b>数据行数:</b> ").append(data instanceof List ? ((List<?>) data).size() : "N/A").append("</p>");

            // 内嵌柱状图
            if (barChartBytes != null) {
                sb.append("<div style='margin:20px 0;text-align:center'>")
                        .append("<h4 style='margin-bottom:8px;color:#333'>📊 柱状图</h4>")
                        .append("<img src='cid:chartBar' style='max-width:100%;border:1px solid #eee;border-radius:6px'/>")
                        .append("</div>");
            }
            // 内嵌饼图
            if (pieChartBytes != null) {
                sb.append("<div style='margin:20px 0;text-align:center'>")
                        .append("<h4 style='margin-bottom:8px;color:#333'>🥧 饼图</h4>")
                        .append("<img src='cid:chartPie' style='max-width:100%;border:1px solid #eee;border-radius:6px'/>")
                        .append("</div>");
            }

            sb.append("<p style='color:#999'>发送时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>");
            helper.setText(sb.toString(), true);

            // Excel 附件
            if (data instanceof List && !((List<?>) data).isEmpty()) {
                byte[] excelBytes = createExcel((List<Map<String, Object>>) data);
                String filename = task.getName() + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
                helper.addAttachment(filename, new ByteArrayResource(excelBytes));
                log.info("Excel 附件生成: {} ({} 行, {} 字节)", filename, ((List<?>) data).size(), excelBytes.length);
            }

            mailSender.send(mime);
            log.info("邮件已发送: task={}, to={}, 柱状图={}, 饼图={}",
                    task.getName(), task.getEmailTo(),
                    barChartBytes != null ? barChartBytes.length + "B" : "无",
                    pieChartBytes != null ? pieChartBytes.length + "B" : "无");
        } catch (Exception e) {
            log.error("发送邮件失败: task={}, to={}, error={}", task.getName(), task.getEmailTo(), e.getMessage(), e);
        }
    }

    private byte[] createExcel(List<Map<String, Object>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("数据");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 写表头
            Row headerRow = sheet.createRow(0);
            Map<String, Object> firstRow = rows.get(0);
            int colIdx = 0;
            for (String key : firstRow.keySet()) {
                Cell cell = headerRow.createCell(colIdx);
                cell.setCellValue(key);
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(colIdx);
                colIdx++;
            }

            // 写数据
            int rowIdx = 1;
            for (Map<String, Object> row : rows) {
                Row dataRow = sheet.createRow(rowIdx);
                colIdx = 0;
                for (Object val : row.values()) {
                    Cell cell = dataRow.createCell(colIdx);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(val));
                    }
                    colIdx++;
                }
                rowIdx++;
            }

            // 自动调整列宽
            for (int i = 0; i < firstRow.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
