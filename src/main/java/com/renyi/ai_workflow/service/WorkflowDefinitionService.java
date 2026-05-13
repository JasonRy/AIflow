package com.renyi.ai_workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renyi.ai_workflow.dto.AppResponse;
import com.renyi.ai_workflow.dto.CreateAppRequest;
import com.renyi.ai_workflow.dto.CreateWorkflowRequest;
import com.renyi.ai_workflow.dto.SaveWorkflowDraftRequest;
import com.renyi.ai_workflow.dto.WorkflowDefinitionResponse;
import com.renyi.ai_workflow.entity.AiAppEntity;
import com.renyi.ai_workflow.entity.WorkflowDefinitionEntity;
import com.renyi.ai_workflow.entity.WorkflowEdgeEntity;
import com.renyi.ai_workflow.entity.WorkflowNodeEntity;
import com.renyi.ai_workflow.entity.WorkflowVersionEntity;
import com.renyi.ai_workflow.repository.AiAppRepository;
import com.renyi.ai_workflow.repository.WorkflowDefinitionRepository;
import com.renyi.ai_workflow.repository.WorkflowEdgeRepository;
import com.renyi.ai_workflow.repository.WorkflowNodeRepository;
import com.renyi.ai_workflow.repository.WorkflowVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowDefinitionService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AiAppRepository appRepository;
    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowEdgeRepository edgeRepository;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionService(
            AiAppRepository appRepository,
            WorkflowDefinitionRepository workflowRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowEdgeRepository edgeRepository,
            ObjectMapper objectMapper) {
        this.appRepository = appRepository;
        this.workflowRepository = workflowRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AppResponse createApp(CreateAppRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AiAppEntity app = new AiAppEntity();
        app.setName(request.getName().trim());
        app.setDescription(blankToNull(request.getDescription()));
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        return toAppResponse(appRepository.save(app));
    }

    @Transactional(readOnly = true)
    public List<AppResponse> listApps() {
        return appRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toAppResponse)
                .toList();
    }

    @Transactional
    public WorkflowDefinitionResponse createWorkflow(CreateWorkflowRequest request) {
        AiAppEntity app = appRepository.findById(request.getAppId())
                .orElseThrow(() -> notFound("应用不存在: " + request.getAppId()));
        LocalDateTime now = LocalDateTime.now();

        WorkflowDefinitionEntity workflow = new WorkflowDefinitionEntity();
        workflow.setApp(app);
        workflow.setName(request.getName().trim());
        workflow.setDescription(blankToNull(request.getDescription()));
        workflow.setStatus("draft");
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        workflow = workflowRepository.save(workflow);

        WorkflowVersionEntity version = new WorkflowVersionEntity();
        version.setWorkflow(workflow);
        version.setVersionNumber(1);
        version.setStatus("draft");
        version.setDefinitionJson(defaultDefinitionJson());
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version = versionRepository.save(version);

        saveNodes(version, defaultNodes());
        saveEdges(version, List.of());
        return toWorkflowResponse(workflow, version);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> listWorkflows() {
        return workflowRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(workflow -> toWorkflowResponse(workflow, latestVersion(workflow)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse getWorkflow(Long workflowId) {
        WorkflowDefinitionEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("工作流不存在: " + workflowId));
        return toWorkflowResponse(workflow, latestVersion(workflow));
    }

    @Transactional
    public WorkflowDefinitionResponse saveDraft(Long workflowId, SaveWorkflowDraftRequest request) {
        WorkflowDefinitionEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("工作流不存在: " + workflowId));
        WorkflowVersionEntity version = latestVersion(workflow);
        if (!"draft".equals(version.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前版本不是草稿，暂不能直接编辑");
        }

        validateDraft(request);
        nodeRepository.deleteAllByVersion(version);
        edgeRepository.deleteAllByVersion(version);
        saveNodes(version, request.getNodes());
        saveEdges(version, request.getEdges());
        version.setDefinitionJson(toJson(Map.of(
                "nodes", request.getNodes(),
                "edges", request.getEdges(),
                "variables", request.getVariables() == null ? Map.of() : request.getVariables()
        )));
        version.setUpdatedAt(LocalDateTime.now());
        versionRepository.save(version);

        workflow.setUpdatedAt(LocalDateTime.now());
        workflowRepository.save(workflow);
        return toWorkflowResponse(workflow, version);
    }

    private void validateDraft(SaveWorkflowDraftRequest request) {
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流至少需要一个节点");
        }
        List<String> nodeKeys = request.getNodes().stream()
                .map(SaveWorkflowDraftRequest.NodeSpec::getKey)
                .toList();
        for (SaveWorkflowDraftRequest.EdgeSpec edge : request.getEdges()) {
            if (!nodeKeys.contains(edge.getSource()) || !nodeKeys.contains(edge.getTarget())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "连线引用了不存在的节点");
            }
        }
    }

    private WorkflowVersionEntity latestVersion(WorkflowDefinitionEntity workflow) {
        return versionRepository.findTopByWorkflowOrderByVersionNumberDesc(workflow)
                .orElseThrow(() -> notFound("工作流版本不存在: " + workflow.getId()));
    }

    private void saveNodes(WorkflowVersionEntity version, List<SaveWorkflowDraftRequest.NodeSpec> nodes) {
        List<WorkflowNodeEntity> entities = new ArrayList<>();
        for (SaveWorkflowDraftRequest.NodeSpec node : nodes) {
            WorkflowNodeEntity entity = new WorkflowNodeEntity();
            entity.setVersion(version);
            entity.setNodeKey(node.getKey());
            entity.setNodeType(node.getType());
            entity.setTitle(node.getTitle());
            entity.setPositionX(node.getX());
            entity.setPositionY(node.getY());
            entity.setConfigJson(toJson(node.getConfig() == null ? Map.of() : node.getConfig()));
            entities.add(entity);
        }
        nodeRepository.saveAll(entities);
    }

    private void saveEdges(WorkflowVersionEntity version, List<SaveWorkflowDraftRequest.EdgeSpec> edges) {
        List<WorkflowEdgeEntity> entities = new ArrayList<>();
        for (SaveWorkflowDraftRequest.EdgeSpec edge : edges) {
            WorkflowEdgeEntity entity = new WorkflowEdgeEntity();
            entity.setVersion(version);
            entity.setSourceNodeKey(edge.getSource());
            entity.setTargetNodeKey(edge.getTarget());
            entity.setSourceHandle(blankToNull(edge.getSourceHandle()));
            entity.setTargetHandle(blankToNull(edge.getTargetHandle()));
            entity.setConditionJson(toJson(edge.getCondition() == null ? Map.of() : edge.getCondition()));
            entities.add(entity);
        }
        edgeRepository.saveAll(entities);
    }

    private WorkflowDefinitionResponse toWorkflowResponse(WorkflowDefinitionEntity workflow, WorkflowVersionEntity version) {
        return WorkflowDefinitionResponse.builder()
                .id(workflow.getId())
                .appId(workflow.getApp().getId())
                .appName(workflow.getApp().getName())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .status(workflow.getStatus())
                .versionNumber(version.getVersionNumber())
                .versionStatus(version.getStatus())
                .nodes(nodeRepository.findAllByVersionOrderByIdAsc(version).stream().map(this::toNodeResponse).toList())
                .edges(edgeRepository.findAllByVersionOrderByIdAsc(version).stream().map(this::toEdgeResponse).toList())
                .variables(readVariables(version.getDefinitionJson()))
                .createdAt(workflow.getCreatedAt().format(FMT))
                .updatedAt(workflow.getUpdatedAt().format(FMT))
                .build();
    }

    private WorkflowDefinitionResponse.NodeResponse toNodeResponse(WorkflowNodeEntity node) {
        return WorkflowDefinitionResponse.NodeResponse.builder()
                .key(node.getNodeKey())
                .type(node.getNodeType())
                .title(node.getTitle())
                .x(node.getPositionX())
                .y(node.getPositionY())
                .config(fromJson(node.getConfigJson()))
                .build();
    }

    private WorkflowDefinitionResponse.EdgeResponse toEdgeResponse(WorkflowEdgeEntity edge) {
        return WorkflowDefinitionResponse.EdgeResponse.builder()
                .source(edge.getSourceNodeKey())
                .target(edge.getTargetNodeKey())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .condition(fromJson(edge.getConditionJson()))
                .build();
    }

    private AppResponse toAppResponse(AiAppEntity app) {
        return AppResponse.builder()
                .id(app.getId())
                .name(app.getName())
                .description(app.getDescription())
                .createdAt(app.getCreatedAt().format(FMT))
                .updatedAt(app.getUpdatedAt().format(FMT))
                .build();
    }

    private List<SaveWorkflowDraftRequest.NodeSpec> defaultNodes() {
        SaveWorkflowDraftRequest.NodeSpec start = new SaveWorkflowDraftRequest.NodeSpec();
        start.setKey("start_001");
        start.setType("start");
        start.setTitle("开始");
        start.setX(80);
        start.setY(180);
        start.setConfig(Map.of("inputKey", "query"));
        return List.of(start);
    }

    private String defaultDefinitionJson() {
        return toJson(Map.of("nodes", defaultNodes(), "edges", List.of(), "variables", Map.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readVariables(String definitionJson) {
        Object variables = fromJson(definitionJson).get("variables");
        return variables instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("工作流定义序列化失败", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
