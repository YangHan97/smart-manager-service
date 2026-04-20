package com.smarttest.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class ManagerTaskRequest {

    @NotBlank
    private String storyId;

    @NotEmpty
    private List<String> testCaseIdList;

    @NotBlank
    private String phase;

    @NotBlank
    private String docType;

    @NotBlank
    private String callbackUrl;

    @NotBlank
    private String envDTO;
}
