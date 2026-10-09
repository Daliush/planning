package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.AbsenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AbsenceJpaRepository extends JpaRepository<AbsenceEntity, UUID> {
    List<AbsenceEntity> findByScenarioIdOrderByFromDateAsc(UUID scenarioId);
    List<AbsenceEntity> findByResourceId(UUID resourceId);
}
