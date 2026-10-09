package com.planning.repository.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Les entités Hibernate, regroupées pour rester lisibles.
 *
 * <p>INVARIANT §4.4 : <strong>aucune de ces classes ne franchit la frontière du
 * domaine.</strong> Elles sont mutables et suivies par le contexte de persistance,
 * or Timefold partage les faits du problème par référence entre tous les clones de
 * solution — une mutation corromprait silencieusement toutes les photos déjà
 * prises. Les mappers produisent des {@code record} immuables ; cette conversion
 * n'est pas une formalité, c'est une barrière de sécurité.
 */
public final class Entities {

    private Entities() {
    }

    @Entity(name = "TenantEntity")
    @Table(name = "tenant")
    public static class TenantEntity {
        @Id
        public UUID id;
        public String code;
        public String label;
        public String profile;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    @Entity(name = "ResourceTypeEntity")
    @Table(name = "resource_type")
    public static class ResourceTypeEntity {
        @Id
        public UUID id;
        @Column(name = "tenant_id")
        public UUID tenantId;
        public String code;
        public String label;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "attr_schema", columnDefinition = "jsonb")
        public JsonNode attrSchema;
    }

    @Entity(name = "TimeModelEntity")
    @Table(name = "time_model")
    public static class TimeModelEntity {
        @Id
        public UUID id;
        @Column(name = "tenant_id")
        public UUID tenantId;
        public String label;
        @Enumerated(EnumType.STRING)
        @Column(name = "horizon_kind")
        public HorizonKind horizonKind;
        @Column(name = "cycle_days")
        public Integer cycleDays;
        @Column(name = "horizon_from")
        public LocalDate horizonFrom;
        @Column(name = "horizon_to")
        public LocalDate horizonTo;
        @Column(name = "max_gap_minutes")
        public int maxGapMinutes;
    }

    @Entity(name = "SlotTemplateEntity")
    @Table(name = "slot_template")
    public static class SlotTemplateEntity {
        @Id
        public UUID id;
        @Column(name = "time_model_id")
        public UUID timeModelId;
        @Column(name = "day_in_cycle")
        public int dayInCycle;
        @Column(name = "ordinal")
        public int ordinal;
        @Column(name = "start_time")
        public LocalTime startTime;
        @Column(name = "end_time")
        public LocalTime endTime;
    }

    @Entity(name = "ScenarioEntity")
    @Table(name = "scenario")
    public static class ScenarioEntity {
        @Id
        public UUID id;
        @Column(name = "tenant_id")
        public UUID tenantId;
        public String label;
        @Column(name = "time_model_id")
        public UUID timeModelId;
        public String status;
        @Column(name = "parent_id")
        public UUID parentId;
        /** Verticale métier, ou {@code null} pour « ne rien filtrer ». Jamais lue par le domaine. */
        @Enumerated(EnumType.STRING)
        public BusinessProfile profile;
        @Column(name = "created_at")
        public Instant createdAt;
    }

    public static class CalendarWeekId implements Serializable {
        public UUID scenarioId;
        public int weekNumber;

        @Override
        public boolean equals(Object o) {
            return o instanceof CalendarWeekId other
                    && other.weekNumber == weekNumber
                    && Objects.equals(other.scenarioId, scenarioId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scenarioId, weekNumber);
        }
    }

    @Entity(name = "CalendarWeekEntity")
    @Table(name = "calendar_week")
    @IdClass(CalendarWeekId.class)
    public static class CalendarWeekEntity {
        @Id
        @Column(name = "scenario_id")
        public UUID scenarioId;
        @Id
        @Column(name = "week_number")
        public int weekNumber;
        public boolean worked;
        @Column(name = "cycle_offset")
        public int cycleOffset;
    }

    @Entity(name = "ResourceEntity")
    @Table(name = "resource")
    public static class ResourceEntity {
        @Id
        public UUID id;
        @Column(name = "scenario_id")
        public UUID scenarioId;
        @Column(name = "type_id")
        public UUID typeId;
        public String label;
        @Enumerated(EnumType.STRING)
        @Column(name = "default_effect")
        public Effect defaultEffect;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode attributes;
    }

    @Entity(name = "ResourceAvailabilityEntity")
    @Table(name = "resource_availability")
    public static class ResourceAvailabilityEntity {
        @Id
        public UUID id;
        @Column(name = "resource_id")
        public UUID resourceId;
        @Enumerated(EnumType.STRING)
        public Effect effect;
        @Column(name = "day_in_cycle")
        public Integer dayInCycle;
        @Column(name = "start_time")
        public LocalTime startTime;
        @Column(name = "end_time")
        public LocalTime endTime;
        public int priority;
    }

    @Entity(name = "DemandEntity")
    @Table(name = "demand")
    public static class DemandEntity {
        @Id
        public UUID id;
        @Column(name = "scenario_id")
        public UUID scenarioId;
        public String label;
        @Column(name = "duration_minutes")
        public int durationMinutes;
        public int occurrences;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode attributes;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode placement;
    }

    @Entity(name = "AbsenceEntity")
    @Table(name = "absence")
    public static class AbsenceEntity {
        @Id public UUID id;
        @Column(name = "scenario_id") public UUID scenarioId;
        @Column(name = "resource_id") public UUID resourceId;
        @Column(name = "from_date") public LocalDate fromDate;
        @Column(name = "to_date") public LocalDate toDate;
        public String reason;
        public String note;
    }

    @Entity(name = "RequirementEntity")
    @Table(name = "requirement")
    public static class RequirementEntity {
        @Id
        public UUID id;
        @Column(name = "demand_id")
        public UUID demandId;
        public String role;
        @Column(name = "type_id")
        public UUID typeId;
        public int quantity;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode criteria;
    }

    @Entity(name = "ConstraintConfigEntity")
    @Table(name = "constraint_config")
    public static class ConstraintConfigEntity {
        @Id
        public UUID id;
        @Column(name = "scenario_id")
        public UUID scenarioId;
        @Column(name = "type")
        public String type;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode scope;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode params;
        public int weight;
        public boolean enabled;
    }

    public static class ConstraintWeightId implements Serializable {
        public UUID scenarioId;
        public String constraintId;

        @Override
        public boolean equals(Object o) {
            return o instanceof ConstraintWeightId other
                    && Objects.equals(other.scenarioId, scenarioId)
                    && Objects.equals(other.constraintId, constraintId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scenarioId, constraintId);
        }
    }

    @Entity(name = "ConstraintWeightEntity")
    @Table(name = "constraint_weight")
    @IdClass(ConstraintWeightId.class)
    public static class ConstraintWeightEntity {
        @Id
        @Column(name = "scenario_id")
        public UUID scenarioId;
        @Id
        @Column(name = "constraint_id")
        public String constraintId;
        @Enumerated(EnumType.STRING)
        public Level level;
        public int weight;
    }

    @Entity(name = "SolveJobEntity")
    @Table(name = "solve_job")
    public static class SolveJobEntity {
        @Id
        public UUID id;
        @Column(name = "scenario_id")
        public UUID scenarioId;
        public String status;
        @Column(name = "time_limit_seconds")
        public int timeLimitSeconds;
        @Column(name = "hard_score")
        public Integer hardScore;
        @Column(name = "soft_score")
        public Integer softScore;
        @Column(name = "created_at")
        public Instant createdAt;
        @Column(name = "started_at")
        public Instant startedAt;
        @Column(name = "finished_at")
        public Instant finishedAt;
        @Column(name = "error_code")
        public String errorCode;
        @Column(name = "error_detail")
        public String errorDetail;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode report;
    }

    @Entity(name = "AssignmentEntity")
    @Table(name = "assignment")
    public static class AssignmentEntity {
        @Id
        public UUID id;
        @Column(name = "solve_job_id")
        public UUID solveJobId;
        @Column(name = "demand_id")
        public UUID demandId;
        public int occurrence;
        @Column(name = "resource_id")
        public UUID resourceId;
        public String role;
        @Column(name = "day_in_cycle")
        public int dayInCycle;
        @Column(name = "start_time")
        public LocalTime startTime;
        @Column(name = "end_time")
        public LocalTime endTime;
        public boolean pinned;
        @Column(name = "start_date") public LocalDate startDate;
        @Column(name = "end_date") public LocalDate endDate;
    }
}
