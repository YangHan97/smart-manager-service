package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class TaskStatusResponse {

    @JsonProperty("task_id")
    private String taskId;

    private String status;

    @JsonProperty("result_summary")
    private JsonNode resultSummary;

    private JsonNode progress;
}
