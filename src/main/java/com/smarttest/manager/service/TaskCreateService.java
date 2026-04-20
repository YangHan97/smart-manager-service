package com.smarttest.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttest.manager.dto.LlmParseResult;
import com.smarttest.manager.dto.TaskCreateRequest;
import com.smarttest.manager.dto.TaskCreateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCreateService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TaskCreateResponse createTask(LlmParseResult parsed, String environment) {
        log.info("Creating downstream task for testCaseId={}", parsed.getTestCaseId());

        String envMarkdown = (environment == null || environment.isEmpty())
                ? "## 测试环境\n\n- 默认环境"
                : environment;

        TaskCreateRequest request = TaskCreateRequest.builder()
                .apiDefinition(parsed.getApiDefinition())
                .environment(envMarkdown)
                .protocol(parsed.getProtocol())
                .scenario(parsed.getScenario())
                .testData(parsed.getTestData())
                .options(TaskCreateRequest.Options.builder()
                        .mode("comprehensive")
                        .maxTestCases(10)
                        .maxRetry(3)
                        .tags(Collections.singletonList("manager-dispatched"))
                        .build())
                .build();

        try {
            log.info("TaskCreate request for testCaseId={}: {}",
                    parsed.getTestCaseId(), objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.warn("Failed to serialize TaskCreate request for logging", e);
        }

        TaskCreateResponse response = webClient.post()
                .uri("/api/v1/tasks/create")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TaskCreateResponse.class)
                .block();

        log.info("TaskCreate response for testCaseId={}: taskId={}, status={}, createdAt={}",
                parsed.getTestCaseId(), response.getTaskId(), response.getStatus(), response.getCreatedAt());

        return response;
    }
}
