package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ConstraintConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConstraintConfigJpaRepository extends JpaRepository<ConstraintConfigEntity, UUID> {

    List<ConstraintConfigEntity> findByScenarioIdOrderByType(UUID scenarioId);

    List<ConstraintConfigEntity> findByScenarioIdAndEnabledTrue(UUID scenarioId);
}
