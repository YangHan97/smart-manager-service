package com.smarttest.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusQueryResponse {

    private Integer code;

    private String msg;

    private Integer taskStatus;
}
