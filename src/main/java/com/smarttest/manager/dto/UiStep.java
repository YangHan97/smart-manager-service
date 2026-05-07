package com.smarttest.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UiStep {
    private String type;      // aiAct / aiAssert
    private String prompt;
    private String options;   // 默认 ""
}
