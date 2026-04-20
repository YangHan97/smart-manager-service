package com.smarttest.manager.controller;

import com.smarttest.manager.dto.ManagerTaskRequest;
import com.smarttest.manager.dto.ManagerTaskResponse;
import com.smarttest.manager.dto.TaskStatusQueryRequest;
import com.smarttest.manager.dto.TaskStatusQueryResponse;
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
}
