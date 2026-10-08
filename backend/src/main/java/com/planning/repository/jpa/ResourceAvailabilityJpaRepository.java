package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ResourceAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceAvailabilityJpaRepository
        extends JpaRepository<ResourceAvailabilityEntity, UUID> {

    List<ResourceAvailabilityEntity> findByResourceIdOrderByPriority(UUID resourceId);

    List<ResourceAvailabilityEntity> findByResourceIdIn(Collection<UUID> resourceIds);

    @Query("""
            select a from ResourceAvailabilityEntity a
            where a.resourceId in (select r.id from ResourceEntity r where r.scenarioId = :scenarioId)
            """)
    List<ResourceAvailabilityEntity> findByScenarioId(UUID scenarioId);
}
