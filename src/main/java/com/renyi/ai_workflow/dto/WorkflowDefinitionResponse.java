package com.renyi.ai_workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class WorkflowDefinitionResponse {
    private Long id;
    private Long appId;
    private String appName;
    private String name;
    private String description;
    private String status;
    private Integer versionNumber;
    private String versionStatus;
    private List<NodeResponse> nodes;
    private List<EdgeResponse> edges;
    private Map<String, Object> variables;
    private String createdAt;
    private String updatedAt;

    @Data
    @Builder
    public static class NodeResponse {
        private String key;
        private String type;
        private String title;
        private int x;
        private int y;
        private Map<String, Object> config;
    }

    @Data
    @Builder
    public static class EdgeResponse {
        private String source;
        private String target;
        private String sourceHandle;
        private String targetHandle;
        private Map<String, Object> condition;
    }
}
