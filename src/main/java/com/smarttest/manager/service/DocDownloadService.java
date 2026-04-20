package com.smarttest.manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocDownloadService {

    private final WebClient docsDownloadWebClient;

    @Value("${mock.md-content:}")
    private String mockMdContent;

    @Value("${mock.md-file:}")
    private String mockMdFile;

    public String downloadDoc(String storyId, String phase, String docType) {
        String mdFromFile = loadMockMdFromFile();
        if (mdFromFile != null && !mdFromFile.isEmpty()) {
            log.info("Using mocked MD file for storyId={}, phase={}, docType={}", storyId, phase, docType);
            return mdFromFile;
        }

        if (mockMdContent != null && !mockMdContent.isEmpty()) {
            log.info("Using mocked MD content for storyId={}, phase={}, docType={}", storyId, phase, docType);
            return mockMdContent;
        }

        log.info("Downloading doc for storyId={}, phase={}, docType={}", storyId, phase, docType);

        byte[] body = docsDownloadWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/stories/{storyId}/docs/download")
                        .queryParam("phase", phase)
                        .queryParam("docType", docType)
                        .build(storyId))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    private String loadMockMdFromFile() {
        if (mockMdFile == null || mockMdFile.isEmpty()) {
            return null;
        }
        try {
            Resource resource = new FileSystemResource(mockMdFile);
            if (!resource.exists()) {
                log.warn("Mock MD file not found: {}", mockMdFile);
                return null;
            }
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to load mock MD file: {}", mockMdFile, e);
            return null;
        }
    }
}
