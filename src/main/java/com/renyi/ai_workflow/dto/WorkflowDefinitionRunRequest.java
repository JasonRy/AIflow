package com.renyi.ai_workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class WorkflowDefinitionRunRequest {
    @NotBlank(message = "message 不能为空")
    private String message;

    private String model = "qwen-max";
    private String provider = "tongyi";
    private boolean enableSearch = false;
    private Map<String, Object> variables = Map.of();
}
