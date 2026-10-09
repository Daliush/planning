package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.planning.domain.builder.WeightBook;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.GenericConstraintProvider;
import com.planning.domain.model.Period;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.port.Rows.ConstraintWeightRow;
import com.planning.domain.solver.ExplanationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceScoreConsistencyTest {
    @Test
    void lesReglesDEquipeRestentCoherentesPendantLesMouvementsDeDatesEtDeRessources() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        var first = fixture.resource("Alex", "{\"quota_hebdo_minutes\":960}");
        var second = fixture.resource("Sam", "{\"quota_hebdo_minutes\":1440}");
        for (int day : new int[]{0, 1, 2, 3, 5, 6, 12}) {
            fixture.work(first, day, day == 5 ? 22 : 8, 480);
        }
        UUID demand = UUID.randomUUID();
        for (int occurrence = 0; occurrence < 2; occurrence++) {
            var activity = fixture.activity(demand, occurrence, occurrence + 7, 8, 120);
            fixture.booking(activity, first);
            fixture.booking(activity, second);
        }
        fixture.rule(new RestMinConstraint(), new RestMinConstraint.Params(660), 2);
        fixture.rule(new WorkDaysMaxConstraint(), new WorkDaysMaxConstraint.Params(3), 2);
        fixture.rule(new ContractLoadConstraint(), new ContractLoadConstraint.Params(), 2);
        fixture.rule(new WeekendFairnessConstraint(), new WeekendFairnessConstraint.Params(), 2);
        fixture.rule(new ResourceConsistencyConstraint(), new ResourceConsistencyConstraint.Params(), 1);
        fixture.rule(new MinLoadConstraint(), new MinLoadConstraint.Params(60, Period.DAY), 1);

        Set<String> active = Set.of(RestMinConstraint.TYPE, WorkDaysMaxConstraint.TYPE,
                ContractLoadConstraint.TYPE, WeekendFairnessConstraint.TYPE,
                ResourceConsistencyConstraint.TYPE, MinLoadConstraint.TYPE,
                ResourceConflictConstraint.TYPE, BookingUnassignedConstraint.TYPE);
        ConstraintRegistry registry = ConstraintRegistry.standard();
        List<ConstraintWeightRow> rows = registry.all().stream().map(plugin ->
                new ConstraintWeightRow(plugin.type(), plugin.defaultLevel(), active.contains(plugin.type()) ? 1 : 0)).toList();
        WeightBook weights = WeightBook.of(registry, rows);
        Schedule problem = fixture.schedule();
        problem.setConstraintWeightOverrides(weights.overrides());

        // Les configurations synthétiques assurent aussi l'explication du dur structurel.
        var configs = new java.util.ArrayList<>(problem.getConfigs());
        configs.add(fixture.rule(new BookingUnassignedConstraint(), new com.planning.domain.constraint.NoParams(), 1));
        configs.add(fixture.rule(new ResourceConflictConstraint(), new com.planning.domain.constraint.NoParams(), 1));
        problem.setConfigs(configs);
        SolverConfig config = new SolverConfig().withSolutionClass(Schedule.class)
                .withEntityClasses(ScheduledActivity.class, ResourceBooking.class)
                .withConstraintProviderClass(GenericConstraintProvider.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withTerminationSpentLimit(Duration.ofSeconds(1));
        Schedule solved = SolverFactory.<Schedule>create(config).buildSolver().solve(problem);
        solved.setHorizon(fixture.horizon());

        ExplanationService.Totals totals = new ExplanationService(registry).totals(solved, weights);
        assertThat(solved.getScore().hardScore()).isEqualTo(-totals.hard());
        assertThat(solved.getScore().softScore()).isEqualTo(-totals.soft());
    }
}
