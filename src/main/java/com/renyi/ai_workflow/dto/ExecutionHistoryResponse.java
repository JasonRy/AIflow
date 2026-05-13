package com.renyi.ai_workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionHistoryResponse {
    private Long id;
    private String workflowType;
    private String userMessage;
    private String assistantMessage;
    private String intent;
    private String model;
    private String provider;
    private boolean enableSearch;
    private String nodeTimings;
    private long costMs;
    private String timestamp;
}
