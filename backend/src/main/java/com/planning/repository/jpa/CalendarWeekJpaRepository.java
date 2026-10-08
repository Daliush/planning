package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.CalendarWeekEntity;
import com.planning.repository.entity.Entities.CalendarWeekId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalendarWeekJpaRepository extends JpaRepository<CalendarWeekEntity, CalendarWeekId> {

    List<CalendarWeekEntity> findByScenarioIdOrderByWeekNumber(UUID scenarioId);
}
