package com.smarttest.manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportUploadService {

    private final WebClient docsDownloadWebClient;

    public void uploadReport(String storyId, String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            log.warn("No report file to upload for storyId={}", storyId);
            return;
        }

        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(Paths.get(filePath)));

            String response = docsDownloadWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/stories/{storyId}/docs")
                            .queryParam("phase", "TEST_EXECUTION")
                            .queryParam("docType", "ISTC_TR")
                            .build(storyId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(parts)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Uploaded report for storyId={}, response={}", storyId, response);
        } catch (Exception e) {
            log.error("Failed to upload report for storyId={}, filePath={}", storyId, filePath, e);
        }
    }
}
