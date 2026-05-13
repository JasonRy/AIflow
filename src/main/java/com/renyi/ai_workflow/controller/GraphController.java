package com.renyi.ai_workflow.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.renyi.ai_workflow.model.WorkflowResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin
public class GraphController {

    private final CompiledGraph workflowGraph;
    private final CompiledGraph analysisGraph;
    private final List<WorkflowResponse> history = new ArrayList<>();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public GraphController(
            @Qualifier("myWorkflowGraph") CompiledGraph workflowGraph,
            @Qualifier("analysisGraph")   CompiledGraph analysisGraph) {
        this.workflowGraph = workflowGraph;
        this.analysisGraph = analysisGraph;
    }

    @GetMapping("/workflow")
    public WorkflowResponse workflow(
            @RequestParam String message,
            @RequestParam(defaultValue = "qwen-max") String model,
            @RequestParam(defaultValue = "tongyi")   String provider,
            @RequestParam(defaultValue = "false")    boolean enableSearch) throws Exception {

        long start = System.currentTimeMillis();

        // Reset branching timing keys to -1 so stale values from prior requests don't pollute callTree
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input",        message);
        inputs.put("model",        model);
        inputs.put("provider",     provider);
        inputs.put("enableSearch", String.valueOf(enableSearch));
        inputs.put("answerMs",     -1L);
        inputs.put("defaultMs",    -1L);

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

        WorkflowResponse response = WorkflowResponse.builder()
                .answer(answer)
                .intent(intent)
                .model(model)
                .provider(provider)
                .nodeTimings(nodeTimings)
                .callTree(buildWorkflowCallTree(result.orElse(null)))
                .costMs(costMs)
                .timestamp(LocalDateTime.now().format(FMT))
                .build();

        history.add(0, response);
        return response;
    }

    @GetMapping("/analysis")
    public WorkflowResponse analysis(
            @RequestParam String data,
            @RequestParam(defaultValue = "qwen-max") String model,
            @RequestParam(defaultValue = "tongyi")   String provider,
            @RequestParam(defaultValue = "false")    boolean enableSearch) throws Exception {

        long start = System.currentTimeMillis();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("data",         data);
        inputs.put("model",        model);
        inputs.put("provider",     provider);
        inputs.put("enableSearch", String.valueOf(enableSearch));

        Optional<OverAllState> result = analysisGraph.invoke(inputs, RunnableConfig.builder().build());
        long costMs = System.currentTimeMillis() - start;

        String output = result
                .map(s -> (String) s.value("output").orElse("无输出"))
                .orElse("分析失败");

        String nodeTimings = assembleTimings(result.orElse(null),
                "typeMs", "type", "analysisMs", "analysis", "summaryMs", "summary");

        WorkflowResponse response = WorkflowResponse.builder()
                .answer(output)
                .intent("数据分析")
                .model(model)
                .provider(provider)
                .nodeTimings(nodeTimings)
                .callTree(buildAnalysisCallTree(result.orElse(null)))
                .costMs(costMs)
                .timestamp(LocalDateTime.now().format(FMT))
                .build();

        history.add(0, response);
        return response;
    }

    @GetMapping("/history")
    public List<WorkflowResponse> history() {
        return Collections.unmodifiableList(history);
    }

    // Builds callTree from per-node timing keys; only includes nodes where ms > 0.
    // answerMs/defaultMs are reset to -1 per request so stale branch values are excluded.
    private List<Map<String, Object>> buildWorkflowCallTree(OverAllState s) {
        if (s == null) return null;
        List<Map<String, Object>> tree = new ArrayList<>();
        addNode(s, "intentMs",  "intent",  "分类", 0, tree);
        addNode(s, "answerMs",  "answer",  "生成", 1, tree);
        addNode(s, "defaultMs", "default", "拦截", 1, tree);
        return tree.isEmpty() ? null : tree;
    }

    private List<Map<String, Object>> buildAnalysisCallTree(OverAllState s) {
        if (s == null) return null;
        List<Map<String, Object>> tree = new ArrayList<>();
        addNode(s, "typeMs",     "type",     "分类", 0, tree);
        addNode(s, "analysisMs", "analysis", "分析", 1, tree);
        addNode(s, "summaryMs",  "summary",  "汇总", 2, tree);
        return tree.isEmpty() ? null : tree;
    }

    private void addNode(OverAllState s, String msKey, String nodeId, String nodeType,
                         int depth, List<Map<String, Object>> tree) {
        s.value(msKey).ifPresent(v -> {
            long ms = ((Number) v).longValue();
            if (ms > 0) {
                tree.add(Map.<String, Object>of(
                        "nodeId",   nodeId,
                        "nodeType", nodeType,
                        "costMs",   ms,
                        "depth",    depth,
                        "status",   "success"
                ));
            }
        });
    }

    private String assembleTimings(OverAllState s, String... msKeyLabelPairs) {
        if (s == null) return "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i + 1 < msKeyLabelPairs.length; i += 2) {
            String key   = msKeyLabelPairs[i];
            String label = msKeyLabelPairs[i + 1];
            s.value(key).ifPresent(v -> {
                long ms = ((Number) v).longValue();
                if (ms > 0) parts.add(label + "=" + ms + "ms");
            });
        }
        return String.join(" | ", parts);
    }
}
