package com.renyi.ai_workflow.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class WorkflowResponse {
    private String answer;
    private String intent;
    private String model;
    private String provider;
    private String nodeTimings;
    private List<Map<String, Object>> callTree;
    private long costMs;
    private String timestamp;
}
