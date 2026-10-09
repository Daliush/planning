package com.planning.application.workforce;

import com.planning.api.dto.Dtos;
import com.planning.api.dto.WorkforceDtos.*;
import com.planning.application.ConstraintService;
import com.planning.application.SolveService;
import com.planning.repository.entity.Entities.*;
import com.planning.repository.jpa.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceOverviewServiceTest {
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final WorkforceEmployeeService employees = mock(WorkforceEmployeeService.class);
    private final WorkforceShiftService shifts = mock(WorkforceShiftService.class);
    private final WorkforceAbsenceService absences = mock(WorkforceAbsenceService.class);
    private final WorkforcePolicyService policy = mock(WorkforcePolicyService.class);
    private final SolveService solves = mock(SolveService.class);
    private final WorkforceOverviewService service = new WorkforceOverviewService(access, employees, shifts, absences,
            policy, mock(ConstraintService.class), solves, mock(TenantJpaRepository.class),
            mock(ScenarioJpaRepository.class), mock(TimeModelJpaRepository.class),
            mock(ResourceTypeJpaRepository.class), mock(ResourceJpaRepository.class));
    private ScenarioEntity scenario;
    private UUID shiftId;
    private Dtos.SolveJobDto job;

    @BeforeEach
    void solvedScenario() {
        scenario = new ScenarioEntity();
        scenario.id = UUID.randomUUID();
        scenario.tenantId = UUID.randomUUID();
        scenario.status = "SOLVED";
        TenantEntity tenant = new TenantEntity();
        tenant.id = scenario.tenantId;
        TimeModelEntity model = new TimeModelEntity();
        model.horizonFrom = LocalDate.of(2026, 10, 5);
        model.horizonTo = model.horizonFrom.plusDays(6);
        WorkforceAccess.Context context = new WorkforceAccess.Context(scenario, tenant, model, new ResourceTypeEntity());
        when(access.context(scenario.id)).thenReturn(context);
        shiftId = UUID.randomUUID();
        when(shifts.list(scenario.id)).thenReturn(List.of(new ShiftDto(shiftId, "Service", model.horizonFrom,
                LocalTime.of(11, 0), LocalTime.of(15, 0), false, 240, List.of(new ShiftRoleDto("Salle", 2, "salle")))));
        when(absences.list(scenario.id)).thenReturn(List.of(new AbsenceDto(UUID.randomUUID(), UUID.randomUUID(),
                model.horizonFrom, model.horizonFrom.plusDays(1), AbsenceReason.LEAVE, null)));
        job = new Dtos.SolveJobDto(UUID.randomUUID(), scenario.id, "DONE", 5, -1000, 0, false,
                Instant.now(), null, Instant.now(), null, null);
        when(solves.list(scenario.id)).thenReturn(List.of(job));
        Dtos.AssignmentDto assignment = mock(Dtos.AssignmentDto.class);
        when(assignment.resourceId()).thenReturn(UUID.randomUUID());
        when(assignment.demandId()).thenReturn(shiftId);
        when(solves.assignments(job.id(), null, null)).thenReturn(List.of(assignment));
    }

    @Test
    void laCouvertureCompteLesPostesEtMinutesReellementAffectes() {
        OverviewDto overview = service.overview(scenario.id);
        assertThat(overview.kpis().requiredPositions()).isEqualTo(2);
        assertThat(overview.kpis().assignedPositions()).isEqualTo(1);
        assertThat(overview.kpis().unassignedPositions()).isEqualTo(1);
        assertThat(overview.kpis().coveragePercent()).isEqualTo(50);
        assertThat(overview.kpis().requiredMinutes()).isEqualTo(480);
        assertThat(overview.kpis().assignedMinutes()).isEqualTo(240);
        assertThat(overview.kpis().absenceDays()).isEqualTo(2);
    }

    @Test
    void lesResultatsAnterieursNeSontPasPresentesCommeActuelsApresMutation() {
        scenario.status = "DRAFT";
        OverviewDto overview = service.overview(scenario.id);
        assertThat(overview.planOutdated()).isTrue();
        assertThat(overview.assignments()).isEmpty();
        assertThat(overview.report()).isNull();
        assertThat(overview.kpis().coveragePercent()).isZero();
        assertThat(overview.latestJob()).isEqualTo(job);
        verify(solves, never()).assignments(any(), any(), any());
        verify(solves, never()).report(any());
    }
}
