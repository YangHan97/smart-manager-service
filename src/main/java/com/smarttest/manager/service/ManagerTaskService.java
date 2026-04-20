package com.smarttest.manager.service;

import com.smarttest.manager.dto.*;
import com.smarttest.manager.config.PollingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerTaskService {

    private final DocDownloadService docDownloadService;
    private final LlmParseService llmParseService;
    private final TaskCreateService taskCreateService;
    private final TaskStatusService taskStatusService;
    private final CallbackService callbackService;
    private final PollingConfig pollingConfig;
    private final ReportService reportService;
    private final ReportUploadService reportUploadService;
    private final StoryNameService storyNameService;
    private final ManagerTaskDao managerTaskDao;

    public ManagerTaskResponse dispatch(ManagerTaskRequest request) {
        String managerTaskId = generateManagerTaskId();
        log.info("Starting manager task {}, storyId={}, testCaseIdList={}",
                managerTaskId, request.getStoryId(), request.getTestCaseIdList());

        String envDto = request.getEnvDTO() == null ? "" : request.getEnvDTO();

        // Persist manager task immediately
        managerTaskDao.insertManagerTask(managerTaskId, request.getStoryId(),
                request.getPhase(), request.getDocType(), envDto, request.getCallbackUrl());

        // Execute downstream dispatching asynchronously
        CompletableFuture.runAsync(() -> {
            String mdContent;
            try {
                mdContent = docDownloadService.downloadDoc(
                        request.getStoryId(), request.getPhase(), request.getDocType());
            } catch (Exception e) {
                log.error("Manager task {} failed at doc download", managerTaskId, e);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            List<LlmParseResult> parsedResults;
            try {
                parsedResults = llmParseService.parseMdDocument(mdContent, request.getTestCaseIdList());
            } catch (Exception e) {
                log.error("Manager task {} failed at LLM parse", managerTaskId, e);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            Map<String, String> taskIdToTestCaseId = new LinkedHashMap<>();
            for (LlmParseResult parsed : parsedResults) {
                try {
                    TaskCreateResponse createResponse = taskCreateService.createTask(
                            parsed, envDto);
                    taskIdToTestCaseId.put(createResponse.getTaskId(), parsed.getTestCaseId());
                    managerTaskDao.insertTaskDetail(managerTaskId, createResponse.getTaskId(), parsed.getTestCaseId());
                    log.info("Created downstream task {} for testCaseId={}",
                            createResponse.getTaskId(), parsed.getTestCaseId());
                } catch (Exception e) {
                    log.error("Failed to create task for testCaseId={}", parsed.getTestCaseId(), e);
                }
            }

            if (taskIdToTestCaseId.isEmpty() && !parsedResults.isEmpty()) {
                log.error("Manager task {} failed: all downstream task creations failed", managerTaskId);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            List<String> taskIds = new ArrayList<>(taskIdToTestCaseId.keySet());
            if (!taskIds.isEmpty()) {
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "running");
                pollAndCallback(managerTaskId, taskIds, taskIdToTestCaseId, request.getCallbackUrl());
            } else {
                log.warn("No downstream tasks created for manager task {}", managerTaskId);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "completed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
            }
        });

        return ManagerTaskResponse.builder()
                .code(200)
                .msg("success")
                .taskId(managerTaskId)
                .build();
    }

    public void resumePolling(String managerTaskId, List<String> taskIds,
                              Map<String, String> taskIdToTestCaseId,
                              String callbackUrl) {
        CompletableFuture.runAsync(() ->
                pollAndCallback(managerTaskId, taskIds, taskIdToTestCaseId, callbackUrl));
    }

    public TaskStatusQueryResponse queryManagerTaskStatus(String managerTaskId) {
        List<String> taskIds = managerTaskDao.queryDownstreamTaskIds(managerTaskId);
        if (taskIds == null || taskIds.isEmpty()) {
            log.warn("Manager task {} not found or has no downstream tasks", managerTaskId);
            return TaskStatusQueryResponse.builder()
                    .code(200)
                    .msg("success")
                    .taskStatus(2)
                    .build();
        }

        boolean hasRunning = false;
        boolean hasCompleted = false;
        boolean hasFailed = false;
        boolean hasPendingOrUnknown = false;

        for (String taskId : taskIds) {
            String s;
            try {
                TaskStatusResponse status = taskStatusService.queryStatus(taskId);
                s = status != null ? status.getStatus() : "unknown";
            } catch (Exception e) {
                log.warn("Query downstream status failed for taskId={}: {}", taskId, e.getMessage());
                s = "unknown";
            }

            if ("running".equals(s)) {
                hasRunning = true;
            } else if ("completed".equals(s)) {
                hasCompleted = true;
            } else if ("failed".equals(s)) {
                hasFailed = true;
            } else {
                hasPendingOrUnknown = true;
            }
        }

        int taskStatus;
        if (hasRunning) {
            taskStatus = 1;
        } else if (hasPendingOrUnknown && !hasCompleted && !hasFailed) {
            taskStatus = 2;
        } else if (hasCompleted && !hasFailed && !hasPendingOrUnknown) {
            taskStatus = 0;
        } else if (hasFailed && !hasCompleted && !hasPendingOrUnknown) {
            taskStatus = -1;
        } else if (hasPendingOrUnknown) {
            taskStatus = 1;
        } else {
            taskStatus = -1;
        }

        return TaskStatusQueryResponse.builder()
                .code(200)
                .msg("success")
                .taskStatus(taskStatus)
                .build();
    }

    private void pollAndCallback(String managerTaskId, List<String> taskIds,
                                  Map<String, String> taskIdToTestCaseId,
                                  String callbackUrl) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                Math.min(taskIds.size(), 4));

        Map<String, TaskStatusResponse> finalStatuses = new HashMap<>();
        Set<String> completed = Collections.synchronizedSet(new HashSet<>());
        int timeoutSeconds = pollingConfig.getTimeoutMinutes() * 60;
        int maxAttempts = timeoutSeconds / pollingConfig.getIntervalSeconds();

        for (String taskId : taskIds) {
            CompletableFuture.runAsync(() -> {
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    try {
                        TaskStatusResponse status = taskStatusService.queryStatus(taskId);
                        if ("completed".equals(status.getStatus()) || "failed".equals(status.getStatus())) {
                            finalStatuses.put(taskId, status);
                            completed.add(taskId);
                            managerTaskDao.updateTaskDetailStatus(managerTaskId, taskId, status.getStatus());
                            log.info("Task {} reached terminal status: {}", taskId, status.getStatus());
                            return;
                        }
                        Thread.sleep(pollingConfig.getIntervalSeconds() * 1000L);
                    } catch (Exception e) {
                        log.warn("Polling error for taskId={}: {}", taskId, e.getMessage());
                        try {
                            Thread.sleep(pollingConfig.getIntervalSeconds() * 1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                log.warn("Task {} polling timed out", taskId);
                managerTaskDao.updateTaskDetailStatus(managerTaskId, taskId, "unknown");
                completed.add(taskId);
            }, scheduler).exceptionally(e -> {
                log.error("Polling failed for taskId={}", taskId, e);
                managerTaskDao.updateTaskDetailStatus(managerTaskId, taskId, "unknown");
                completed.add(taskId);
                return null;
            });
        }

        long progressLogInterval = 30 * 1000L;
        long nextProgressLogTime = System.currentTimeMillis() + progressLogInterval;
        while (completed.size() < taskIds.size()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (System.currentTimeMillis() >= nextProgressLogTime) {
                log.info("Manager task {} polling progress: {}/{} tasks completed",
                        managerTaskId, completed.size(), taskIds.size());
                nextProgressLogTime = System.currentTimeMillis() + progressLogInterval;
            }
        }

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        List<CallbackPayload.TaskResult> results = new ArrayList<>();
        for (String taskId : taskIds) {
            TaskStatusResponse status = finalStatuses.get(taskId);
            results.add(CallbackPayload.TaskResult.builder()
                    .taskId(taskId)
                    .testCaseId(taskIdToTestCaseId.get(taskId))
                    .status(status != null ? status.getStatus() : "unknown")
                    .resultSummary(status != null ? status.getResultSummary() : null)
                    .build());
        }

        CallbackPayload completionCallback = CallbackPayload.builder()
                .managerTaskId(managerTaskId)
                .phase("completed")
                .tasks(results)
                .build();
        callbackService.sendCallback(callbackUrl, completionCallback);

        try {
            String storyId = managerTaskDao.queryStoryid(managerTaskId);
            String storyName = storyNameService.queryStoryName(storyId);
            String reportPath = reportService.generateReport(managerTaskId, taskIds, storyId, storyName);
            if (reportPath != null) {
                reportUploadService.uploadReport(storyId, reportPath);
            }
        } catch (Exception e) {
            log.error("Failed to generate or upload report for managerTaskId={}", managerTaskId, e);
        }

        managerTaskDao.updateManagerTaskStatus(managerTaskId, "completed");
        log.info("Manager task {} completed, all downstream tasks finished", managerTaskId);
    }

    private String generateManagerTaskId() {
        return "mtk_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private CallbackPayload buildFailureCallback(String managerTaskId) {
        return CallbackPayload.builder()
                .managerTaskId(managerTaskId)
                .phase("failed")
                .tasks(Collections.emptyList())
                .build();
    }
}
