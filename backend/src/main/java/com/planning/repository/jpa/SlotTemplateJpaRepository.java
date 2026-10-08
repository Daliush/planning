package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.SlotTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlotTemplateJpaRepository extends JpaRepository<SlotTemplateEntity, UUID> {

    List<SlotTemplateEntity> findByTimeModelIdOrderByDayInCycleAscOrdinalAsc(UUID timeModelId);
}
