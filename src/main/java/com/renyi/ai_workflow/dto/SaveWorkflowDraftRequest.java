package com.renyi.ai_workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SaveWorkflowDraftRequest {
    @Valid
    private List<NodeSpec> nodes = new ArrayList<>();

    @Valid
    private List<EdgeSpec> edges = new ArrayList<>();

    private Map<String, Object> variables = Map.of();

    @Data
    public static class NodeSpec {
        @NotBlank(message = "节点 key 不能为空")
        private String key;

        @NotBlank(message = "节点类型不能为空")
        private String type;

        @NotBlank(message = "节点标题不能为空")
        private String title;

        @NotNull(message = "节点 x 坐标不能为空")
        private Integer x;

        @NotNull(message = "节点 y 坐标不能为空")
        private Integer y;

        private Map<String, Object> config = Map.of();
    }

    @Data
    public static class EdgeSpec {
        @NotBlank(message = "连线源节点不能为空")
        private String source;

        @NotBlank(message = "连线目标节点不能为空")
        private String target;

        private String sourceHandle;
        private String targetHandle;
        private Map<String, Object> condition = Map.of();
    }
}
