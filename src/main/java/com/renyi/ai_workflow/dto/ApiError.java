package com.renyi.ai_workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class ApiError {
    private String code;
    private String message;
    private String path;
    private String timestamp;

    public static ApiError of(String code, String message, String path) {
        return ApiError.builder()
                .code(code)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
}
