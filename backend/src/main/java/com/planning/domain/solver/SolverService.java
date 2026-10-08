package com.planning.domain.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.planning.domain.constraint.GenericConstraintProvider;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;

import java.time.Duration;

/**
 * Construit un solveur par résolution.
 *
 * <p>Une {@code SolverFactory} par job, et non une pour l'application : le budget
 * de temps est propre à chaque demande, et rien n'est partagé entre workers, ce
 * qui est exactement le régime visé par le scaling horizontal de l'ADR-004.
 *
 * <p>{@code solve()} rend <strong>un autre objet</strong> que celui qui lui a été
 * passé (§8.1).
 */
public class SolverService {

    private final EnvironmentMode environmentMode;

    public SolverService(EnvironmentMode environmentMode) {
        this.environmentMode = environmentMode;
    }

    public Schedule solve(Schedule problem, int timeLimitSeconds) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(ScheduledActivity.class, ResourceBooking.class)
                .withConstraintProviderClass(GenericConstraintProvider.class)
                .withEnvironmentMode(environmentMode)
                .withTerminationSpentLimit(Duration.ofSeconds(timeLimitSeconds));

        SolverFactory<Schedule> factory = SolverFactory.create(config);
        Solver<Schedule> solver = factory.buildSolver();
        return solver.solve(problem);
    }
}
