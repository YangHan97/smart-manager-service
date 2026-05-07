package com.smarttest.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class UiManagerTaskRequest {
    @NotBlank
    private String storyId;

    @NotBlank
    private String phase;

    @NotBlank
    private String docType;

    // 不填时默认执行 Markdown 中解析出的全部用例
    private List<String> testCaseIdList;

    @NotBlank
    private String callbackUrl;

    @NotNull
    private UiEnvironment environment;

    private Integer maxRetries = 1;
}
