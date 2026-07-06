package com.devapo.operaton_demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricActivityInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessController.class);

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    public ProcessController(RuntimeService runtimeService,
                             TaskService taskService,
                             HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
    }

    // ---------------------------------------------------------------------
    // Processes
    // ---------------------------------------------------------------------

    @PostMapping("/start/{processDefinitionKey}")
    public ResponseEntity<Map<String, Object>> startProcess(
            @PathVariable String processDefinitionKey,
            @RequestBody(required = false) Map<String, Object> variables) {

        LOGGER.info("Starting process '{}' with variables: {}", processDefinitionKey, variables);

        Map<String, Object> vars = variables != null ? variables : new HashMap<>();
        ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(processDefinitionKey, vars);

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", instance.getId());
        response.put("processDefinitionId", instance.getProcessDefinitionId());
        response.put("businessKey", instance.getBusinessKey());
        response.put("ended", instance.isEnded());
        response.put("variables", vars);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/instance/{processInstanceId}")
    public ResponseEntity<Map<String, Object>> getInstance(@PathVariable String processInstanceId) {
        LOGGER.info("Fetching process instance '{}'", processInstanceId);

        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (instance == null) {
            return notFound("Process instance not found: " + processInstanceId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", instance.getId());
        response.put("processDefinitionId", instance.getProcessDefinitionId());
        response.put("businessKey", instance.getBusinessKey());
        response.put("suspended", instance.isSuspended());
        response.put("ended", instance.isEnded());
        response.put("variables", runtimeService.getVariables(processInstanceId));

        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    // User tasks
    // ---------------------------------------------------------------------

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> getTasks(
            @RequestParam(defaultValue = "demo") String assignee) {

        LOGGER.info("Fetching tasks for assignee '{}'", assignee);

        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc()
                .list();

        List<Map<String, Object>> response = new ArrayList<>();
        for (Task task : tasks) {
            response.add(taskToMap(task));
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String taskId) {
        LOGGER.info("Fetching task '{}'", taskId);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return notFound("Task not found: " + taskId);
        }

        Map<String, Object> response = taskToMap(task);
        response.put("variables", taskService.getVariables(taskId));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/task/{taskId}/complete")
    public ResponseEntity<Map<String, Object>> completeTask(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> variables) {

        LOGGER.info("Completing task '{}' with variables: {}", taskId, variables);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return notFound("Task not found: " + taskId);
        }

        Map<String, Object> vars = variables != null ? variables : new HashMap<>();
        taskService.complete(taskId, vars);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("completed", true);
        response.put("variables", vars);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/task/{taskId}/claim")
    public ResponseEntity<Map<String, Object>> claimTask(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "demo") String userId) {

        LOGGER.info("Claiming task '{}' for user '{}'", taskId, userId);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return notFound("Task not found: " + taskId);
        }

        taskService.claim(taskId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("assignee", userId);
        response.put("claimed", true);

        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    // History
    // ---------------------------------------------------------------------

    @GetMapping("/history/{processInstanceId}")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @PathVariable String processInstanceId) {

        LOGGER.info("Fetching history for process instance '{}'", processInstanceId);

        List<HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<Map<String, Object>> response = new ArrayList<>();
        for (HistoricActivityInstance activity : activities) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("activityId", activity.getActivityId());
            entry.put("activityName", activity.getActivityName());
            entry.put("activityType", activity.getActivityType());
            entry.put("assignee", activity.getAssignee());
            entry.put("startTime", activity.getStartTime());
            entry.put("endTime", activity.getEndTime());
            entry.put("durationInMillis", activity.getDurationInMillis());
            response.add(entry);
        }

        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("assignee", task.getAssignee());
        map.put("created", task.getCreateTime());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("processDefinitionId", task.getProcessDefinitionId());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        return map;
    }

    private <T> ResponseEntity<T> notFound(String message) {
        LOGGER.warn(message);
        return ResponseEntity.notFound().build();
    }
}
