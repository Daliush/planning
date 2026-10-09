package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ScenarioEntity s where s.id = :id")
    java.util.Optional<ScenarioEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<ScenarioEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
