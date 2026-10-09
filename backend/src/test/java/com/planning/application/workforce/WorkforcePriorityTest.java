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
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Résout avec les règles réellement produites par la façade, contrats et garde-fous compris. */
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

    @Test
    void unContratDeTrenteCinqHeuresExigeTrenteCinqHeuresQuandLesPostesExistent() {
        LocalDate monday = FROM.plusDays(4);
        BuiltProblem built = problem(monday, 7, 2100, 0, workingDays(monday, 420), List.of(), List.of());
        Schedule solved = solve(built);
        assertThat(assignedMinutes(solved)).isEqualTo(2100);
        SolveReport report = coherentReport(solved, built);
        assertThat(report.feasible()).isTrue();
        assertThat(report.hardViolations()).isEmpty();
    }

    @Test
    void troisHeuresSupplementairesAutorisentTrenteHuitHeuresEtPasDavantage() {
        LocalDate monday = FROM.plusDays(4);
        List<DemandRow> demands = new ArrayList<>(workingDays(monday, 420));
        demands.add(demand(monday.plusDays(5), "09:00", 180));
        BuiltProblem built = problem(monday, 7, 2100, 180, demands, List.of(), List.of());
        Schedule solved = solve(built);
        assertThat(assignedMinutes(solved)).isEqualTo(2280);
        assertThat(coherentReport(solved, built).feasible()).isTrue();

        // Deux postes vacants de durées différentes coûtent chacun une vacance.
        // On vérifie ici la borne, sans imposer lequel des postes le solveur choisit.
        demands.add(demand(monday.plusDays(5), "12:00", 30));
        built = problem(monday, 7, 2100, 180, demands, List.of(), List.of());
        solved = solve(built);
        assertThat(assignedMinutes(solved)).isBetween(2100, 2280);
        assertOnlyVacancies(solved, built, 1);
        assertThat(coherentReport(solved, built).hardViolations()).extracting(Violation::constraintId)
                .containsOnly("booking.unassigned");
    }

    @Test
    void unJourDAbsenceDeduitSeptHeuresDuContratSansLeverLInterdiction() {
        LocalDate monday = FROM.plusDays(4);
        LocalDate wednesday = monday.plusDays(2);
        BuiltProblem built = problem(monday, 7, 2100, 0, workingDays(monday, 420),
                List.of(new AbsenceRow(EMPLOYEE, wednesday, wednesday)), List.of());
        Schedule solved = solve(built);
        assertThat(assignedMinutes(solved)).isEqualTo(1680);
        assertOnlyVacancies(solved, built, 1);
        assertThat(coherentReport(solved, built).hardViolations()).extracting(Violation::constraintId)
                .containsOnly("booking.unassigned");
    }

    @Test
    void leCreditDAbsencePreserveLaMargeSupplementaireEtNestCompteQuUneFois() {
        LocalDate monday = FROM.plusDays(4);
        LocalDate wednesday = monday.plusDays(2);
        List<DemandRow> demands = new ArrayList<>(workingDays(monday, 420));
        demands.add(demand(monday.plusDays(5), "09:00", 180));
        BuiltProblem built = problem(monday, 7, 2100, 180, demands,
                List.of(new AbsenceRow(EMPLOYEE, wednesday, wednesday),
                        new AbsenceRow(EMPLOYEE, wednesday, wednesday)), List.of());
        Schedule solved = solve(built);
        assertThat(assignedMinutes(solved)).isEqualTo(1860);
        assertOnlyVacancies(solved, built, 1);
        assertThat(coherentReport(solved, built).hardViolations()).extracting(Violation::constraintId)
                .containsOnly("booking.unassigned");
    }

    @Test
    void laDureeParJourPersonnaliseeModifieLeMinimumEtLePlafond() {
        LocalDate monday = FROM.plusDays(4);
        LocalDate wednesday = monday.plusDays(2);
        List<DemandRow> demands = new ArrayList<>(workingDays(monday, 420));
        demands.add(demand(monday.plusDays(5), "09:00", 60));
        demands.add(demand(monday.plusDays(5), "10:00", 30));
        BuiltProblem built = problem(monday, 7, 2100, 0, 360, demands,
                List.of(new AbsenceRow(EMPLOYEE, wednesday, wednesday)), List.of());
        Schedule solved = solve(built);
        assertThat(assignedMinutes(solved)).isEqualTo(1740);
        assertOnlyVacancies(solved, built, 2);
        assertThat(coherentReport(solved, built).hardViolations()).extracting(Violation::constraintId)
                .containsOnly("booking.unassigned");
    }

    @Test
    void unVolumeDePostesInsuffisantSignaleLeContratIncompletSansCreerDeService() {
        LocalDate monday = FROM.plusDays(4);
        BuiltProblem built = problem(monday, 7, 2100, 0, workingDays(monday, 360), List.of(), List.of());
        Schedule solved = solve(built);
        assertThat(solved.getActivities()).hasSize(5);
        assertThat(assignedMinutes(solved)).isEqualTo(1800);
        SolveReport report = coherentReport(solved, built);
        assertThat(report.feasible()).isFalse();
        assertThat(report.hardViolations()).extracting(Violation::constraintId).containsOnly("load.min");
        assertThat(report.penaltyByConstraint()).containsEntry("load.min", 300);
        assertThat(report.hardViolations().getFirst().context()).containsAllEntriesOf(
                Map.of("totalMinutes", 1800, "minMinutes", 2100));
    }

    @Test
    void uneIndisponibiliteHoraireNePeutPasEtreEnfreintePourRemplirLeContrat() {
        BuiltProblem built = problem(FROM, 3, 2100, 0,
                List.of(demand(0, "09:00", 30), demand(0, "10:00", 30)), List.of(),
                List.of(new AvailabilityRow(EMPLOYEE, Effect.UNAVAILABLE, 3,
                        LocalTime.of(9, 0), LocalTime.of(10, 0), 0)));
        Schedule solved = solve(built);
        assertOnlyVacancies(solved, built, 1);
        assertThat(solved.getBookings().stream().filter(ResourceBooking::isPlaced))
                .allMatch(booking -> booking.startTime().equals(LocalTime.of(10, 0)));
        assertThat(coherentReport(solved, built).penaltyByConstraint()).containsEntry("load.min", 2070);
    }

    @Test
    void uneIndisponibiliteDeNuitCouvreLeLendemainSansBloquerLaFinDePlage() {
        BuiltProblem built = problem(FROM, 3, 60, 0,
                List.of(demand(0, "22:00", 30), demand(1, "01:30", 30), demand(1, "02:00", 30)),
                List.of(), List.of(new AvailabilityRow(EMPLOYEE, Effect.UNAVAILABLE, 3,
                        LocalTime.of(22, 0), LocalTime.of(2, 0), 0)));
        Schedule solved = solve(built);
        assertOnlyVacancies(solved, built, 2);
        assertThat(assignedMinutes(solved)).isEqualTo(30);
        assertThat(solved.getBookings().stream().filter(ResourceBooking::isPlaced))
                .allMatch(booking -> booking.startTime().equals(LocalTime.of(2, 0)));
    }

    @Test
    void uneIndisponibiliteHoraireTousLesJoursBloqueChaqueDate() {
        BuiltProblem built = problem(FROM, 3, 2100, 0,
                List.of(demand(0, "09:00", 30), demand(1, "09:00", 30)), List.of(),
                List.of(new AvailabilityRow(EMPLOYEE, Effect.UNAVAILABLE, null,
                        LocalTime.of(9, 0), LocalTime.of(10, 0), 0)));
        assertOnlyVacancies(solve(built), built, 2);
    }

    @Test
    void laGenerationMetierNeChangePasLeMinimumSoupleDuScolaire() {
        BuiltProblem school = SchoolFixture.builder().build(UUID.randomUUID());
        Schedule solved = solve(school);
        SolveReport report = coherentReport(solved, school);
        assertThat(report.hardViolations()).extracting(Violation::constraintId).doesNotContain("load.min");
        assertThat(ConstraintRegistry.standard().get("load.min").defaultLevel())
                .isEqualTo(com.planning.domain.model.Level.SOFT);
    }

    private Schedule solve(BuiltProblem built) {
        return new SolverService(EnvironmentMode.FULL_ASSERT).solve(built.schedule(), 1);
    }

    private void assertOnlyVacancies(Schedule solved, BuiltProblem built, int expectedVacancies) {
        assertThat(solved.getActivities()).allMatch(activity -> activity.getStart() != null);
        assertThat(solved.getBookings().stream().filter(booking -> booking.getResource() == null))
                .hasSize(expectedVacancies);
        SolveReport report = coherentReport(solved, built);
        assertThat(report.hardViolations()).allMatch(violation ->
                "booking.unassigned".equals(violation.constraintId()) || "load.min".equals(violation.constraintId()));
        assertThat(report.penaltyByConstraint()).containsEntry("booking.unassigned", expectedVacancies);
        assertThat(report.hardScore()).isEqualTo(-expectedVacancies
                - report.penaltyByConstraint().getOrDefault("load.min", 0));
        assertThat(solved.getBookings().stream().filter(ResourceBooking::isPlaced))
                .hasSize(solved.getBookings().size() - expectedVacancies);
    }

    private SolveReport coherentReport(Schedule solved, BuiltProblem built) {
        SolveReport report = new ExplanationService(ConstraintRegistry.standard()).report(solved, built.weights());
        assertThat(solved.getScore().hardScore()).isEqualTo(report.hardScore());
        assertThat(solved.getScore().softScore()).isEqualTo(report.softScore());
        return report;
    }

    private int assignedMinutes(Schedule solved) {
        return solved.getBookings().stream().filter(ResourceBooking::isPlaced)
                .mapToInt(ResourceBooking::durationMinutes).sum();
    }

    private List<DemandRow> workingDays(LocalDate monday, int minutes) {
        List<DemandRow> demands = new ArrayList<>();
        for (int day = 0; day < 5; day++) demands.add(demand(monday.plusDays(day), "09:00", minutes));
        return demands;
    }

    private BuiltProblem problem(int days, int contractMinutes, List<DemandRow> demands,
                                 List<AbsenceRow> absences) {
        return problem(FROM, days, contractMinutes, 0, demands, absences, List.of());
    }

    private BuiltProblem problem(LocalDate from, int days, int contractMinutes, int overtimeMinutes,
                                 List<DemandRow> demands, List<AbsenceRow> absences,
                                 List<AvailabilityRow> availability) {
        return problem(from, days, contractMinutes, overtimeMinutes,
                WorkforceEmployeeService.defaultAbsenceDailyMinutes(contractMinutes), demands, absences, availability);
    }

    private BuiltProblem problem(LocalDate from, int days, int contractMinutes, int overtimeMinutes,
                                 int absenceDailyMinutes, List<DemandRow> demands, List<AbsenceRow> absences,
                                 List<AvailabilityRow> availability) {
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
                .put("quota_hebdo_minutes", contractMinutes).put("heures_supp_hebdo_minutes", overtimeMinutes)
                .put("minutes_absence_jour", absenceDailyMinutes);
        policy.employeeContract(employee);
        ResourceRow resource = new ResourceRow(EMPLOYEE, TYPE, "employee", "Alice", Effect.AVAILABLE,
                employee.attributes);
        TimeModelRow model = new TimeModelRow(UUID.randomUUID(), "Période", HorizonKind.DATED, null,
                from, from.plusDays(days - 1L), 0);
        FakeProblemSource source = new FakeProblemSource(model, grid(), List.of(resource), availability,
                demands, rules, weights) {
            @Override public List<AbsenceRow> absences(UUID ignored) { return absences; }
        };
        return SchoolFixture.builder(source).build(scenarioId);
    }

    private DemandRow demand(int day, String start, int minutes) {
        return demand(FROM.plusDays(day), start, minutes);
    }

    private DemandRow demand(LocalDate date, String start, int minutes) {
        var placement = mapper.createObjectNode().put("strategy", "fixed")
                .put("date", date.toString()).put("startTime", start);
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
