package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ResourceTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceTypeJpaRepository extends JpaRepository<ResourceTypeEntity, UUID> {

    List<ResourceTypeEntity> findByTenantIdOrderByLabel(UUID tenantId);
}
