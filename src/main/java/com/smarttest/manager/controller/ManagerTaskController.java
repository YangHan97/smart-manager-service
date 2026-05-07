package com.smarttest.manager.controller;

import com.smarttest.manager.dto.*;
import com.smarttest.manager.service.ManagerTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerTaskController {

    private final ManagerTaskService managerTaskService;

    @PostMapping("/tasks/create")
    public ResponseEntity<ManagerTaskResponse> createTasks(@RequestBody @Valid ManagerTaskRequest request) {
        ManagerTaskResponse response = managerTaskService.dispatch(request);
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/tasks/status")
    public ResponseEntity<TaskStatusQueryResponse> queryStatus(@RequestBody @Valid TaskStatusQueryRequest request) {
        TaskStatusQueryResponse response = managerTaskService.queryManagerTaskStatus(request.getTaskId());
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/ui-tasks/create")
    public ResponseEntity<UiManagerTaskResponse> createUiTasks(@RequestBody @Valid UiManagerTaskRequest request) {
        UiManagerTaskResponse response = managerTaskService.dispatchUi(request);
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/ui-tasks/status")
    public ResponseEntity<TaskStatusQueryResponse> queryUiStatus(@RequestBody @Valid TaskStatusQueryRequest request) {
        TaskStatusQueryResponse response = managerTaskService.queryUiManagerTaskStatus(request.getTaskId());
        return ResponseEntity.ok().body(response);
    }
}
