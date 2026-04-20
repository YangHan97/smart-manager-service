package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TaskCreateResponse {

    @JsonProperty("task_id")
    private String taskId;

    private String status;

    @JsonProperty("created_at")
    private String createdAt;
}
