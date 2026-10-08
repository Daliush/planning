package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {
}
