package com.smarttest.manager.service;

import com.smarttest.manager.dto.UiTaskStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class UiTaskStatusService {

    @Qualifier("uiDownstreamWebClient")
    private final WebClient uiDownstreamWebClient;

    public UiTaskStatusResponse queryStatus(String taskId) {
        return uiDownstreamWebClient.get()
                .uri("/api/v1/tasks/{taskId}", taskId)
                .retrieve()
                .bodyToMono(UiTaskStatusResponse.class)
                .block();
    }
}
