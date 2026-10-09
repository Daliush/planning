package com.planning.domain.builder;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.exception.IncompatibleTimeModelException;
import com.planning.domain.model.*;
import com.planning.domain.model.Period;
import com.planning.domain.port.Rows.*;
import com.planning.domain.solver.ExplanationService;
import com.planning.domain.solver.SolverService;
import com.planning.domain.time.TimeHorizon;
import com.planning.support.FakeProblemSource;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DatedWorkforceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 8); // jeudi, pas lundi
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID TYPE = UUID.randomUUID();

    private TimeModelRow model() {
        return new TimeModelRow(UUID.randomUUID(), "Service", HorizonKind.DATED, null,
                FROM, FROM.plusDays(2), 0);
    }
    private List<SlotRow> grid() {
        List<SlotRow> rows = new ArrayList<>();
        for (int day = 0; day < 7; day++) for (int k = 0; k < 48; k++) {
            LocalTime start = LocalTime.MIDNIGHT.plusMinutes(k * 30L);
            rows.add(new SlotRow(day, k, start, start.plusMinutes(30)));
        }
        return rows;
    }
    private FakeProblemSource source(boolean qualified, List<AbsenceRow> absences) {
        ResourceRow employee = new ResourceRow(EMPLOYEE, TYPE, "employee", "Alice", Effect.AVAILABLE,
                SchoolFixture.json("{\"competences\":[\"cuisine\"]}"));
        var requirement = new RequirementRow(UUID.randomUUID(), "Cuisine", TYPE, 1,
                SchoolFixture.json(qualified ? "{}" : "{\"contains\":{\"path\":\"/competences\",\"value\":\"bar\"}}"));
        var demand = new DemandRow(UUID.randomUUID(), "Nuit", 240, 1, SchoolFixture.json("{}"),
                SchoolFixture.json("{\"strategy\":\"fixed\",\"date\":\"2026-10-08\",\"startTime\":\"22:00\"}"),
                List.of(requirement));
        return new FakeProblemSource(model(), grid(), List.of(employee), List.of(), List.of(demand),
                List.of(), SchoolFixture.weights()) {
            @Override public List<AbsenceRow> absences(UUID scenarioId) { return absences; }
        };
    }

    @Test void laDateFixeeEtLaFinLeLendemainSontReelles() {
        var built = SchoolFixture.builder(source(true, List.of())).build(UUID.randomUUID());
        var activity = built.schedule().getActivities().getFirst();
        assertThat(activity.getValidStarts()).hasSize(1);
        var start = activity.getValidStarts().getFirst();
        assertThat(start.date()).isEqualTo(FROM);
        assertThat(start.weekday()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(activity.endDateTime(start)).isEqualTo(FROM.plusDays(1).atTime(2, 0));
        assertThat(built.schedule().getHorizon().periodBuckets().stream().filter(p -> p.period() == Period.DAY)).hasSize(3);
    }

    @Test void uneAbsenceLeLendemainInterditLaFinDuServiceDeNuit() {
        var built = SchoolFixture.builder(source(true,
                List.of(new AbsenceRow(EMPLOYEE, FROM.plusDays(1), FROM.plusDays(1)))))
                .build(UUID.randomUUID());
        var solved = new SolverService(EnvironmentMode.FULL_ASSERT).solve(built.schedule(), 1);
        assertThat(solved.getBookings().getFirst().getResource()).isNull();
        var totals = new ExplanationService(ConstraintRegistry.standard()).totals(solved, built.weights());
        assertThat(solved.getScore().hardScore()).isEqualTo(-totals.hard());
        assertThat(solved.getScore().softScore()).isEqualTo(-totals.soft());
    }

    @Test void unPosteSansCompetenceDisponibleResteVisibleSansFaireEchouerLeCalcul() {
        var built = SchoolFixture.builder(source(false, List.of())).build(UUID.randomUUID());
        var solved = new SolverService(EnvironmentMode.FULL_ASSERT).solve(built.schedule(), 1);
        assertThat(solved.getBookings()).hasSize(1);
        assertThat(solved.getBookings().getFirst().getResource()).isNull();
        assertThat(solved.getScore().hardScore()).isNegative();
    }

    @Test void uneDisponibiliteDuJeudiNeDevientPasUneDisponibiliteDuLundi() {
        TimeHorizon horizon = new HorizonFactory().create(model(), grid());
        ResourceRow employee = new ResourceRow(EMPLOYEE, TYPE, "employee", "Alice", Effect.UNAVAILABLE,
                SchoolFixture.json("{}"));
        var profile = new AvailabilityCompiler().compile(employee,
                List.of(new AvailabilityRow(EMPLOYEE, Effect.AVAILABLE, 3, null, null, 0)), horizon.slots());
        assertThat(profile.allowed().get(0, 48).cardinality()).isEqualTo(48);
        assertThat(profile.allowed().get(48, 144).cardinality()).isZero();
    }

    @Test void laFinInclutLaPauseAutoriseeEtLesChevauchementsSontRefuses() {
        var cyclic = new TimeModelRow(UUID.randomUUID(), "Pause", HorizonKind.CYCLIC, 1, null, null, 15);
        var horizon = new HorizonFactory().create(cyclic, List.of(
                new SlotRow(0,0,LocalTime.of(9,0),LocalTime.of(10,0)),
                new SlotRow(0,1,LocalTime.of(10,15),LocalTime.of(11,15))));
        var activity = new ScheduledActivity(UUID.randomUUID(), UUID.randomUUID(), "Pause", 0,
                120, 2, SchoolFixture.json("{}"), horizon.slots());
        assertThat(activity.endDateTime(horizon.slots().getFirst()).toLocalTime()).isEqualTo(LocalTime.of(11,15));
        assertThatThrownBy(() -> new HorizonFactory().create(cyclic, List.of(
                new SlotRow(0,0,LocalTime.of(9,0),LocalTime.of(10,0)),
                new SlotRow(0,1,LocalTime.of(9,30),LocalTime.of(10,30)))))
                .isInstanceOf(IncompatibleTimeModelException.class);
        assertThatThrownBy(() -> new HorizonFactory().create(new TimeModelRow(UUID.randomUUID(),
                "Invalide", HorizonKind.CYCLIC, 1, null, null, -1), horizon.slots().stream()
                .map(s -> new SlotRow(s.dayInCycle(), s.ordinal(), s.start(), s.end())).toList()))
                .isInstanceOf(IncompatibleTimeModelException.class);
    }

    @Test void uneConfigurationStructurelleNePeutPasDoublerLeRapport() {
        var original = source(true, List.of());
        var invalid = new FakeProblemSource(model(), grid(), original.resources(null), List.of(),
                original.demands(null), List.of(new ConstraintConfigRow(UUID.randomUUID(),
                "resource.conflict", SchoolFixture.json("{}"), SchoolFixture.json("{}"), 1, true)), List.of());
        assertThatThrownBy(() -> SchoolFixture.builder(invalid).build(UUID.randomUUID()))
                .hasMessageContaining("structurelle");
    }
}
