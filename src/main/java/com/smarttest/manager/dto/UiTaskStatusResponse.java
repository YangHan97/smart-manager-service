package com.smarttest.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UiTaskStatusResponse {
    private Integer code;
    private String message;
    private UiTaskData data;

    @Data
    public static class UiTaskData {
        @JsonProperty("task_id")
        private String taskId;
        private String status;       // pending/running/success/failed/killed/timeout
        private String type;
        private String log;
        @JsonProperty("report_url")
        private String reportUrl;
        @JsonProperty("start_time")
        private Long startTime;
        @JsonProperty("end_time")
        private Long endTime;
        @JsonProperty("exit_code")
        private Integer exitCode;
    }
}
