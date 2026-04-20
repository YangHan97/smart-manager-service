package com.smarttest.manager.service;

import com.smarttest.manager.dto.CallbackPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    public void sendCallback(String callbackUrl, CallbackPayload payload) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("Callback skipped because callbackUrl is empty, phase={}", payload.getPhase());
            return;
        }

        log.info("Sending callback to {}, phase={}, tasks={}",
                callbackUrl, payload.getPhase(), payload.getTasks().size());

        WebClient.builder().build()
                .post()
                .uri(callbackUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(30))
                .doOnError(e -> log.error("Callback to {} failed: {}", callbackUrl, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe(
                        response -> log.info("Callback to {} succeeded, status={}", callbackUrl, response.getStatusCode()),
                        error -> log.error("Callback to {} failed", callbackUrl, error)
                );
    }
}
