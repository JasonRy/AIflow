package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.WorkflowDefinitionEntity;
import com.renyi.ai_workflow.entity.WorkflowVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, Long> {
    Optional<WorkflowVersionEntity> findTopByWorkflowOrderByVersionNumberDesc(WorkflowDefinitionEntity workflow);
}
