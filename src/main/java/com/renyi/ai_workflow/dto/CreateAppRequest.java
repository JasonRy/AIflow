package com.renyi.ai_workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAppRequest {
    @NotBlank(message = "应用名称不能为空")
    private String name;

    private String description;
}
