package com.smarttest.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttest.manager.config.LlmConfig;
import com.smarttest.manager.dto.LlmParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmParseService {

    private final WebClient llmWebClient;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;

    public List<LlmParseResult> parseMdDocument(String mdContent, List<String> testCaseIdList) {
        log.info("Parsing MD document, testCaseIdList={}", testCaseIdList);

        // 方案3：优先本地提取 api_definition、scenario、protocol，避免 LLM 超时
        List<LlmParseResult> localResults = parseLocally(mdContent, testCaseIdList);
        if (!localResults.isEmpty()) {
            log.info("Successfully parsed {} test cases locally", localResults.size());
            for (LlmParseResult r : localResults) {
                log.info("Local parse result: testCaseId={}, protocol={}", r.getTestCaseId(), r.getProtocol());
            }
            return localResults;
        }

        log.warn("Local parsing returned empty, falling back to LLM");
        return callLlm(mdContent, testCaseIdList);
    }

    private List<LlmParseResult> parseLocally(String mdContent, List<String> testCaseIdList) {
        if (testCaseIdList == null || testCaseIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<LlmParseResult> results = new ArrayList<>();
        for (String testCaseId : testCaseIdList) {
            Map<String, String> row = parseTableRow(mdContent, testCaseId);
            if (row == null) {
                log.warn("Test case {} not found in markdown table", testCaseId);
                continue;
            }

            String scenario = row.get("scenario");
            String tag = row.get("tag");
            String interfaceName = row.get("interfaceName");

            String protocol = extractProtocol(tag);
            String apiDefinition = extractApiDefinition(mdContent, interfaceName);

            LlmParseResult result = new LlmParseResult();
            result.setTestCaseId(testCaseId);
            result.setScenario(scenario);
            result.setProtocol(protocol);
            result.setApiDefinition(apiDefinition);
            result.setTestData("-");
            results.add(result);
        }
        return results;
    }

    private Map<String, String> parseTableRow(String mdContent, String testCaseId) {
        String[] lines = mdContent.split("\n");
        boolean inTable = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("|") && line.contains("编号") && line.contains("测试点描述")) {
                inTable = true;
                i++; // 跳过分隔符行 |---|---|
                continue;
            }
            if (inTable && line.startsWith("|")) {
                String[] cols = line.split("\\|");
                if (cols.length > 5) {
                    String id = cols[1].trim();
                    if (id.equals(testCaseId)) {
                        Map<String, String> map = new HashMap<>();
                        map.put("scenario", cols[2].trim());
                        map.put("tag", cols[3].trim());
                        map.put("interfaceName", cols[5].trim());
                        return map;
                    }
                }
            } else if (inTable && !line.startsWith("|")) {
                break; // 表格结束
            }
        }
        return null;
    }

    private String extractProtocol(String tag) {
        if (tag == null) return "";
        String upper = tag.toUpperCase();
        if (upper.contains("DUBBO")) return "DUBBO";
        if (upper.contains("HTTP")) return "HTTP";
        return "";
    }

    private String extractApiDefinition(String mdContent, String interfaceName) {
        int detailIdx = mdContent.indexOf("## 接口详情");
        if (detailIdx == -1) {
            return "";
        }
        String detailSection = mdContent.substring(detailIdx);

        // 截断到下一个二级标题（如 ## 其他内容）
        int nextH2 = detailSection.indexOf("\n## ", 4);
        if (nextH2 != -1) {
            detailSection = detailSection.substring(0, nextH2);
        }

        if (interfaceName == null || interfaceName.isEmpty()) {
            return detailSection.trim();
        }

        String targetHeader = "### " + interfaceName;
        int ifaceIdx = detailSection.indexOf(targetHeader);
        if (ifaceIdx == -1) {
            return detailSection.trim();
        }

        int basicIdx = detailSection.lastIndexOf("### 接口基本信息", ifaceIdx);
        if (basicIdx == -1) {
            basicIdx = ifaceIdx;
        }

        int nextBasicIdx = detailSection.indexOf("### 接口基本信息", ifaceIdx);
        int endIdx = (nextBasicIdx != -1 && nextBasicIdx > basicIdx) ? nextBasicIdx : detailSection.length();
        return detailSection.substring(basicIdx, endIdx).trim();
    }

    // ==================== LLM Fallback ====================

    private List<LlmParseResult> callLlm(String mdContent, List<String> testCaseIdList) {
        String prompt = buildPrompt(mdContent, testCaseIdList);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个测试接口解析助手。根据提供的 Markdown 格式的接口文档，按指定的测试用例 ID 提取对应的接口信息，返回 JSON 格式。");
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1);

        try {
            log.info("LLM request body: {}", objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            log.warn("Failed to serialize LLM request body for logging", e);
        }

        String response = llmWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("LLM raw response: {}", response);

        return parseLlmResponse(response, testCaseIdList);
    }

    private String buildPrompt(String mdContent, List<String> testCaseIdList) {
        String tcIds = (testCaseIdList == null || testCaseIdList.isEmpty())
                ? "未指定" : String.join(", ", testCaseIdList);
        return "请从以下 Markdown 接口文档中，按测试用例 ID 列表提取每个测试用例对应的接口信息。\n\n" +
                "测试用例 ID 列表: " + tcIds + "\n\n" +
                "请返回 JSON 数组，每个元素格式如下：\n" +
                "{\n" +
                "  \"testCaseId\": \"测试用例ID\",\n" +
                "  \"api_definition\": \"接口定义的 Markdown 内容\",\n" +
                "  \"scenario\": \"需求场景描述的 Markdown 内容\",\n" +
                "  \"test_data\": \"测试数据的 Markdown 内容\",\n" +
                "  \"protocol\": \"协议类型，DUBBO 或 HTTP\"\n" +
                "}\n\n" +
                "提取规则：\n" +
                "1. 在文档开头的测试用例列表表格中，找到对应编号的测试用例。\n" +
                "2. scenario 字段直接取该测试用例的【测试点描述】列内容。\n" +
                "3. protocol 字段根据【测试对象类型唯一标签】列判断，如 '接口类-HTTP' 则为 HTTP，'接口类-DUBBO' 则为 DUBBO。\n" +
                "4. api_definition 字段：根据该测试用例的【接口名称】，去文档下方 '## 接口详情' 部分找到同名标题（如 '### 接口名称'），将该接口的完整定义 Markdown（含接口基本信息、入参、出参、变更说明、逻辑实现等）作为内容。\n" +
                "5. test_data 字段：如果文档中有专门的测试数据/测试环境章节则提取，否则填空字符串。\n" +
                "6. 只返回 JSON 数组，不要其他内容\n\n" +
                "--- 接口文档开始 ---\n" +
                mdContent + "\n" +
                "--- 接口文档结束 ---";
    }

    private List<LlmParseResult> parseLlmResponse(String response, List<String> testCaseIdList) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            String jsonContent = content;
            if (content.contains("```json")) {
                jsonContent = content.substring(content.indexOf("```json") + 7);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```")).trim();
            } else if (content.contains("```")) {
                jsonContent = content.substring(content.indexOf("```") + 3);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```")).trim();
            }

            String normalizedJson = jsonContent
                    .replace("\"apiDefinition\"", "\"api_definition\"")
                    .replace("\"testData\"", "\"test_data\"")
                    .replace("\"testPointId\"", "\"testCaseId\"");

            List<LlmParseResult> results = objectMapper.readValue(
                    normalizedJson, new TypeReference<List<LlmParseResult>>() {});

            log.info("LLM parsed {} test cases", results.size());
            for (LlmParseResult r : results) {
                log.info("LLM parse result: testCaseId={}, protocol={}", r.getTestCaseId(), r.getProtocol());
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", response, e);
            throw new RuntimeException("LLM response parsing failed", e);
        }
    }
}
