package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.SolveJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SolveJobJpaRepository extends JpaRepository<SolveJobEntity, UUID> {

    List<SolveJobEntity> findByScenarioIdOrderByCreatedAtDesc(UUID scenarioId);

    List<SolveJobEntity> findByStatusIn(Collection<String> statuses);
}
