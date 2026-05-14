package com.renyi.ai_workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

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
    private List<Map<String, Object>> callTree;
    private long costMs;
    private String timestamp;
}
