package com.smarttest.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmParseResult {

    private String testCaseId;

    private String apiDefinition;

    private String scenario;

    private String testData;

    private String protocol;
}
