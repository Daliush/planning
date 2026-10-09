package com.planning.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Les lignes brutes échangées entre la persistance et le builder. */
public final class Rows {

    private Rows() {
    }

    public record TimeModelRow(
            UUID id,
            String label,
            HorizonKind horizonKind,
            Integer cycleDays,
            LocalDate horizonFrom,
            LocalDate horizonTo,
            int maxGapMinutes) {
    }

    public record SlotRow(int dayInCycle, int ordinal, LocalTime startTime, LocalTime endTime) {
    }

    /**
     * @param typeCode recopié dans {@code attributes} sous la clé {@code type} par
     *                 le builder. Sans cette projection, un scope
     *                 {@code {"eq":{"path":"/type",…}}} ne matcherait jamais rien,
     *                 silencieusement (INVARIANT §6.4).
     */
    public record ResourceRow(
            UUID id,
            UUID typeId,
            String typeCode,
            String label,
            Effect defaultEffect,
            JsonNode attributes) {
    }

    public record AvailabilityRow(
            UUID resourceId,
            Effect effect,
            Integer dayInCycle,
            LocalTime startTime,
            LocalTime endTime,
            int priority) {
    }

    public record AbsenceRow(UUID resourceId, LocalDate fromDate, LocalDate toDate) {
    }

    public record RequirementRow(
            UUID id,
            String role,
            UUID typeId,
            int quantity,
            JsonNode criteria) {
    }

    public record DemandRow(
            UUID id,
            String label,
            int durationMinutes,
            int occurrences,
            JsonNode attributes,
            JsonNode placement,
            List<RequirementRow> requirements) {
    }

    public record ConstraintConfigRow(
            UUID id,
            String type,
            JsonNode scope,
            JsonNode params,
            int weight,
            boolean enabled) {
    }

    public record ConstraintWeightRow(String constraintId, Level level, int weight) {
    }

    /** Une ligne de résultat. {@code resourceId} nul = poste non pourvu. */
    public record AssignmentRow(
            UUID demandId,
            int occurrence,
            UUID resourceId,
            String role,
            int dayInCycle,
            LocalTime startTime,
            LocalTime endTime,
            boolean pinned,
            LocalDate date,
            LocalDate endDate) {
        public AssignmentRow(UUID demandId, int occurrence, UUID resourceId, String role,
                             int dayInCycle, LocalTime startTime, LocalTime endTime, boolean pinned) {
            this(demandId, occurrence, resourceId, role, dayInCycle, startTime, endTime, pinned, null, null);
        }
    }
}
