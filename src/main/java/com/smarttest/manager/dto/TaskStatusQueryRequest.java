package com.smarttest.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TaskStatusQueryRequest {

    @NotBlank
    private String taskId;
}
