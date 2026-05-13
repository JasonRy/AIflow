package com.renyi.ai_workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalysisRunRequest {
    @NotBlank(message = "data 不能为空")
    private String data;

    private String model = "qwen-max";
    private String provider = "tongyi";
    private boolean enableSearch = false;
}
