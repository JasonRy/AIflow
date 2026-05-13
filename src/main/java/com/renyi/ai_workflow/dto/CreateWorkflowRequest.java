package com.renyi.ai_workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWorkflowRequest {
    @NotNull(message = "appId 不能为空")
    private Long appId;

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;
}
