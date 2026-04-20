package com.smarttest.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttest.manager.dto.TaskStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatusService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TaskStatusResponse queryStatus(String taskId) {
        Map<String, String> body = new HashMap<>();
        body.put("task_id", taskId);

        try {
            log.info("TaskStatus request: {}", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Failed to serialize TaskStatus request for logging", e);
        }

        TaskStatusResponse response = webClient.post()
                .uri("/api/v1/tasks/status")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TaskStatusResponse.class)
                .block();

        try {
            log.info("TaskStatus response for taskId={}: {}",
                    taskId, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("Failed to serialize TaskStatus response for logging", e);
        }

        return response;
    }
}
