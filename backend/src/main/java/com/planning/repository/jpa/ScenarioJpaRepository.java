package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, UUID> {

    List<ScenarioEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
