package com.planning.api.dto;

import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.Effect;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Contrats de saisie métier : aucune connaissance des critères JSON n'est requise. */
public final class WorkforceDtos {
    private WorkforceDtos() {}

    public enum AbsenceReason { LEAVE, SICKNESS, OTHER }

    public record WorkspaceInput(@NotBlank(message = "Le nom de l'établissement est obligatoire.") String label,
                                 @NotNull(message = "Le profil de l'établissement est obligatoire.") BusinessProfile profile,
                                 @NotNull(message = "La date de début est obligatoire.") LocalDate from,
                                 @NotNull(message = "La date de fin est obligatoire.") LocalDate to, Boolean demo) {}
    public record WorkspaceCreated(UUID tenantId, UUID scenarioId) {}
    public record WorkspaceDto(UUID tenantId, UUID scenarioId, String label,
                               BusinessProfile profile, LocalDate from, LocalDate to,
                               int employeeCount) {}
    public record PeriodDto(LocalDate from, LocalDate to, int slotMinutes) {}
    public record EmployeeInput(@NotBlank(message = "Le nom de l'employé est obligatoire.") String label,
                                @NotEmpty(message = "Renseignez au moins une compétence.") List<String> skills,
                                @NotNull(message = "La durée hebdomadaire du contrat est obligatoire.") Integer contractWeeklyMinutes,
                                List<@Valid RecurringAvailabilityInput> availability) {}
    public record RecurringAvailabilityInput(Effect effect, Integer dayInCycle,
                                             LocalTime startTime, LocalTime endTime,
                                             Integer priority) {}
    public record EmployeeDto(UUID id, String label, List<String> skills, int contractWeeklyMinutes,
                              List<Dtos.AvailabilityDto> availability) {}
    public record ShiftRoleInput(@NotBlank(message = "Le nom du rôle est obligatoire.") String role,
                                 @NotNull(message = "Le nombre de personnes est obligatoire.") Integer quantity,
                                 String skill) {}
    public record ShiftInput(@NotBlank(message = "Le nom du service est obligatoire.") String label,
                             @NotNull(message = "La date du service est obligatoire.") LocalDate date,
                             @NotNull(message = "L'heure de début est obligatoire.") LocalTime startTime,
                             @NotNull(message = "L'heure de fin est obligatoire.") LocalTime endTime,
                             Boolean nextDay,
                             @NotEmpty(message = "Renseignez au moins un rôle.") List<@Valid ShiftRoleInput> roles) {}
    public record ShiftRoleDto(String role, int quantity, String skill) {}
    public record ShiftDto(UUID id, String label, LocalDate date, LocalTime startTime,
                           LocalTime endTime, boolean nextDay, int durationMinutes,
                           List<ShiftRoleDto> roles) {}
    public record AbsenceInput(@NotNull(message = "L'employé est obligatoire.") UUID resourceId,
                               @NotNull(message = "Le début de l'absence est obligatoire.") LocalDate from,
                               @NotNull(message = "La fin de l'absence est obligatoire.") LocalDate to,
                               @NotNull(message = "Le motif de l'absence est obligatoire.") AbsenceReason reason, String note) {}
    public record AbsenceDto(UUID id, UUID resourceId, LocalDate from, LocalDate to,
                             AbsenceReason reason, String note) {}
    public record PolicyDto(int minRestMinutes, int maximumDailyMinutes, boolean balanceWorkload) {}
    public record KpisDto(int employeeCount, int shiftCount, int requiredPositions,
                          int assignedPositions, int unassignedPositions, int coveragePercent,
                          long requiredMinutes, long assignedMinutes, long absenceDays) {}
    public record OverviewDto(Dtos.TenantDto tenant, Dtos.ScenarioDto scenario, PeriodDto period,
                              List<Dtos.ResourceTypeDto> resourceTypes, List<EmployeeDto> employees,
                              List<ShiftDto> shifts, List<AbsenceDto> absences,
                              Dtos.SolveJobDto latestJob, List<Dtos.AssignmentDto> assignments,
                              Dtos.SolveReportDto report, KpisDto kpis,
                              List<Dtos.ConstraintConfigDto> rules, PolicyDto policy,
                              boolean planOutdated) {}
}
