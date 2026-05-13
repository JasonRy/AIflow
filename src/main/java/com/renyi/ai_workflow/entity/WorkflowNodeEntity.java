package com.renyi.ai_workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workflow_nodes")
public class WorkflowNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private WorkflowVersionEntity version;

    @Column(nullable = false, length = 128)
    private String nodeKey;

    @Column(nullable = false, length = 64)
    private String nodeType;

    @Column(nullable = false, length = 128)
    private String title;

    private int positionX;

    private int positionY;

    @Lob
    @Column(nullable = false)
    private String configJson;
}
