package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest {

    @JsonProperty("api_definition")
    private String apiDefinition;

    private String environment;

    private String protocol;

    private String scenario;

    @JsonProperty("test_data")
    private String testData;

    private Options options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Options {

        private String mode;

        @JsonProperty("max_test_cases")
        private Integer maxTestCases;

        @JsonProperty("max_retry")
        private Integer maxRetry;

        @JsonProperty("independent_ratio")
        private Double independentRatio;

        private List<String> tags;
    }
}
