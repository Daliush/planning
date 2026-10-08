package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.DemandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DemandJpaRepository extends JpaRepository<DemandEntity, UUID> {

    List<DemandEntity> findByScenarioIdOrderByLabel(UUID scenarioId);
}
