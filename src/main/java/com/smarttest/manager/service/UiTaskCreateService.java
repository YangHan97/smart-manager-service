package com.smarttest.manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttest.manager.dto.UiEnvironment;
import com.smarttest.manager.dto.UiTaskCreateRequest;
import com.smarttest.manager.dto.UiTaskCreateResponse;
import com.smarttest.manager.dto.UiTestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class UiTaskCreateService {

    @Qualifier("uiDownstreamWebClient")
    private final WebClient uiDownstreamWebClient;
    private final ObjectMapper objectMapper;

    public UiTaskCreateResponse createTask(UiTestCase testCase, UiEnvironment env,
                                           String callbackUrl, Integer maxRetries) {
        UiTaskCreateRequest request = UiTaskCreateRequest.builder()
                .testCase(UiTaskCreateRequest.UiTestCaseModel.builder()
                        .stepList(testCase.getStepList())
                        .build())
                .environment(UiTaskCreateRequest.UiEnvironmentModel.builder()
                        .type(env.getType().toUpperCase())
                        .url(env.getUrl() != null ? env.getUrl() : "")
                        .packageUrl(env.getPackageUrl() != null ? env.getPackageUrl() : "")
                        .appId(env.getAppId() != null ? env.getAppId() : "")
                        .build())
                .callbackUrl(callbackUrl != null ? callbackUrl : "")
                .maxRetries(maxRetries != null ? maxRetries : 1)
                .build();

        try {
            log.info("[UI-DOWNSTREAM] POST /api/v1/ui/create request body: {}",
                    objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            log.warn("[UI-DOWNSTREAM] Failed to serialize request body for logging", e);
        }

        return uiDownstreamWebClient.post()
                .uri("/api/v1/ui/create")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UiTaskCreateResponse.class)
                .block();
    }
}
