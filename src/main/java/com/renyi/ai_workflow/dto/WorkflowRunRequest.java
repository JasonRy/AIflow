package com.renyi.ai_workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowRunRequest {
    @NotBlank(message = "message 不能为空")
    private String message;

    private String model = "qwen-max";
    private String provider = "tongyi";
    private boolean enableSearch = false;
}
