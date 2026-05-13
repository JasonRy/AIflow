package com.renyi.ai_workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppResponse {
    private Long id;
    private String name;
    private String description;
    private String createdAt;
    private String updatedAt;
}
