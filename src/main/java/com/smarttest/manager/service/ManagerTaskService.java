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

    // UI orchestration services
    private final UiMdParseService uiMdParseService;
    private final UiTaskCreateService uiTaskCreateService;
    private final UiTaskStatusService uiTaskStatusService;
    private final UiReportService uiReportService;
    private final ExecPlatformSyncService execPlatformSyncService;

    public ManagerTaskResponse dispatch(ManagerTaskRequest request) {
        String managerTaskId = generateManagerTaskId();
        log.info("Starting manager task {}, storyId={}, testCaseIdList={}",
                managerTaskId, request.getStoryId(), request.getTestCaseIdList());

        String envDto = request.getEnvDTO() == null ? "" : request.getEnvDTO();

        // Persist manager task immediately
        managerTaskDao.insertManagerTask(managerTaskId, request.getStoryId(), "api",
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

        boolean reportSuccess = true;
        try {
            String storyId = managerTaskDao.queryStoryid(managerTaskId);
            String storyName = storyNameService.queryStoryName(storyId);
            String reportPath = reportService.generateReport(managerTaskId, taskIds, storyId, storyName);
            if (reportPath != null) {
                reportUploadService.uploadReport(storyId, reportPath);
            } else {
                reportSuccess = false;
                log.warn("Report generation returned null for managerTaskId={}", managerTaskId);
            }
        } catch (Exception e) {
            reportSuccess = false;
            log.error("Failed to generate or upload report for managerTaskId={}", managerTaskId, e);
        }

        CallbackPayload completionCallback = CallbackPayload.builder()
                .managerTaskId(managerTaskId)
                .phase(reportSuccess ? "completed" : "completed_with_report_error")
                .tasks(results)
                .build();
        callbackService.sendCallback(callbackUrl, completionCallback);

        managerTaskDao.updateManagerTaskStatus(managerTaskId, "completed");
        log.info("Manager task {} completed, all downstream tasks finished", managerTaskId);
    }

    private String generateManagerTaskId() {
        return "mtk_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateUiManagerTaskId() {
        return "uimtk_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private CallbackPayload buildFailureCallback(String managerTaskId) {
        return CallbackPayload.builder()
                .managerTaskId(managerTaskId)
                .phase("failed")
                .tasks(Collections.emptyList())
                .build();
    }

    // ==================== UI Orchestration ====================

    public UiManagerTaskResponse dispatchUi(UiManagerTaskRequest request) {
        String managerTaskId = generateUiManagerTaskId();
        log.info("[UI-DISPATCH] Start managerTaskId={}, storyId={}, phase={}, docType={}, caseFilter={}, envType={}, maxRetries={}",
                managerTaskId, request.getStoryId(), request.getPhase(), request.getDocType(),
                request.getTestCaseIdList() != null ? request.getTestCaseIdList() : "all",
                request.getEnvironment() != null ? request.getEnvironment().getType() : "null",
                request.getMaxRetries());

        managerTaskDao.insertManagerTask(managerTaskId, request.getStoryId(), "ui",
                request.getPhase(), request.getDocType(), "", request.getCallbackUrl());
        log.info("[UI-DISPATCH] managerTaskId={} persisted to DB", managerTaskId);

        CompletableFuture.runAsync(() -> {
            String mdContent;
            try {
                mdContent = docDownloadService.downloadDoc(
                        request.getStoryId(), request.getPhase(), request.getDocType());
                log.info("[UI-DISPATCH] managerTaskId={} downloaded doc, length={}",
                        managerTaskId, mdContent != null ? mdContent.length() : 0);
            } catch (Exception e) {
                log.error("[UI-DISPATCH] managerTaskId={} doc download FAILED: storyId={}, phase={}, docType={}",
                        managerTaskId, request.getStoryId(), request.getPhase(), request.getDocType(), e);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            UiMdParseService.ParseResult parseResult;
            List<UiTestCase> testCases;
            try {
                parseResult = uiMdParseService.parseMdDocumentFull(mdContent, request.getTestCaseIdList());
                testCases = parseResult.getTestCases();
                log.info("[UI-DISPATCH] managerTaskId={} parsed {} test cases from MD, mdTitle={}",
                        managerTaskId, testCases.size(), parseResult.getMdTitle());
                for (int i = 0; i < testCases.size(); i++) {
                    UiTestCase tc = testCases.get(i);
                    log.info("[UI-DISPATCH] managerTaskId={} case[{}] id={}, name={}, steps={}",
                            managerTaskId, i, tc.getTestCaseId(), tc.getCaseName(),
                            tc.getStepList() != null ? tc.getStepList().size() : 0);
                }
            } catch (Exception e) {
                log.error("[UI-DISPATCH] managerTaskId={} MD parse FAILED", managerTaskId, e);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            if (testCases.isEmpty()) {
                log.error("[UI-DISPATCH] managerTaskId={} no test cases parsed after filter, abort",
                        managerTaskId);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            // 任务名格式: 用例ID-用例名称
            Map<String, String> taskIdToCaseName = new LinkedHashMap<>();
            Map<String, UiTaskCreateResponse> taskIdToResponse = new HashMap<>();
            int successCount = 0;
            int failCount = 0;

            for (UiTestCase testCase : testCases) {
                String displayName = request.getStoryId() + "-" + testCase.getTestCaseId() + "-" + testCase.getCaseName();
                log.info("[UI-DISPATCH] managerTaskId={} creating downstream task for case={}",
                        managerTaskId, displayName);
                try {
                    UiTaskCreateResponse createResp = uiTaskCreateService.createTask(
                            testCase, request.getEnvironment(),
                            null, request.getMaxRetries());

                    if (createResp != null && createResp.getTaskId() != null) {
                        taskIdToCaseName.put(createResp.getTaskId(), displayName);
                        taskIdToResponse.put(createResp.getTaskId(), createResp);
                        managerTaskDao.insertTaskDetail(managerTaskId,
                                createResp.getTaskId(), testCase.getTestCaseId(), displayName);
                        successCount++;
                        log.info("[UI-DISPATCH] managerTaskId={} downstream task CREATED for case={}, taskId={}",
                                managerTaskId, displayName, createResp.getTaskId());
                    } else {
                        failCount++;
                        log.error("[UI-DISPATCH] managerTaskId={} downstream task creation returned NULL for case={}, resp={}",
                                managerTaskId, displayName, createResp);
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("[UI-DISPATCH] managerTaskId={} downstream task creation FAILED for case={}, envType={}, maxRetries={}",
                            managerTaskId, displayName,
                            request.getEnvironment() != null ? request.getEnvironment().getType() : "null",
                            request.getMaxRetries(), e);
                }
            }

            log.info("[UI-DISPATCH] managerTaskId={} task creation summary: total={}, success={}, fail={}",
                    managerTaskId, testCases.size(), successCount, failCount);

            if (taskIdToCaseName.isEmpty()) {
                log.error("[UI-DISPATCH] managerTaskId={} ALL downstream task creations failed, abort",
                        managerTaskId);
                managerTaskDao.updateManagerTaskStatus(managerTaskId, "failed");
                callbackService.sendCallback(request.getCallbackUrl(), buildFailureCallback(managerTaskId));
                return;
            }

            // 测试集名称 = MD标题中首个UI用例ID前缀 + managerTaskId
            String suiteName = buildSuiteName(parseResult.getMdTitle(), managerTaskId);
            try {
                execPlatformSyncService.syncSuite(managerTaskId, suiteName,
                        taskIdToCaseName, request.getEnvironment());
                log.info("[UI-DISPATCH] managerTaskId={} exec-platform sync succeeded, suiteName={}",
                        managerTaskId, suiteName);
            } catch (Exception e) {
                log.warn("[UI-DISPATCH] managerTaskId={} exec-platform sync FAILED: {}",
                        managerTaskId, e.getMessage(), e);
            }

            managerTaskDao.updateManagerTaskStatus(managerTaskId, "running");
            log.info("[UI-DISPATCH] managerTaskId={} status updated to running, starting poll on {} tasks",
                    managerTaskId, taskIdToCaseName.size());
            pollUiAndCallback(managerTaskId, new ArrayList<>(taskIdToCaseName.keySet()),
                    taskIdToCaseName, request.getCallbackUrl());
        });

        return UiManagerTaskResponse.builder()
                .code(200).msg("success").taskId(managerTaskId).build();
    }

    public void resumeUiPolling(String managerTaskId, List<String> taskIds,
                                 Map<String, String> taskIdToCaseName, String callbackUrl) {
        log.info("[UI-POLL-RESUME] managerTaskId={}, taskCount={}, taskIds={}",
                managerTaskId, taskIds.size(), taskIds);
        CompletableFuture.runAsync(() ->
                pollUiAndCallback(managerTaskId, taskIds, taskIdToCaseName, callbackUrl));
    }

    public TaskStatusQueryResponse queryUiManagerTaskStatus(String managerTaskId) {
        List<String> taskIds = managerTaskDao.queryDownstreamTaskIds(managerTaskId);
        log.info("[UI-QUERY] managerTaskId={}, downstreamTaskCount={}", managerTaskId, taskIds != null ? taskIds.size() : 0);
        if (taskIds == null || taskIds.isEmpty()) {
            log.warn("[UI-QUERY] managerTaskId={} not found or has no downstream tasks", managerTaskId);
            return TaskStatusQueryResponse.builder()
                    .code(200)
                    .msg("success")
                    .taskStatus(2)
                    .build();
        }

        boolean hasRunning = false;
        boolean hasSuccess = false;
        boolean hasFailed = false;
        boolean hasPendingOrUnknown = false;

        for (String taskId : taskIds) {
            String s;
            try {
                UiTaskStatusResponse status = uiTaskStatusService.queryStatus(taskId);
                s = status != null && status.getData() != null ? status.getData().getStatus() : "unknown";
                log.info("[UI-QUERY] managerTaskId={}, taskId={}, status={}", managerTaskId, taskId, s);
            } catch (Exception e) {
                log.warn("[UI-QUERY] managerTaskId={}, taskId={} status query FAILED: {}",
                        managerTaskId, taskId, e.getMessage());
                s = "unknown";
            }

            if ("running".equals(s)) {
                hasRunning = true;
            } else if ("success".equals(s)) {
                hasSuccess = true;
            } else if ("failed".equals(s) || "killed".equals(s) || "timeout".equals(s)) {
                hasFailed = true;
            } else {
                hasPendingOrUnknown = true;
            }
        }

        int taskStatus;
        if (hasRunning) {
            taskStatus = 1;
        } else if (hasPendingOrUnknown && !hasSuccess && !hasFailed) {
            taskStatus = 2;
        } else if (hasSuccess && !hasFailed && !hasPendingOrUnknown) {
            taskStatus = 0;
        } else if (hasFailed && !hasSuccess && !hasPendingOrUnknown) {
            taskStatus = -1;
        } else if (hasPendingOrUnknown) {
            taskStatus = 1;
        } else {
            taskStatus = -1;
        }

        log.info("[UI-QUERY] managerTaskId={}, aggregated taskStatus={} (running={}, success={}, failed={}, pendingOrUnknown={})",
                managerTaskId, taskStatus, hasRunning, hasSuccess, hasFailed, hasPendingOrUnknown);

        return TaskStatusQueryResponse.builder()
                .code(200)
                .msg("success")
                .taskStatus(taskStatus)
                .build();
    }

    private void pollUiAndCallback(String managerTaskId, List<String> taskIds,
                                    Map<String, String> taskIdToCaseName, String callbackUrl) {
        log.info("[UI-POLL] managerTaskId={}, taskCount={}, taskIds={}, caseMapping={}",
                managerTaskId, taskIds.size(), taskIds, taskIdToCaseName);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                Math.min(taskIds.size(), 4));

        Map<String, UiTaskStatusResponse.UiTaskData> finalStatuses = new HashMap<>();
        Set<String> completed = Collections.synchronizedSet(new HashSet<>());
        int timeoutSeconds = pollingConfig.getTimeoutMinutes() * 60;
        int maxAttempts = timeoutSeconds / pollingConfig.getIntervalSeconds();
        log.info("[UI-POLL] managerTaskId={}, timeout={}min, interval={}s, maxAttempts={}",
                managerTaskId, pollingConfig.getTimeoutMinutes(), pollingConfig.getIntervalSeconds(), maxAttempts);

        for (String taskId : taskIds) {
            String caseName = taskIdToCaseName.get(taskId);
            CompletableFuture.runAsync(() -> {
                log.info("[UI-POLL] managerTaskId={}, taskId={}, caseName={} polling started",
                        managerTaskId, taskId, caseName);
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    try {
                        UiTaskStatusResponse statusResp = uiTaskStatusService.queryStatus(taskId);
                        UiTaskStatusResponse.UiTaskData data = statusResp != null ? statusResp.getData() : null;
                        String status = data != null ? data.getStatus() : "unknown";

                        managerTaskDao.updateTaskDetailStatus(managerTaskId, taskId, status);

                        if (isTerminalStatus(status)) {
                            finalStatuses.put(taskId, data);
                            completed.add(taskId);
                            log.info("[UI-POLL] managerTaskId={}, taskId={}, caseName={} reached terminal status={}, attempt={}",
                                    managerTaskId, taskId, caseName, status, attempt);
                            return;
                        }
                        if (attempt == 0 || attempt % 10 == 0) {
                            log.info("[UI-POLL] managerTaskId={}, taskId={}, caseName={} status={}, attempt={}/{}",
                                    managerTaskId, taskId, caseName, status, attempt, maxAttempts);
                        }
                        Thread.sleep(pollingConfig.getIntervalSeconds() * 1000L);
                    } catch (Exception e) {
                        log.warn("[UI-POLL] managerTaskId={}, taskId={}, caseName={} polling error at attempt={}: {}",
                                managerTaskId, taskId, caseName, attempt, e.getMessage());
                        try {
                            Thread.sleep(pollingConfig.getIntervalSeconds() * 1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("[UI-POLL] managerTaskId={}, taskId={}, caseName={} polling interrupted",
                                    managerTaskId, taskId, caseName);
                            return;
                        }
                    }
                }
                log.warn("[UI-POLL] managerTaskId={}, taskId={}, caseName={} polling TIMED OUT after {} attempts",
                        managerTaskId, taskId, caseName, maxAttempts);
                managerTaskDao.updateTaskDetailStatus(managerTaskId, taskId, "timeout");
                completed.add(taskId);
            }, scheduler).exceptionally(e -> {
                log.error("[UI-POLL] managerTaskId={}, taskId={}, caseName={} polling thread FAILED",
                        managerTaskId, taskId, caseName, e);
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
                log.warn("[UI-POLL] managerTaskId={} wait loop interrupted, completed={}/{}"
                        , managerTaskId, completed.size(), taskIds.size());
                break;
            }
            if (System.currentTimeMillis() >= nextProgressLogTime) {
                log.info("[UI-POLL] managerTaskId={} progress: {}/{} tasks completed, remaining={}",
                        managerTaskId, completed.size(), taskIds.size(),
                        taskIds.stream().filter(id -> !completed.contains(id)).collect(java.util.stream.Collectors.toList()));
                nextProgressLogTime = System.currentTimeMillis() + progressLogInterval;
            }
        }

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("[UI-POLL] managerTaskId={} scheduler did not terminate in 60s, forcing shutdown", managerTaskId);
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        boolean allSuccess = finalStatuses.values().stream()
                .allMatch(d -> d != null && "success".equals(d.getStatus()));
        String finalStatus = allSuccess ? "completed" : "failed";
        managerTaskDao.updateManagerTaskStatus(managerTaskId, finalStatus);

        log.info("[UI-POLL] managerTaskId={} all tasks done, finalStatus={}, detail:", managerTaskId, finalStatus);
        for (String taskId : taskIds) {
            UiTaskStatusResponse.UiTaskData data = finalStatuses.get(taskId);
            log.info("[UI-POLL] managerTaskId={}, taskId={}, caseName={}, status={}, reportUrl={}",
                    managerTaskId, taskId, taskIdToCaseName.get(taskId),
                    data != null ? data.getStatus() : "unknown",
                    data != null ? data.getReportUrl() : "null");
        }

        List<CallbackPayload.TaskResult> results = new ArrayList<>();
        for (String taskId : taskIds) {
            UiTaskStatusResponse.UiTaskData data = finalStatuses.get(taskId);
            results.add(CallbackPayload.TaskResult.builder()
                    .taskId(taskId)
                    .testCaseId(taskIdToCaseName.get(taskId))
                    .status(data != null ? data.getStatus() : "unknown")
                    .resultSummary(null)
                    .build());
        }

        CallbackPayload completionCallback = CallbackPayload.builder()
                .managerTaskId(managerTaskId)
                .phase(finalStatus)
                .tasks(results)
                .build();
        log.info("[UI-POLL] managerTaskId={} sending callback to {}, phase={}",
                managerTaskId, callbackUrl, finalStatus);
        callbackService.sendCallback(callbackUrl, completionCallback);

        try {
            uiReportService.generateReport(managerTaskId, taskIds, finalStatuses);
            log.info("[UI-POLL] managerTaskId={} UI report generated", managerTaskId);
        } catch (Exception e) {
            log.error("[UI-POLL] managerTaskId={} UI report generation FAILED", managerTaskId, e);
        }

        log.info("[UI-POLL] managerTaskId={} FULLY COMPLETED", managerTaskId);
    }

    private boolean isTerminalStatus(String status) {
        return "success".equals(status) || "failed".equals(status)
                || "killed".equals(status) || "timeout".equals(status);
    }

    /**
     * 构建测试集名称：MD标题中 "UI类" 前面的ID前缀 + managerTaskId
     * 例: "# HOLDING-OPT-02 UI类测试点" -> "HOLDING-OPT-02-uimtk_a1b2c3d4"
     */
    private String buildSuiteName(String mdTitle, String managerTaskId) {
        if (mdTitle == null || mdTitle.isEmpty()) {
            return managerTaskId;
        }
        // 去掉 # 前缀
        String title = mdTitle.replaceFirst("^#+\\s*", "").trim();
        // 提取 "UI类" 前面的部分
        int idx = title.indexOf("UI类");
        if (idx > 0) {
            return title.substring(0, idx).trim() + "-" + managerTaskId;
        }
        return managerTaskId;
    }
}
