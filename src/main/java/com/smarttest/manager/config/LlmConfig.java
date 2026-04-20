package com.smarttest.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
}
