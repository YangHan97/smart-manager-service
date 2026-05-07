package com.smarttest.manager.dto;

import lombok.Data;

import java.util.List;

@Data
public class UiTestCase {
    private String testCaseId;
    private String caseName;
    private List<UiStep> stepList;
}
