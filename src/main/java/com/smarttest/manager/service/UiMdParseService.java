package com.smarttest.manager.service;

import com.smarttest.manager.dto.UiStep;
import com.smarttest.manager.dto.UiTestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UiMdParseService {

    private static final Set<String> VALID_STEP_TYPES = new HashSet<>(
            Arrays.asList("aiAct", "aiAssert", "aiUpload", "aiDownload"));

    /**
     * 解析后的结构：包含用例列表和 MD 第一行标题
     */
    @lombok.Data
    public static class ParseResult {
        private List<UiTestCase> testCases;
        private String mdTitle;  // MD 第一行 # 标题，如 "# HOLDING-OPT-02 UI类测试点"
    }

    public ParseResult parseMdDocumentFull(String mdContent, List<String> testCaseIdList) {
        ParseResult result = new ParseResult();
        result.setMdTitle(extractMdTitle(mdContent));
        result.setTestCases(parseMdDocument(mdContent, testCaseIdList));
        return result;
    }

    public List<UiTestCase> parseMdDocument(String mdContent, List<String> testCaseIdList) {
        List<UiTestCase> results = new ArrayList<>();

        // 动态检测列索引
        Map<String, Integer> colMap = detectColumns(mdContent);
        if (colMap == null || !colMap.containsKey("用例ID")) {
            log.error("Failed to detect markdown table columns, colMap={}", colMap);
            return results;
        }
        int idCol = colMap.get("用例ID");
        int nameCol = colMap.getOrDefault("用例名称", -1);
        int preScriptCol = colMap.getOrDefault("前置操作自动化脚本", -1);
        int testScriptCol = colMap.getOrDefault("测试步骤自动化脚本", -1);

        if (testCaseIdList == null || testCaseIdList.isEmpty()) {
            testCaseIdList = extractAllCaseIds(mdContent, idCol);
            log.info("testCaseIdList is empty, auto extracted {} cases from markdown", testCaseIdList.size());
        }

        for (String testCaseId : testCaseIdList) {
            String[] rowCols = parseTableRow(mdContent, testCaseId, idCol);
            if (rowCols == null) {
                log.warn("UI test case {} not found in markdown table", testCaseId);
                continue;
            }

            String preScript = preScriptCol >= 0 && preScriptCol < rowCols.length ? rowCols[preScriptCol] : "";
            String testScript = testScriptCol >= 0 && testScriptCol < rowCols.length ? rowCols[testScriptCol] : "";
            String caseName = nameCol >= 0 && nameCol < rowCols.length ? rowCols[nameCol] : testCaseId;

            List<UiStep> stepList = new ArrayList<>();
            stepList.addAll(parseScriptToSteps(preScript));
            stepList.addAll(parseScriptToSteps(testScript));

            if (stepList.isEmpty()) {
                log.warn("UI test case {} skipped: no valid steps after filtering", testCaseId);
                continue;
            }

            UiTestCase testCase = new UiTestCase();
            testCase.setTestCaseId(testCaseId);
            testCase.setCaseName(caseName);
            testCase.setStepList(stepList);
            results.add(testCase);

            log.debug("Parsed case: id={}, caseName={}, steps={}", testCaseId, caseName, stepList.size());
        }

        log.info("Parsed {} test cases from markdown, colMap={}", results.size(), colMap);
        return results;
    }

    /**
     * 从表头行动态检测各列索引
     */
    private Map<String, Integer> detectColumns(String mdContent) {
        String[] lines = mdContent.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.contains("用例ID")) {
                String[] headers = trimmed.split("\\|");
                Map<String, Integer> colMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim();
                    if (!h.isEmpty()) {
                        colMap.put(h, i);
                    }
                }
                return colMap;
            }
        }
        return null;
    }

    private List<String> extractAllCaseIds(String mdContent, int idCol) {
        List<String> caseIds = new ArrayList<>();
        String[] lines = mdContent.split("\n");
        boolean inTable = false;
        boolean headerPassed = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.contains("用例ID")) {
                inTable = true;
                continue;
            }
            if (inTable && !headerPassed && trimmed.matches("^\\|[\\-\\s|]+\\|$")) {
                headerPassed = true;
                continue;
            }
            if (inTable && headerPassed && trimmed.startsWith("|")) {
                String[] cols = trimmed.split("\\|");
                if (cols.length > idCol) {
                    String id = cols[idCol].trim();
                    if (!id.isEmpty()) {
                        caseIds.add(id);
                    }
                }
            } else if (inTable && headerPassed && !trimmed.startsWith("|")) {
                break;
            }
        }
        return caseIds;
    }

    private String[] parseTableRow(String mdContent, String testCaseId, int idCol) {
        String[] lines = mdContent.split("\n");
        boolean inTable = false;
        boolean headerPassed = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("|") && trimmed.contains("用例ID")) {
                inTable = true;
                continue;
            }

            if (inTable && !headerPassed && trimmed.matches("^\\|[\\-\\s|]+\\|$")) {
                headerPassed = true;
                continue;
            }

            if (inTable && headerPassed && trimmed.startsWith("|")) {
                String[] cols = trimmed.split("\\|");
                if (cols.length > idCol) {
                    String id = cols[idCol].trim();
                    if (id.equals(testCaseId)) {
                        // 去掉首尾空元素（split("|") 会在首尾产生空串）
                        List<String> cleanCols = new ArrayList<>();
                        for (String c : cols) {
                            cleanCols.add(c.trim());
                        }
                        return cleanCols.toArray(new String[0]);
                    }
                }
            } else if (inTable && headerPassed && !trimmed.startsWith("|")) {
                break;
            }
        }
        return null;
    }

    private String extractMdTitle(String mdContent) {
        String[] lines = mdContent.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * 兼容新旧两种脚本格式：
     * 新格式: aiAct : 在"工号"输入框中，...</br> aiAssert : ...
     * 旧格式: aiAct("使用业务经理账号登录系统")<br>aiAssert("登录成功")
     */
    private List<UiStep> parseScriptToSteps(String script) {
        List<UiStep> steps = new ArrayList<>();
        if (script == null || script.trim().isEmpty()) return steps;

        // 先按 </br> 或 <br> 拆分
        String[] parts = script.split("(?i)</?br\\s*/?>");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String type = null;
            String prompt = null;

            // 新格式: aiAct : prompt; 或 aiAssert : prompt;
            if (part.startsWith("aiAct")) {
                type = "aiAct";
                prompt = extractColonPrompt(part, "aiAct");
            } else if (part.startsWith("aiAssert")) {
                type = "aiAssert";
                prompt = extractColonPrompt(part, "aiAssert");
            }

            if (type != null && prompt != null && !prompt.isEmpty() && VALID_STEP_TYPES.contains(type)) {
                steps.add(new UiStep(type, prompt, ""));
            }
        }
        return steps;
    }

    /**
     * 提取冒号格式的 prompt: "aiAct : 在工号输入框中..." -> "在工号输入框中..."
     * 同时兼容旧格式: aiAct("prompt") -> "prompt"
     */
    private String extractColonPrompt(String part, String prefix) {
        // 先尝试旧格式: aiAct("prompt")
        Pattern funcPattern = Pattern.compile(prefix + "\\s*\\(\"([^\"]*)\"\\)");
        Matcher funcMatcher = funcPattern.matcher(part);
        if (funcMatcher.find()) {
            return funcMatcher.group(1).trim();
        }

        // 新格式: aiAct : prompt;
        int idx = part.indexOf(':');
        if (idx == -1) return part;
        String prompt = part.substring(idx + 1).trim();
        if (prompt.endsWith(";") || prompt.endsWith("；")) {
            prompt = prompt.substring(0, prompt.length() - 1);
        }
        return prompt.trim();
    }
}
