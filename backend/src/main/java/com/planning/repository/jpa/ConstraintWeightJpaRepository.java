package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ConstraintWeightEntity;
import com.planning.repository.entity.Entities.ConstraintWeightId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConstraintWeightJpaRepository
        extends JpaRepository<ConstraintWeightEntity, ConstraintWeightId> {

    List<ConstraintWeightEntity> findByScenarioIdOrderByConstraintId(UUID scenarioId);
}
