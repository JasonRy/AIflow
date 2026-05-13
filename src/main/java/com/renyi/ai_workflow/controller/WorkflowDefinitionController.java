package com.renyi.ai_workflow.controller;

import com.renyi.ai_workflow.dto.AppResponse;
import com.renyi.ai_workflow.dto.CreateAppRequest;
import com.renyi.ai_workflow.dto.CreateWorkflowRequest;
import com.renyi.ai_workflow.dto.SaveWorkflowDraftRequest;
import com.renyi.ai_workflow.dto.WorkflowDefinitionResponse;
import com.renyi.ai_workflow.service.WorkflowDefinitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @PostMapping("/apps")
    public AppResponse createApp(@Valid @RequestBody CreateAppRequest request) {
        return service.createApp(request);
    }

    @GetMapping("/apps")
    public List<AppResponse> listApps() {
        return service.listApps();
    }

    @PostMapping("/workflow-definitions")
    public WorkflowDefinitionResponse createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        return service.createWorkflow(request);
    }

    @GetMapping("/workflow-definitions")
    public List<WorkflowDefinitionResponse> listWorkflows() {
        return service.listWorkflows();
    }

    @GetMapping("/workflow-definitions/{workflowId}")
    public WorkflowDefinitionResponse getWorkflow(@PathVariable Long workflowId) {
        return service.getWorkflow(workflowId);
    }

    @PutMapping("/workflow-definitions/{workflowId}/draft")
    public WorkflowDefinitionResponse saveDraft(
            @PathVariable Long workflowId,
            @Valid @RequestBody SaveWorkflowDraftRequest request) {
        return service.saveDraft(workflowId, request);
    }
}
