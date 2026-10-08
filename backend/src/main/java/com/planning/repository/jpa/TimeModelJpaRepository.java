package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.TimeModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeModelJpaRepository extends JpaRepository<TimeModelEntity, UUID> {

    List<TimeModelEntity> findByTenantIdOrderByLabel(UUID tenantId);
}
