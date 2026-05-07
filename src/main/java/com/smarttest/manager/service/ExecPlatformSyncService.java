package com.smarttest.manager.service;

import com.smarttest.manager.dto.ExternalSyncRequest;
import com.smarttest.manager.dto.UiEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecPlatformSyncService {

    @Qualifier("execPlatformWebClient")
    private final WebClient execPlatformWebClient;

    /**
     * @param managerTaskId  manager 任务 ID
     * @param suiteName      测试集名称（MD标题前缀 + managerTaskId）
     * @param taskIdToCaseName downstreamTaskId -> "用例ID-用例名称"
     * @param environment    环境信息
     */
    public void syncSuite(String managerTaskId, String suiteName,
                          Map<String, String> taskIdToCaseName,
                          UiEnvironment environment) {
        ExternalSyncRequest request = new ExternalSyncRequest();
        request.setExternalBatchNo(managerTaskId);
        request.setSuiteName(suiteName);
        request.setTaskType(environment.getType().toLowerCase());
        request.setPkey("proj_003");
        request.setCreator("manager-service");
        request.setEntryUrl(environment.getUrl());

        List<ExternalSyncRequest.ExternalSyncTask> taskList = new ArrayList<>();
        for (Map.Entry<String, String> entry : taskIdToCaseName.entrySet()) {
            String downstreamTaskId = entry.getKey();
            String caseName = entry.getValue();  // 格式: 用例ID-用例名称

            ExternalSyncRequest.ExternalSyncTask t = new ExternalSyncRequest.ExternalSyncTask();
            t.setTaskName(caseName);
            t.setDownstreamTaskId(downstreamTaskId);
            t.setExecutor("");
            t.setDevice("");
            taskList.add(t);
        }
        request.setTaskList(taskList);

        String respBody = execPlatformWebClient.post()
                .uri("/api/v1/external/sync-suite")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("[UI-SYNC] Synced {} tasks to exec-platform for managerTaskId={}, suiteName={}, pkey={}, resp={}",
                taskList.size(), managerTaskId, suiteName, request.getPkey(), respBody);
    }
}
