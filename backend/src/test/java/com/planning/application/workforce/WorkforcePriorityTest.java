package com.planning.application.workforce;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.ConstraintConfigInput;
import com.planning.api.dto.Dtos.ConstraintWeightInput;
import com.planning.application.ConstraintService;
import com.planning.domain.builder.ProblemBuilder.BuiltProblem;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.SolveReport;
import com.planning.domain.model.Violation;
import com.planning.domain.port.Rows.*;
import com.planning.domain.solver.ExplanationService;
import com.planning.domain.solver.SolverService;
import com.planning.repository.entity.Entities.ResourceEntity;
import com.planning.repository.jpa.ConstraintConfigJpaRepository;
import com.planning.support.FakeProblemSource;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Résout avec les règles réellement produites par la façade, contrat SOFT compris. */
class WorkforcePriorityTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 8);
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID TYPE = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unPosteDeTrenteMinutesResteVacantPendantUneAbsenceMalgreLeContrat() {
        BuiltProblem built = problem(3, 2100, List.of(demand(1, "09:00", 30)),
                List.of(new AbsenceRow(EMPLOYEE, FROM.plusDays(1), FROM.plusDays(1))));
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void uneAbsenceInterditLeServiceCourtEtLaFinDuServiceDeNuit() {
        BuiltProblem built = problem(3, 2100,
                List.of(demand(0, "22:00", 240), demand(1, "09:00", 30)),
                List.of(new AbsenceRow(EMPLOYEE, FROM.plusDays(1), FROM.plusDays(1))));
        assertOnlyVacancies(solve(built), built, 2);
    }

    @Test
    void unConflitLaisseUnDesDeuxPostesVacant() {
        BuiltProblem built = problem(3, 2100,
                List.of(demand(1, "09:00", 30), demand(1, "09:00", 30)), List.of());
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void leReposEstPrioritaireSurLaCouverture() {
        BuiltProblem built = problem(3, 2100,
                List.of(demand(0, "23:30", 30), demand(1, "09:00", 30)), List.of());
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void lePlafondQuotidienEstPrioritaireSurLaCouverture() {
        BuiltProblem built = problem(3, 2100,
                List.of(demand(1, "09:00", 600), demand(1, "19:00", 30)), List.of());
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void lePlafondHebdomadaireEstPrioritaireSurLaCouverture() {
        BuiltProblem built = problem(3, 30,
                List.of(demand(0, "09:00", 30), demand(1, "09:00", 30)), List.of());
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void unContratNulNePermetAucunPoste() {
        BuiltProblem built = problem(3, 0, List.of(demand(1, "09:00", 30)), List.of());
        assertOnlyVacancies(solve(built), built, 1);
    }

    @Test
    void laLimiteDeSixJoursPrimeSurDouzePetitsPostesDuSeptiemeJour() {
        List<DemandRow> demands = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            for (int slot = 0; slot < 12; slot++) {
                demands.add(demand(day, LocalTime.of(9, 0).plusMinutes(slot * 30L).toString(), 30));
            }
        }
        BuiltProblem built = problem(7, 10080, demands, List.of());
        assertOnlyVacancies(new SolverService(EnvironmentMode.FULL_ASSERT).solve(built.schedule(), 3), built, 12);
    }

    private Schedule solve(BuiltProblem built) {
        return new SolverService(EnvironmentMode.FULL_ASSERT).solve(built.schedule(), 1);
    }

    private void assertOnlyVacancies(Schedule solved, BuiltProblem built, int expectedVacancies) {
        assertThat(solved.getActivities()).allMatch(activity -> activity.getStart() != null);
        assertThat(solved.getBookings().stream().filter(booking -> booking.getResource() == null))
                .hasSize(expectedVacancies);
        SolveReport report = new ExplanationService(ConstraintRegistry.standard()).report(solved, built.weights());
        assertThat(report.hardViolations()).extracting(Violation::constraintId)
                .containsOnly("booking.unassigned");
        assertThat(report.penaltyByConstraint()).containsEntry("booking.unassigned", expectedVacancies);
        assertThat(report.hardScore()).isEqualTo(-expectedVacancies);
        assertThat(solved.getScore().hardScore()).isEqualTo(report.hardScore());
        assertThat(solved.getScore().softScore()).isEqualTo(report.softScore());
        assertThat(solved.getBookings().stream().filter(ResourceBooking::isPlaced))
                .hasSize(solved.getBookings().size() - expectedVacancies);
    }

    private BuiltProblem problem(int days, int contractMinutes, List<DemandRow> demands,
                                 List<AbsenceRow> absences) {
        UUID scenarioId = UUID.randomUUID();
        List<ConstraintConfigRow> rules = new ArrayList<>();
        List<ConstraintWeightRow> weights = new ArrayList<>();
        ConstraintService constraints = mock(ConstraintService.class);
        ConstraintConfigJpaRepository repository = mock(ConstraintConfigJpaRepository.class);
        when(repository.findByScenarioIdOrderByType(scenarioId)).thenReturn(List.of());
        doAnswer(invocation -> {
            ConstraintWeightInput input = invocation.getArgument(1);
            weights.add(new ConstraintWeightRow(input.constraintId(), input.level(), input.weight()));
            return null;
        }).when(constraints).upsertWeight(eq(scenarioId), any(ConstraintWeightInput.class));
        doAnswer(invocation -> {
            ConstraintConfigInput input = invocation.getArgument(1);
            rules.add(new ConstraintConfigRow(UUID.randomUUID(), input.type(), input.scope(), input.params(),
                    input.weight(), input.enabled()));
            return null;
        }).when(constraints).createConfig(eq(scenarioId), any(ConstraintConfigInput.class));

        WorkforcePolicyService policy = new WorkforcePolicyService(mock(WorkforceAccess.class), constraints,
                repository, ConstraintRegistry.standard(), mapper);
        policy.bootstrap(scenarioId);
        ResourceEntity employee = new ResourceEntity();
        employee.id = EMPLOYEE;
        employee.scenarioId = scenarioId;
        employee.attributes = mapper.createObjectNode().put("code", EMPLOYEE.toString())
                .put("quota_hebdo_minutes", contractMinutes);
        policy.employeeContract(employee);
        ResourceRow resource = new ResourceRow(EMPLOYEE, TYPE, "employee", "Alice", Effect.AVAILABLE,
                employee.attributes);
        TimeModelRow model = new TimeModelRow(UUID.randomUUID(), "Période", HorizonKind.DATED, null,
                FROM, FROM.plusDays(days - 1L), 0);
        FakeProblemSource source = new FakeProblemSource(model, grid(), List.of(resource), List.of(),
                demands, rules, weights) {
            @Override public List<AbsenceRow> absences(UUID ignored) { return absences; }
        };
        return SchoolFixture.builder(source).build(scenarioId);
    }

    private DemandRow demand(int day, String start, int minutes) {
        var placement = mapper.createObjectNode().put("strategy", "fixed")
                .put("date", FROM.plusDays(day).toString()).put("startTime", start);
        return new DemandRow(UUID.randomUUID(), "Service", minutes, 1, mapper.createObjectNode(), placement,
                List.of(new RequirementRow(UUID.randomUUID(), "Salle", TYPE, 1, mapper.createObjectNode())));
    }

    private List<SlotRow> grid() {
        List<SlotRow> slots = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            for (int ordinal = 0; ordinal < 48; ordinal++) {
                LocalTime start = LocalTime.MIDNIGHT.plusMinutes(ordinal * 30L);
                slots.add(new SlotRow(day, ordinal, start, start.plusMinutes(30)));
            }
        }
        return slots;
    }
}
