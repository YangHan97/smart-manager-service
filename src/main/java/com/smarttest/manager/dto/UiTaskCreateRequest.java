package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiTaskCreateRequest {
    @JsonProperty("test_case")
    private UiTestCaseModel testCase;
    private UiEnvironmentModel environment;
    @JsonProperty("callback_url")
    private String callbackUrl;
    @JsonProperty("max_retries")
    private Integer maxRetries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UiTestCaseModel {
        @JsonProperty("step_list")
        private List<UiStep> stepList;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UiEnvironmentModel {
        private String type;
        private String url;
        @JsonProperty("package_url")
        private String packageUrl;
        @JsonProperty("app_id")
        private String appId;
    }
}
