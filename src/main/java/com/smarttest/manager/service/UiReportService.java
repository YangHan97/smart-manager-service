package com.smarttest.manager.service;

import com.smarttest.manager.dto.UiTaskStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UiReportService {

    public String generateReport(String managerTaskId, List<String> taskIds,
                                  Map<String, UiTaskStatusResponse.UiTaskData> taskDataMap) {
        StringBuilder md = new StringBuilder();
        md.append("# UI 自动化测试报告\n\n");

        int total = taskIds.size();
        long success = taskIds.stream()
                .filter(id -> taskDataMap.containsKey(id) && "success".equals(taskDataMap.get(id).getStatus()))
                .count();
        long failed = total - success;

        md.append(String.format("**编排任务**: %s\n\n", managerTaskId));
        md.append(String.format("**总用例**: %d | **通过**: %d | **失败**: %d | **通过率**: %d%%\n\n",
                total, success, failed, total > 0 ? (int)(success * 100 / total) : 0));

        md.append("## 执行详情\n\n");
        md.append("| 用例ID | 状态 | 报告 | 日志 |\n");
        md.append("|--------|------|------|------|\n");

        for (String taskId : taskIds) {
            UiTaskStatusResponse.UiTaskData data = taskDataMap.get(taskId);
            String status = data != null ? data.getStatus() : "unknown";
            String report = data != null && data.getReportUrl() != null ?
                    String.format("[查看报告](%s)", data.getReportUrl()) : "-";
            String log = "-";
            if (data != null && data.getLog() != null) {
                String logStr = data.getLog().replace("\n", "<br>");
                log = logStr.substring(0, Math.min(200, logStr.length()));
            }
            md.append(String.format("| %s | %s | %s | %s |\n", taskId, status, report, log));
        }

        String fileName = String.format("[UI_TR][%s]%s-UI测试报告.md",
                managerTaskId, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        try {
            Files.write(Paths.get(fileName), md.toString().getBytes(StandardCharsets.UTF_8));
            return fileName;
        } catch (Exception e) {
            log.error("Failed to write report file", e);
            return null;
        }
    }
}
