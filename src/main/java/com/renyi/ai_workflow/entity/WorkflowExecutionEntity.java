package com.renyi.ai_workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "workflow_executions")
public class WorkflowExecutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String workflowType;

    @Column(length = 64)
    private String intent;

    @Column(length = 64)
    private String model;

    @Column(length = 64)
    private String provider;

    private boolean enableSearch;

    private long costMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Lob
    @Column(nullable = false)
    private String inputText;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Lob
    private String nodeTimings;

    @Lob
    private String callTreeJson;
}
