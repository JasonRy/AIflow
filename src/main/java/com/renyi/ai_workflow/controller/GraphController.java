package com.renyi.ai_workflow.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renyi.ai_workflow.dto.AnalysisRunRequest;
import com.renyi.ai_workflow.dto.ExecutionHistoryResponse;
import com.renyi.ai_workflow.dto.WorkflowRunRequest;
import com.renyi.ai_workflow.entity.WorkflowExecutionEntity;
import com.renyi.ai_workflow.model.WorkflowResponse;
import com.renyi.ai_workflow.repository.WorkflowExecutionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin
public class GraphController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<Map<String, Object>>> CALL_TREE_TYPE = new TypeReference<>() {};

    private final CompiledGraph workflowGraph;
    private final CompiledGraph analysisGraph;
    private final WorkflowExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final String dashscopeApiKey;
    private final String deepseekApiKey;

    public GraphController(
            @Qualifier("myWorkflowGraph") CompiledGraph workflowGraph,
            @Qualifier("analysisGraph") CompiledGraph analysisGraph,
            WorkflowExecutionRepository executionRepository,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey,
            @Value("${deepseek.api-key:}") String deepseekApiKey) {
        this.workflowGraph = workflowGraph;
        this.analysisGraph = analysisGraph;
        this.executionRepository = executionRepository;
        this.objectMapper = objectMapper;
        this.dashscopeApiKey = dashscopeApiKey;
        this.deepseekApiKey = deepseekApiKey;
    }

    @PostMapping("/api/workflows/chat/run")
    public WorkflowResponse workflow(@Valid @RequestBody WorkflowRunRequest request) throws Exception {
        long start = System.currentTimeMillis();
        String model = defaultIfBlank(request.getModel(), "qwen-max");
        String provider = defaultIfBlank(request.getProvider(), "tongyi");
        validateProviderKey(provider);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", request.getMessage());
        inputs.put("model", model);
        inputs.put("provider", provider);
        inputs.put("enableSearch", String.valueOf(request.isEnableSearch()));
        inputs.put("answerMs", -1L);
        inputs.put("defaultMs", -1L);

        Optional<OverAllState> result = workflowGraph.invoke(inputs, RunnableConfig.builder().build());
        long costMs = System.currentTimeMillis() - start;

        String answer = result
                .map(s -> (String) s.value("output").orElse("无输出"))
                .orElse("工作流执行失败");
        String intent = result
                .map(s -> (String) s.value("intent").orElse("未知"))
                .orElse("未知");
        String nodeTimings = assembleTimings(result.orElse(null),
                "intentMs", "intent", "answerMs", "answer", "defaultMs", "default");
        List<Map<String, Object>> callTree = buildWorkflowCallTree(result.orElse(null));

        WorkflowResponse response = WorkflowResponse.builder()
                .answer(answer)
                .intent(intent)
                .model(model)
                .provider(provider)
                .nodeTimings(nodeTimings)
                .callTree(callTree)
                .costMs(costMs)
                .timestamp(LocalDateTime.now().format(FMT))
                .build();

        saveExecution("chat", request.getMessage(), request.isEnableSearch(), response);
        return response;
    }

    @PostMapping("/api/workflows/analysis/run")
    public WorkflowResponse analysis(@Valid @RequestBody AnalysisRunRequest request) throws Exception {
        long start = System.currentTimeMillis();
        String model = defaultIfBlank(request.getModel(), "qwen-max");
        String provider = defaultIfBlank(request.getProvider(), "tongyi");
        validateProviderKey(provider);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("data", request.getData());
        inputs.put("model", model);
        inputs.put("provider", provider);
        inputs.put("enableSearch", String.valueOf(request.isEnableSearch()));

        Optional<OverAllState> result = analysisGraph.invoke(inputs, RunnableConfig.builder().build());
        long costMs = System.currentTimeMillis() - start;

        String output = result
                .map(s -> (String) s.value("output").orElse("无输出"))
                .orElse("分析失败");
        String nodeTimings = assembleTimings(result.orElse(null),
                "typeMs", "type", "analysisMs", "analysis", "summaryMs", "summary");
        List<Map<String, Object>> callTree = buildAnalysisCallTree(result.orElse(null));

        WorkflowResponse response = WorkflowResponse.builder()
                .answer(output)
                .intent("数据分析")
                .model(model)
                .provider(provider)
                .nodeTimings(nodeTimings)
                .callTree(callTree)
                .costMs(costMs)
                .timestamp(LocalDateTime.now().format(FMT))
                .build();

        saveExecution("analysis", request.getData(), request.isEnableSearch(), response);
        return response;
    }

    @GetMapping({"/api/executions", "/history"})
    public List<ExecutionHistoryResponse> history() {
        return executionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private void saveExecution(String workflowType, String inputText, boolean enableSearch, WorkflowResponse response) {
        WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
        entity.setWorkflowType(workflowType);
        entity.setIntent(response.getIntent());
        entity.setModel(response.getModel());
        entity.setProvider(response.getProvider());
        entity.setEnableSearch(enableSearch);
        entity.setCostMs(response.getCostMs());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setInputText(inputText);
        entity.setAnswer(response.getAnswer());
        entity.setNodeTimings(response.getNodeTimings());
        entity.setCallTreeJson(toJson(response.getCallTree()));
        executionRepository.save(entity);
    }

    private WorkflowResponse toResponse(WorkflowExecutionEntity entity) {
        return WorkflowResponse.builder()
                .answer(entity.getAnswer())
                .intent(entity.getIntent())
                .model(entity.getModel())
                .provider(entity.getProvider())
                .nodeTimings(entity.getNodeTimings())
                .callTree(fromJson(entity.getCallTreeJson()))
                .costMs(entity.getCostMs())
                .timestamp(entity.getCreatedAt().format(FMT))
                .build();
    }

    private ExecutionHistoryResponse toHistoryResponse(WorkflowExecutionEntity entity) {
        return ExecutionHistoryResponse.builder()
                .id(entity.getId())
                .workflowType(entity.getWorkflowType())
                .userMessage(entity.getInputText())
                .assistantMessage(entity.getAnswer())
                .intent(entity.getIntent())
                .model(entity.getModel())
                .provider(entity.getProvider())
                .enableSearch(entity.isEnableSearch())
                .nodeTimings(entity.getNodeTimings())
                .costMs(entity.getCostMs())
                .timestamp(entity.getCreatedAt().format(FMT))
                .build();
    }

    private String toJson(List<Map<String, Object>> callTree) {
        try {
            return objectMapper.writeValueAsString(callTree == null ? List.of() : callTree);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("调用树序列化失败", ex);
        }
    }

    private List<Map<String, Object>> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Map<String, Object>> callTree = objectMapper.readValue(json, CALL_TREE_TYPE);
            return callTree.isEmpty() ? null : callTree;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> buildWorkflowCallTree(OverAllState s) {
        if (s == null) return null;
        List<Map<String, Object>> tree = new ArrayList<>();
        addNode(s, "intentMs", "intent", "分类", 0, tree);
        addNode(s, "answerMs", "answer", "生成", 1, tree);
        addNode(s, "defaultMs", "default", "拦截", 1, tree);
        return tree.isEmpty() ? null : tree;
    }

    private List<Map<String, Object>> buildAnalysisCallTree(OverAllState s) {
        if (s == null) return null;
        List<Map<String, Object>> tree = new ArrayList<>();
        addNode(s, "typeMs", "type", "分类", 0, tree);
        addNode(s, "analysisMs", "analysis", "分析", 1, tree);
        addNode(s, "summaryMs", "summary", "汇总", 2, tree);
        return tree.isEmpty() ? null : tree;
    }

    private void addNode(OverAllState s, String msKey, String nodeId, String nodeType,
                         int depth, List<Map<String, Object>> tree) {
        s.value(msKey).ifPresent(v -> {
            long ms = ((Number) v).longValue();
            if (ms > 0) {
                tree.add(Map.<String, Object>of(
                        "nodeId", nodeId,
                        "nodeType", nodeType,
                        "costMs", ms,
                        "depth", depth,
                        "status", "success"
                ));
            }
        });
    }

    private String assembleTimings(OverAllState s, String... msKeyLabelPairs) {
        if (s == null) return "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i + 1 < msKeyLabelPairs.length; i += 2) {
            String key = msKeyLabelPairs[i];
            String label = msKeyLabelPairs[i + 1];
            s.value(key).ifPresent(v -> {
                long ms = ((Number) v).longValue();
                if (ms > 0) parts.add(label + "=" + ms + "ms");
            });
        }
        return String.join(" | ", parts);
    }

    private void validateProviderKey(String provider) {
        if ("deepseek".equals(provider)) {
            if (isPlaceholderKey(deepseekApiKey, "your-deepseek-api-key-here")) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "DeepSeek API Key 未配置，请设置 DEEPSEEK_API_KEY 后重启服务");
            }
            return;
        }
        if (isPlaceholderKey(dashscopeApiKey, "your-dashscope-api-key-here")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "通义千问 API Key 未配置，请设置 DASHSCOPE_API_KEY 后重启服务");
        }
    }

    private boolean isPlaceholderKey(String value, String placeholder) {
        return value == null || value.isBlank() || placeholder.equals(value);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}


