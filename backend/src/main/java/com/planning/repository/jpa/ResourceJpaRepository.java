package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByScenarioIdOrderByLabel(UUID scenarioId);

    List<ResourceEntity> findByScenarioIdAndTypeIdOrderByLabel(UUID scenarioId, UUID typeId);
}
