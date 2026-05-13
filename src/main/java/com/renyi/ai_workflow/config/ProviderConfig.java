package com.renyi.ai_workflow.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfig {

    @Bean("dashscopeChatClient")
    public ChatClient dashscopeChatClient(DashScopeChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(
            @Value("${deepseek.api-key}") String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
                .build();
        return ChatClient.builder(model).build();
    }
}
