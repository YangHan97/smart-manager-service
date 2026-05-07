package com.smarttest.manager.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExternalSyncRequest {
    private String externalBatchNo;
    private String suiteName;
    private String taskType;
    private String pkey;
    private String creator;
    private String entryUrl;
    private Long appPackageId;
    private List<ExternalSyncTask> taskList;

    @Data
    public static class ExternalSyncTask {
        private String taskName;
        private String downstreamTaskId;
        private String executor;
        private String device;
    }
}
