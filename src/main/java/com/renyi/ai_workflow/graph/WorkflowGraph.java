package com.renyi.ai_workflow.graph;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Configuration
public class WorkflowGraph {

    private final ChatClient dashscopeClient;
    private final ChatClient deepseekClient;

    public WorkflowGraph(
            @Qualifier("dashscopeChatClient") ChatClient dashscopeClient,
            @Qualifier("deepseekChatClient")  ChatClient deepseekClient) {
        this.dashscopeClient = dashscopeClient;
        this.deepseekClient  = deepseekClient;
    }

    private ChatClient client(String provider) {
        return "deepseek".equals(provider) ? deepseekClient : dashscopeClient;
    }

    private DashScopeChatOptions tongyiOptions(String model, boolean enableSearch) {
        return DashScopeChatOptions.builder()
                .withModel(model)
                .withEnableSearch(enableSearch)
                .build();
    }

    private OpenAiChatOptions deepseekOptions(String model) {
        return OpenAiChatOptions.builder()
                .model(model)
                .build();
    }

    // ── Factories ──

    @Bean("workflowStateFactory")
    public OverAllStateFactory workflowStateFactory() {
        return () -> OverAllStateBuilder.builder()
                .withKeyStrategy("input",        new ReplaceStrategy())
                .withKeyStrategy("intent",       new ReplaceStrategy())
                .withKeyStrategy("output",       new ReplaceStrategy())
                .withKeyStrategy("model",        new ReplaceStrategy())
                .withKeyStrategy("provider",     new ReplaceStrategy())
                .withKeyStrategy("enableSearch", new ReplaceStrategy())
                .withKeyStrategy("intentMs",     new ReplaceStrategy())
                .withKeyStrategy("answerMs",     new ReplaceStrategy())
                .withKeyStrategy("defaultMs",    new ReplaceStrategy())
                .build();
    }

    @Bean("analysisStateFactory")
    public OverAllStateFactory analysisStateFactory() {
        return () -> OverAllStateBuilder.builder()
                .withKeyStrategy("data",           new ReplaceStrategy())
                .withKeyStrategy("dataType",       new ReplaceStrategy())
                .withKeyStrategy("analysisResult", new ReplaceStrategy())
                .withKeyStrategy("output",         new ReplaceStrategy())
                .withKeyStrategy("model",          new ReplaceStrategy())
                .withKeyStrategy("provider",       new ReplaceStrategy())
                .withKeyStrategy("enableSearch",   new ReplaceStrategy())
                .withKeyStrategy("typeMs",         new ReplaceStrategy())
                .withKeyStrategy("analysisMs",     new ReplaceStrategy())
                .withKeyStrategy("summaryMs",      new ReplaceStrategy())
                .build();
    }

    // ── Graphs ──

    @Bean("myWorkflowGraph")
    public CompiledGraph workflowGraph(
            @Qualifier("workflowStateFactory") OverAllStateFactory factory) throws Exception {

        AsyncNodeAction intentNode = state -> CompletableFuture.supplyAsync(() -> {
            String input    = state.value("input").map(Object::toString).orElse("");
            String model    = state.value("model").map(Object::toString).orElse("qwen-max");
            String provider = state.value("provider").map(Object::toString).orElse("tongyi");
            String prompt = "判断以下输入是否值得用AI回答。\n" +
                    "如果是问题、请求、指令、闲聊、任何需要回应的内容，回复'问题'。\n" +
                    "只有当输入是完全无意义的乱码或空白时，才回复'其他'。\n" +
                    "只回复'问题'或'其他'这两个词之一，不要有任何其他内容。\n" +
                    "输入内容：" + input;

            long t0 = System.currentTimeMillis();
            String intent = "deepseek".equals(provider)
                    ? client(provider).prompt().user(prompt).options(deepseekOptions(model)).call().content().trim()
                    : client(provider).prompt().user(prompt).options(tongyiOptions(model, false)).call().content().trim();
            long ms = System.currentTimeMillis() - t0;

            return Map.of("intent", intent, "intentMs", ms);
        });

        AsyncNodeAction answerNode = state -> CompletableFuture.supplyAsync(() -> {
            String input    = state.value("input").map(Object::toString).orElse("");
            String model    = state.value("model").map(Object::toString).orElse("qwen-max");
            String provider = state.value("provider").map(Object::toString).orElse("tongyi");
            boolean enableSearch = Boolean.parseBoolean(
                    state.value("enableSearch").map(Object::toString).orElse("false"));
            String today = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            String sysPrompt = "今天是 " + today + "。请直接回答用户的问题，不要评论日期本身。";

            long t0 = System.currentTimeMillis();
            String answer = "deepseek".equals(provider)
                    ? client(provider).prompt().system(sysPrompt).user(input).options(deepseekOptions(model)).call().content()
                    : client(provider).prompt().system(sysPrompt).user(input).options(tongyiOptions(model, enableSearch)).call().content();
            long ms = System.currentTimeMillis() - t0;

            return Map.of("output", answer, "answerMs", ms);
        });

        AsyncNodeAction defaultNode = state -> CompletableFuture.supplyAsync(() ->
                Map.of("output", "我只能回答问题，请重新输入。", "defaultMs", 1L)
        );

        AsyncEdgeAction router = state -> CompletableFuture.supplyAsync(() -> {
            String intent = state.value("intent").map(Object::toString).orElse("");
            return intent.contains("问题") ? "answer" : "default";
        });

        return new StateGraph(factory)
                .addNode("intent",  intentNode)
                .addNode("answer",  answerNode)
                .addNode("default", defaultNode)
                .addEdge(START, "intent")
                .addConditionalEdges(
                        "intent",
                        router,
                        Map.of("answer", "answer", "default", "default")
                )
                .addEdge("answer",  END)
                .addEdge("default", END)
                .compile();
    }

    @Bean("analysisGraph")
    public CompiledGraph analysisGraph(
            @Qualifier("analysisStateFactory") OverAllStateFactory factory) throws Exception {

        AsyncNodeAction typeNode = state -> CompletableFuture.supplyAsync(() -> {
            String data     = state.value("data").map(Object::toString).orElse("");
            String model    = state.value("model").map(Object::toString).orElse("qwen-max");
            String provider = state.value("provider").map(Object::toString).orElse("tongyi");
            String prompt = "判断以下数据属于哪种类型，只回复类型名称，不要解释。\n" +
                    "可选类型：数值型、文本型、表格型、时间序列型、混合型。\n" +
                    "数据内容：\n" + data;

            long t0 = System.currentTimeMillis();
            String dataType = "deepseek".equals(provider)
                    ? client(provider).prompt().user(prompt).options(deepseekOptions(model)).call().content().trim()
                    : client(provider).prompt().user(prompt).options(tongyiOptions(model, false)).call().content().trim();
            long ms = System.currentTimeMillis() - t0;

            return Map.of("dataType", dataType, "typeMs", ms);
        });

        AsyncNodeAction analysisNode = state -> CompletableFuture.supplyAsync(() -> {
            String data     = state.value("data").map(Object::toString).orElse("");
            String dataType = state.value("dataType").map(Object::toString).orElse("未知");
            String model    = state.value("model").map(Object::toString).orElse("qwen-max");
            String provider = state.value("provider").map(Object::toString).orElse("tongyi");
            boolean enableSearch = Boolean.parseBoolean(
                    state.value("enableSearch").map(Object::toString).orElse("false"));
            String prompt = "你是一名数据分析专家。以下数据类型为【" + dataType + "】。\n" +
                    "请对数据进行专业分析，包括：关键特征、规律或趋势、异常点（如有）。\n" +
                    "数据内容：\n" + data;

            long t0 = System.currentTimeMillis();
            String result = "deepseek".equals(provider)
                    ? client(provider).prompt().user(prompt).options(deepseekOptions(model)).call().content()
                    : client(provider).prompt().user(prompt).options(tongyiOptions(model, enableSearch)).call().content();
            long ms = System.currentTimeMillis() - t0;

            return Map.of("analysisResult", result, "analysisMs", ms);
        });

        AsyncNodeAction summaryNode = state -> CompletableFuture.supplyAsync(() -> {
            String dataType       = state.value("dataType").map(Object::toString).orElse("未知");
            String analysisResult = state.value("analysisResult").map(Object::toString).orElse("");
            String model          = state.value("model").map(Object::toString).orElse("qwen-max");
            String provider       = state.value("provider").map(Object::toString).orElse("tongyi");
            String prompt = "请将以下数据分析结果整理成简洁清晰的报告，使用 Markdown 格式输出，" +
                    "包含：数据类型、核心发现、结论与建议三个部分。\n" +
                    "数据类型：" + dataType + "\n" +
                    "分析结果：\n" + analysisResult;

            long t0 = System.currentTimeMillis();
            String output = "deepseek".equals(provider)
                    ? client(provider).prompt().user(prompt).options(deepseekOptions(model)).call().content()
                    : client(provider).prompt().user(prompt).options(tongyiOptions(model, false)).call().content();
            long ms = System.currentTimeMillis() - t0;

            return Map.of("output", output, "summaryMs", ms);
        });

        return new StateGraph(factory)
                .addNode("type",     typeNode)
                .addNode("analysis", analysisNode)
                .addNode("summary",  summaryNode)
                .addEdge(START,      "type")
                .addEdge("type",     "analysis")
                .addEdge("analysis", "summary")
                .addEdge("summary",  END)
                .compile();
    }
}
