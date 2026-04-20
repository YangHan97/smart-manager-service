package com.smarttest.manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String generateReport(String managerTaskId, List<String> downstreamTaskIds, String storyId, String storyName) {
        if (downstreamTaskIds == null || downstreamTaskIds.isEmpty()) {
            log.warn("No downstream tasks for managerTaskId={}, skipping report generation", managerTaskId);
            return null;
        }

        List<CaseResult> caseResults = queryCaseResults(downstreamTaskIds);
        if (caseResults.isEmpty()) {
            log.warn("No case results found for managerTaskId={}, skipping report generation", managerTaskId);
            return null;
        }

        String report = buildMarkdownReport(caseResults);
        String fileName = buildReportFileName(storyId, storyName);
        Path filePath = Paths.get(fileName);
        try {
            Files.write(filePath, report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("Report generated: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("Failed to write report file for managerTaskId={}", managerTaskId, e);
            return null;
        }
    }

    private String buildReportFileName(String storyid, String storyName) {
        String yyyymmdd = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sId = storyid == null ? "" : storyid;
        String sName = storyName == null || storyName.isEmpty() ? sId : storyName;
        return String.format("[ISTC_TR][%s][%s]%s-实例化系统用例-测试报告.md", sId, yyyymmdd, sName);
    }

    private List<CaseResult> queryCaseResults(List<String> taskIds) {
        String placeholders = taskIds.stream().map(t -> "?").collect(Collectors.joining(","));

        String sql = "SELECT t.id, t.name, t.description, t.request_data, t.expected_response, t.assertions, " +
                "r.passed, r.executed_at, r.error " +
                "FROM api_test_cases t " +
                "LEFT JOIN (" +
                "  SELECT e1.case_id, e1.passed, e1.executed_at, e1.error " +
                "  FROM api_execution_results e1 " +
                "  WHERE e1.task_id IN (" + placeholders + ")" +
                "    AND e1.attempt = (" +
                "      SELECT MAX(e2.attempt) FROM api_execution_results e2 " +
                "      WHERE e2.task_id = e1.task_id AND e2.case_id = e1.case_id" +
                "    )" +
                ") r ON t.id = r.case_id " +
                "WHERE t.task_id IN (" + placeholders + ")";

        List<Object> params = new ArrayList<>(taskIds);
        params.addAll(taskIds);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapCaseResult(rs), params.toArray());
    }

    private CaseResult mapCaseResult(ResultSet rs) throws SQLException {
        CaseResult cr = new CaseResult();
        cr.caseId = rs.getString("id");
        cr.name = rs.getString("name");
        cr.description = rs.getString("description");
        cr.requestData = rs.getString("request_data");
        cr.expectedResponse = rs.getString("expected_response");
        cr.assertions = rs.getString("assertions");
        Object passedObj = rs.getObject("passed");
        cr.passed = passedObj == null ? null : (Boolean) passedObj;
        Object executedAt = rs.getObject("executed_at");
        cr.executedAt = executedAt == null ? null : rs.getTimestamp("executed_at").toLocalDateTime().format(DT_FMT);
        cr.error = rs.getString("error");
        return cr;
    }

    private String buildMarkdownReport(List<CaseResult> caseResults) {
        List<CaseResult> independentCases = caseResults.stream()
                .filter(c -> c.name == null || !c.name.startsWith("scn_"))
                .collect(Collectors.toList());

        Map<String, List<CaseResult>> scenarioGroups = caseResults.stream()
                .filter(c -> c.name != null && c.name.startsWith("scn_"))
                .collect(Collectors.groupingBy(c -> extractScenarioPrefix(c.name)));

        // independent stats
        int indTotal = independentCases.size();
        long indPassed = independentCases.stream().filter(c -> Boolean.TRUE.equals(c.passed)).count();
        long indFailed = independentCases.stream().filter(c -> Boolean.FALSE.equals(c.passed)).count();
        long indSkipped = independentCases.stream().filter(c -> c.passed == null).count();

        // scenario stats (group level)
        int scnTotal = scenarioGroups.size();
        long scnPassed = 0;
        long scnFailed = 0;
        long scnSkipped = 0;
        for (List<CaseResult> group : scenarioGroups.values()) {
            boolean hasFailed = group.stream().anyMatch(c -> Boolean.FALSE.equals(c.passed));
            boolean allPassed = group.stream().allMatch(c -> Boolean.TRUE.equals(c.passed));
            if (hasFailed) {
                scnFailed++;
            } else if (allPassed) {
                scnPassed++;
            } else {
                scnSkipped++;
            }
        }

        int total = indTotal + scnTotal;
        long totalPassed = indPassed + scnPassed;
        long totalFailed = indFailed + scnFailed;
        long totalSkipped = indSkipped + scnSkipped;
        int passRate = total > 0 ? (int) Math.round(totalPassed * 100.0 / total) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("# API综合测试分析报告\n");
        sb.append("## 总体评估\n");
        sb.append(String.format("本次综合测试覆盖单接口独立测试+多接口联动测试两类场景，总用例数%d条，整体通过率%d%%（%d条通过、%d条失败、%d条跳过）：\n",
                total, passRate, totalPassed, totalFailed, totalSkipped));
        sb.append(String.format("- **单接口测试**：共%d条用例，通过率%d%%（%d条通过、%d条失败）。\n",
                indTotal,
                indTotal > 0 ? (int) Math.round(indPassed * 100.0 / indTotal) : 0,
                indPassed, indFailed));
        sb.append(String.format("- **多接口联动测试**：共%d条用例，通过率%d%%（%d条失败、%d条跳过）。\n",
                scnTotal,
                scnTotal > 0 ? (int) Math.round(scnPassed * 100.0 / scnTotal) : 0,
                scnFailed, scnSkipped));
        sb.append("\n");
        sb.append(" # 用例执行结果\n");
        sb.append(" | 用例ID | 用例名称 | 用例描述 | 执行时间 |  执行结果 | 日志 |\n");
        sb.append(" | --- | --- | --- | --- | --- | --- |\n");

        // independent rows
        for (CaseResult c : independentCases) {
            sb.append(formatCaseRow(c));
        }
        // scenario rows (flatten all cases)
        for (List<CaseResult> group : scenarioGroups.values()) {
            for (CaseResult c : group) {
                sb.append(formatCaseRow(c));
            }
        }

        return sb.toString();
    }

    private String extractScenarioPrefix(String name) {
        if (name == null) return "";
        int idx = name.indexOf('_');
        if (idx == -1) return name;
        int idx2 = name.indexOf('_', idx + 1);
        if (idx2 == -1) return name;
        return name.substring(0, idx2);
    }

    private String formatCaseRow(CaseResult c) {
        String status;
        if (Boolean.TRUE.equals(c.passed)) {
            status = "passed";
        } else if (Boolean.FALSE.equals(c.passed)) {
            status = "failed";
        } else {
            status = "skipped";
        }
        String logContent = String.format("request_data:%s<br>expected_response:%s<br>assertions:%s",
                nullToEmpty(c.requestData),
                nullToEmpty(c.expectedResponse),
                nullToEmpty(c.assertions));
        return String.format(" |%s|%s|%s|%s|%s|%s|\n",
                nullToEmpty(c.caseId),
                nullToEmpty(c.name),
                nullToEmpty(c.description),
                nullToEmpty(c.executedAt),
                status,
                logContent);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static class CaseResult {
        String caseId;
        String name;
        String description;
        String requestData;
        String expectedResponse;
        String assertions;
        Boolean passed;
        String executedAt;
        String error;
    }
}
