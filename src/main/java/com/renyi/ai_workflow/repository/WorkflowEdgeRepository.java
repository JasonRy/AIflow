package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.WorkflowEdgeEntity;
import com.renyi.ai_workflow.entity.WorkflowVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdgeEntity, Long> {
    List<WorkflowEdgeEntity> findAllByVersionOrderByIdAsc(WorkflowVersionEntity version);
    void deleteAllByVersion(WorkflowVersionEntity version);
}
