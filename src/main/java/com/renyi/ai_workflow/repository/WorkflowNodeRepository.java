package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.WorkflowNodeEntity;
import com.renyi.ai_workflow.entity.WorkflowVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNodeEntity, Long> {
    List<WorkflowNodeEntity> findAllByVersionOrderByIdAsc(WorkflowVersionEntity version);
    void deleteAllByVersion(WorkflowVersionEntity version);
}
