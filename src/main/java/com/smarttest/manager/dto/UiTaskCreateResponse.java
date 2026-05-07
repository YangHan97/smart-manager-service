package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UiTaskCreateResponse {
    private Integer code;
    private String message;
    private UiTaskCreateData data;

    public String getTaskId() {
        return data != null ? data.getTaskId() : null;
    }

    @Data
    public static class UiTaskCreateData {
        @JsonProperty("task_id")
        private String taskId;
        @JsonProperty("created_time")
        private Long createdTime;
    }
}
