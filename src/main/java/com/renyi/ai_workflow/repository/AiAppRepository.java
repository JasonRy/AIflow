package com.renyi.ai_workflow.repository;

import com.renyi.ai_workflow.entity.AiAppEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAppRepository extends JpaRepository<AiAppEntity, Long> {
    List<AiAppEntity> findAllByOrderByUpdatedAtDesc();
}
