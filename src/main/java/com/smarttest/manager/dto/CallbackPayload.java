package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackPayload {

    @JsonProperty("manager_task_id")
    private String managerTaskId;

    private String phase;

    private List<TaskResult> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResult {

        @JsonProperty("task_id")
        private String taskId;

        @JsonProperty("test_case_id")
        private String testCaseId;

        private String status;

        @JsonProperty("result_summary")
        private JsonNode resultSummary;
    }
}
