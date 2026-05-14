package com.renyi.ai_workflow.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renyi.ai_workflow.dto.WorkflowDefinitionRunRequest;
import com.renyi.ai_workflow.entity.WorkflowDefinitionEntity;
import com.renyi.ai_workflow.entity.WorkflowEdgeEntity;
import com.renyi.ai_workflow.entity.WorkflowExecutionEntity;
import com.renyi.ai_workflow.entity.WorkflowNodeEntity;
import com.renyi.ai_workflow.entity.WorkflowVersionEntity;
import com.renyi.ai_workflow.model.WorkflowResponse;
import com.renyi.ai_workflow.repository.WorkflowDefinitionRepository;
import com.renyi.ai_workflow.repository.WorkflowEdgeRepository;
import com.renyi.ai_workflow.repository.WorkflowExecutionRepository;
import com.renyi.ai_workflow.repository.WorkflowNodeRepository;
import com.renyi.ai_workflow.repository.WorkflowVersionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkflowRuntimeService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowEdgeRepository edgeRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient dashscopeClient;
    private final ChatClient deepseekClient;
    private final String dashscopeApiKey;
    private final String deepseekApiKey;

    public WorkflowRuntimeService(
            WorkflowDefinitionRepository workflowRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowEdgeRepository edgeRepository,
            WorkflowExecutionRepository executionRepository,
            ObjectMapper objectMapper,
            @Qualifier("dashscopeChatClient") ChatClient dashscopeClient,
            @Qualifier("deepseekChatClient") ChatClient deepseekClient,
            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey,
            @Value("${deepseek.api-key:}") String deepseekApiKey) {
        this.workflowRepository = workflowRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.executionRepository = executionRepository;
        this.objectMapper = objectMapper;
        this.dashscopeClient = dashscopeClient;
        this.deepseekClient = deepseekClient;
        this.dashscopeApiKey = dashscopeApiKey;
        this.deepseekApiKey = deepseekApiKey;
    }

    @Transactional
    public WorkflowResponse run(Long workflowId, WorkflowDefinitionRunRequest request) {
        WorkflowDefinitionEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("工作流不存在: " + workflowId));
        WorkflowVersionEntity version = versionRepository.findTopByWorkflowOrderByVersionNumberDesc(workflow)
                .orElseThrow(() -> notFound("工作流版本不存在: " + workflowId));

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByVersionOrderByIdAsc(version);
        List<WorkflowEdgeEntity> edges = edgeRepository.findAllByVersionOrderByIdAsc(version);
        Map<String, WorkflowNodeEntity> nodeByKey = new HashMap<>();
        for (WorkflowNodeEntity node : nodes) {
            nodeByKey.put(node.getNodeKey(), node);
        }

        WorkflowNodeEntity startNode = nodes.stream()
                .filter(node -> "start".equals(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> badRequest("工作流缺少 start 节点"));

        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.putIfAbsent("query", request.getMessage());
        variables.putIfAbsent("input", request.getMessage());

        long startedAt = System.currentTimeMillis();
        List<Map<String, Object>> callTree = new ArrayList<>();
        List<String> timingParts = new ArrayList<>();
        String currentKey = startNode.getNodeKey();
        String output = request.getMessage();
        Set<String> visited = new HashSet<>();

        while (currentKey != null) {
            if (!visited.add(currentKey)) {
                throw badRequest("工作流存在循环，暂不支持循环运行");
            }
            WorkflowNodeEntity node = nodeByKey.get(currentKey);
            if (node == null) {
                throw badRequest("连线引用了不存在的节点: " + currentKey);
            }

            Map<String, Object> config = fromJson(node.getConfigJson());
            long nodeStart = System.currentTimeMillis();
            String nodeOutput = executeNode(node, config, variables, request);
            long nodeMs = System.currentTimeMillis() - nodeStart;
            if (nodeMs == 0) nodeMs = 1;

            variables.put(node.getNodeKey(), nodeOutput);
            if ("llm".equals(node.getNodeType())) {
                output = nodeOutput;
            }
            if ("end".equals(node.getNodeType())) {
                output = valueAsString(variables.getOrDefault(valueAsString(config.get("outputKey"), "answer"), output), "");
            }

            timingParts.add(node.getNodeKey() + "=" + nodeMs + "ms");
            callTree.add(Map.<String, Object>of(
                    "nodeId", node.getNodeKey(),
                    "nodeType", node.getNodeType(),
                    "costMs", nodeMs,
                    "depth", Math.max(0, visited.size() - 1),
                    "status", "success"
            ));

            currentKey = nextNodeKey(currentKey, edges, nodeOutput);
        }

        String provider = resolveProvider(request.getProvider(), null);
        String model = defaultIfBlank(request.getModel(), "qwen-max");
        long costMs = System.currentTimeMillis() - startedAt;
        WorkflowResponse response = WorkflowResponse.builder()
                .answer(output)
                .intent(workflow.getName())
                .model(model)
                .provider(provider)
                .nodeTimings(String.join(" | ", timingParts))
                .callTree(callTree)
                .costMs(costMs)
                .timestamp(LocalDateTime.now().format(FMT))
                .build();
        saveExecution("definition", "#" + workflow.getId() + " " + request.getMessage(), request.isEnableSearch(), response);
        return response;
    }

    private String executeNode(
            WorkflowNodeEntity node,
            Map<String, Object> config,
            Map<String, Object> variables,
            WorkflowDefinitionRunRequest request) {
        return switch (node.getNodeType()) {
            case "start" -> request.getMessage();
            case "llm" -> runLlmNode(config, variables, request);
            case "condition" -> runConditionNode(config, variables);
            case "set_variable", "setvariable" -> runSetVariableNode(config, variables);
            case "end" -> valueAsString(variables.getOrDefault("answer", variables.getOrDefault("query", "")), "");
            default -> throw badRequest("暂不支持节点类型: " + node.getNodeType());
        };
    }

    private String runLlmNode(Map<String, Object> config, Map<String, Object> variables, WorkflowDefinitionRunRequest request) {
        String provider = resolveProvider(request.getProvider(), valueAsString(config.get("provider"), null));
        String model = defaultIfBlank(valueAsString(config.get("model"), request.getModel()), "qwen-max");
        validateProviderKey(provider);

        String promptTemplate = valueAsString(config.get("prompt"), "{{query}}");
        String prompt = renderTemplate(promptTemplate, variables);
        boolean enableSearch = Boolean.parseBoolean(valueAsString(config.getOrDefault("enableSearch", request.isEnableSearch()), "false"));

        String answer = "deepseek".equals(provider)
                ? deepseekClient.prompt().user(prompt).options(OpenAiChatOptions.builder().model(model).build()).call().content()
                : dashscopeClient.prompt().user(prompt).options(DashScopeChatOptions.builder()
                        .withModel(model)
                        .withEnableSearch(enableSearch)
                        .build()).call().content();
        variables.put("answer", answer);
        return answer;
    }

    @SuppressWarnings("unchecked")
    private String runSetVariableNode(Map<String, Object> config, Map<String, Object> variables) {
        Object assignments = config.get("assignments");
        if (assignments instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> assignment) {
                    String name = valueAsString(firstPresent(assignment, "name", "key", "variable"), "").trim();
                    if (!name.isBlank()) {
                        Object rawValue = firstPresent(assignment, "value", "template");
                        String value = renderTemplate(valueAsString(rawValue, ""), variables);
                        variables.put(name, value);
                        names.add(name);
                    }
                }
            }
            return String.join(",", names);
        }

        Object values = config.get("variables");
        if (values instanceof Map<?, ?> map) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = valueAsString(entry.getKey(), "").trim();
                if (!name.isBlank()) {
                    String value = renderTemplate(valueAsString(entry.getValue(), ""), variables);
                    variables.put(name, value);
                    names.add(name);
                }
            }
            return String.join(",", names);
        }
        throw badRequest("set_variable 节点缺少 assignments 或 variables 配置");
    }

    private String runConditionNode(Map<String, Object> config, Map<String, Object> variables) {
        String variable = valueAsString(firstPresent(config, "variable", "key", "left"), "query");
        String operator = valueAsString(config.get("operator"), "not_empty");
        String expected = renderTemplate(valueAsString(config.get("value"), ""), variables);
        String actual = valueAsString(variables.get(variable), "");
        return evaluateCondition(actual, operator, expected) ? "true" : "false";
    }

    private boolean evaluateCondition(String actual, String operator, String expected) {
        return switch (operator) {
            case "equals", "eq", "==" -> Objects.equals(actual, expected);
            case "not_equals", "ne", "!=" -> !Objects.equals(actual, expected);
            case "contains" -> actual.contains(expected);
            case "not_contains" -> !actual.contains(expected);
            case "starts_with" -> actual.startsWith(expected);
            case "ends_with" -> actual.endsWith(expected);
            case "empty" -> actual.isBlank();
            case "not_empty" -> !actual.isBlank();
            default -> throw badRequest("暂不支持条件操作符: " + operator);
        };
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", valueAsString(entry.getValue(), ""));
        }
        return rendered;
    }

    private String nextNodeKey(String currentKey, List<WorkflowEdgeEntity> edges, String sourceHandle) {
        List<WorkflowEdgeEntity> outgoing = edges.stream()
                .filter(edge -> currentKey.equals(edge.getSourceNodeKey()))
                .toList();
        if (outgoing.isEmpty()) {
            return null;
        }
        if (sourceHandle != null && !sourceHandle.isBlank()) {
            return outgoing.stream()
                    .filter(edge -> sourceHandle.equals(edge.getSourceHandle()))
                    .map(WorkflowEdgeEntity::getTargetNodeKey)
                    .findFirst()
                    .orElseGet(() -> outgoing.stream()
                            .filter(edge -> matchesEdgeCondition(edge, sourceHandle))
                            .map(WorkflowEdgeEntity::getTargetNodeKey)
                            .findFirst()
                            .orElse(outgoing.getFirst().getTargetNodeKey()));
        }
        return outgoing.stream()
                .filter(edge -> edge.getSourceHandle() == null || edge.getSourceHandle().isBlank())
                .map(WorkflowEdgeEntity::getTargetNodeKey)
                .findFirst()
                .orElse(outgoing.getFirst().getTargetNodeKey());
    }

    private boolean matchesEdgeCondition(WorkflowEdgeEntity edge, String sourceHandle) {
        Map<String, Object> condition = fromJson(edge.getConditionJson());
        String value = valueAsString(firstPresent(condition, "handle", "branch", "value"), "");
        return sourceHandle.equals(value);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("执行记录序列化失败", ex);
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

    private String resolveProvider(String requestProvider, String configProvider) {
        return defaultIfBlank(configProvider, defaultIfBlank(requestProvider, "tongyi"));
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

    private String valueAsString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
