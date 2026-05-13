package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.WorkflowExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {
    List<WorkflowExecutionEntity> findTop50ByOrderByCreatedAtDesc();
}
