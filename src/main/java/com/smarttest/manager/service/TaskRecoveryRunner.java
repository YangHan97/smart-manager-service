package com.smarttest.manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecoveryRunner implements ApplicationRunner {

    private final ManagerTaskDao managerTaskDao;
    private final ManagerTaskService managerTaskService;

    @Override
    public void run(ApplicationArguments args) {
        // Recover API tasks
        List<String> runningTaskIds = managerTaskDao.queryRunningManagerTaskIds();
        if (!runningTaskIds.isEmpty()) {
            log.info("Recovering {} running API manager tasks from database", runningTaskIds.size());
            for (String managerTaskId : runningTaskIds) {
                try {
                    Map<String, String> taskDetailMap = managerTaskDao.queryTaskDetailMap(managerTaskId);
                    if (taskDetailMap.isEmpty()) {
                        log.warn("No downstream tasks found for recovered managerTaskId={}, marking as failed", managerTaskId);
                        managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                        continue;
                    }
                    String callbackUrl = managerTaskDao.queryCallbackUrl(managerTaskId);
                    List<String> taskIds = new java.util.ArrayList<>(taskDetailMap.keySet());
                    managerTaskService.resumePolling(managerTaskId, taskIds, taskDetailMap, callbackUrl);
                    log.info("Resumed API polling for managerTaskId={}", managerTaskId);
                } catch (Exception e) {
                    log.error("Failed to recover API managerTaskId={}", managerTaskId, e);
                }
            }
        }

        // Recover UI tasks
        List<String> runningUiTaskIds = managerTaskDao.queryRunningUiManagerTaskIds();
        if (!runningUiTaskIds.isEmpty()) {
            log.info("Recovering {} running UI manager tasks from database", runningUiTaskIds.size());
            for (String managerTaskId : runningUiTaskIds) {
                try {
                    Map<String, String> taskDetailMap = managerTaskDao.queryTaskDetailMap(managerTaskId);
                    if (taskDetailMap.isEmpty()) {
                        log.warn("No downstream tasks found for recovered UI managerTaskId={}, marking as failed", managerTaskId);
                        managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                        continue;
                    }
                    String callbackUrl = managerTaskDao.queryCallbackUrl(managerTaskId);
                    List<String> taskIds = new java.util.ArrayList<>(taskDetailMap.keySet());
                    managerTaskService.resumeUiPolling(managerTaskId, taskIds, taskDetailMap, callbackUrl);
                    log.info("Resumed UI polling for managerTaskId={}", managerTaskId);
                } catch (Exception e) {
                    log.error("Failed to recover UI managerTaskId={}", managerTaskId, e);
                }
            }
        }

        if (runningTaskIds.isEmpty() && runningUiTaskIds.isEmpty()) {
            log.info("No running manager tasks to recover");
        }
    }
}
