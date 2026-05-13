package com.renyi.ai_workflow.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ExecutionRecord {
    private String answer;    // AI 回答
    private String intent;    // 识别到的意图
    private long costMs;      // 执行耗时
    private String timestamp; // 执行时间
}
