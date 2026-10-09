package com.planning.application.workforce;

import com.planning.api.dto.Dtos.AssignmentDto;
import com.planning.api.dto.WorkforceDtos.AbsenceDto;
import com.planning.api.dto.WorkforceDtos.AbsenceReason;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceContractProjectionTest {
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 5);
    private final EmployeeDto employee = new EmployeeDto(employeeId, "Camille", List.of("support"),
            2100, 180, 420, List.of());

    @Test
    void uneAbsenceReduitLaCibleEtConserveLaMargeSupp() {
        AbsenceDto absence = absence(monday, monday);
        var week = WorkforceContractProjection.weeks(monday, monday.plusDays(6), List.of(employee),
                List.of(absence), List.of(assignment(monday.plusDays(1), "08:00", monday.plusDays(1), "15:00"))).getFirst();
        assertThat(week.contractMinutes()).isEqualTo(2100);
        assertThat(week.absenceMinutes()).isEqualTo(420);
        assertThat(week.dueMinutes()).isEqualTo(1680);
        assertThat(week.maximumMinutes()).isEqualTo(1860);
        assertThat(week.assignedMinutes()).isEqualTo(420);
        assertThat(week.missingMinutes()).isEqualTo(1260);
        assertThat(week.overtimeMinutes()).isZero();
    }

    @Test
    void lesAbsencesSontDistinctesEtLeurCreditEstPlafonneAuContrat() {
        var week = WorkforceContractProjection.weeks(monday, monday.plusDays(6), List.of(employee),
                List.of(absence(monday, monday.plusDays(6)), absence(monday, monday)), List.of()).getFirst();
        assertThat(week.absenceMinutes()).isEqualTo(2100);
        assertThat(week.dueMinutes()).isZero();
        assertThat(week.maximumMinutes()).isEqualTo(180);
        assertThat(week.missingMinutes()).isZero();
    }

    @Test
    void uneNuitDuDimancheEstVentileeEntreLesDeuxSemaines() {
        EmployeeDto shortContract = new EmployeeDto(employeeId, "Camille", List.of("support"),
                60, 180, 30, List.of());
        var weeks = WorkforceContractProjection.weeks(monday, monday.plusDays(7), List.of(shortContract), List.of(),
                List.of(assignment(monday.plusDays(6), "22:00", monday.plusDays(7), "02:00")));
        assertThat(weeks).hasSize(2);
        assertThat(weeks).allSatisfy(week -> {
            assertThat(week.assignedMinutes()).isEqualTo(120);
            assertThat(week.overtimeMinutes()).isEqualTo(60);
            assertThat(week.missingMinutes()).isZero();
            assertThat(week.dueMinutes()).isEqualTo(60);
        });
        assertThat(weeks.getLast().from()).isEqualTo(monday.plusDays(7));
        assertThat(weeks.getLast().to()).isEqualTo(monday.plusDays(7));
    }

    @Test
    void uneAbsenceHorsSemaineNeReduitPasLeContrat() {
        var weeks = WorkforceContractProjection.weeks(monday, monday.plusDays(13), List.of(employee),
                List.of(absence(monday.plusDays(7), monday.plusDays(7))), List.of());
        assertThat(weeks.getFirst().dueMinutes()).isEqualTo(2100);
        assertThat(weeks.getLast().dueMinutes()).isEqualTo(1680);
    }

    private AbsenceDto absence(LocalDate from, LocalDate to) {
        return new AbsenceDto(UUID.randomUUID(), employeeId, from, to, AbsenceReason.SICKNESS, null);
    }

    private AssignmentDto assignment(LocalDate from, String start, LocalDate to, String end) {
        return new AssignmentDto(UUID.randomUUID(), UUID.randomUUID(), "Support", 0, employeeId, "Camille",
                "employee", "Support", 0, LocalTime.parse(start), LocalTime.parse(end), false, from, to);
    }
}
