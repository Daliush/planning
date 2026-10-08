package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.RequirementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RequirementJpaRepository extends JpaRepository<RequirementEntity, UUID> {

    List<RequirementEntity> findByDemandIdOrderByRole(UUID demandId);

    List<RequirementEntity> findByDemandIdIn(Collection<UUID> demandIds);

    @Query("""
            select r from RequirementEntity r
            where r.demandId in (select d.id from DemandEntity d where d.scenarioId = :scenarioId)
            """)
    List<RequirementEntity> findByScenarioId(UUID scenarioId);
}
