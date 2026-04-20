package com.smarttest.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryNameService {

    private final WebClient docsDownloadWebClient;
    private final ObjectMapper objectMapper;

    public String queryStoryName(String storyId) {
        try {
            String response = docsDownloadWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/stories/{storyId}/docs")
                            .queryParam("phase", "PRODUCT_DESIGN")
                            .build(storyId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Query story documents for storyId={}, response={}", storyId, response);

            if (response == null || response.isEmpty()) {
                return storyId;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() == 0) {
                log.warn("No documents found for storyId={}", storyId);
                return storyId;
            }

            String fileName = data.get(0).path("fileName").asText();
            return extractStoryName(fileName, storyId);
        } catch (Exception e) {
            log.error("Failed to query story name for storyId={}", storyId, e);
            return storyId;
        }
    }

    private String extractStoryName(String fileName, String storyId) {
        if (fileName == null || fileName.isEmpty()) {
            return storyId;
        }
        String prefix = "[" + storyId + "]";
        int start = fileName.indexOf(prefix);
        if (start == -1) {
            return storyId;
        }
        int nameStart = start + prefix.length();
        int end = fileName.lastIndexOf(".md");
        if (end == -1 || end <= nameStart) {
            return storyId;
        }
        return fileName.substring(nameStart, end);
    }
}
