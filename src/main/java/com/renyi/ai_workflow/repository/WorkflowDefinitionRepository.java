package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.WorkflowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, Long> {
    List<WorkflowDefinitionEntity> findAllByOrderByUpdatedAtDesc();
}
