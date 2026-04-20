package com.smarttest.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(AppConfig appConfig) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(appConfig.getBaseUrl())
                .build();
    }

    @Bean("docsDownloadWebClient")
    public WebClient docsDownloadWebClient(DocsDownloadConfig docsDownloadConfig) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(docsDownloadConfig.getBaseUrl())
                .build();
    }

    @Bean("llmWebClient")
    public WebClient llmWebClient(LlmConfig llmConfig) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(llmConfig.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + llmConfig.getApiKey())
                .build();
    }
}
