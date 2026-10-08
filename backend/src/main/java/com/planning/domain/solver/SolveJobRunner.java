package com.planning.domain.solver;

import com.planning.domain.builder.ProblemBuilder;
import com.planning.domain.builder.ProblemBuilder.BuiltProblem;
import com.planning.domain.exception.PlanningException;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.SolveReport;
import com.planning.domain.port.Rows.AssignmentRow;
import com.planning.domain.port.SolveResultSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Le worker (ADR-004).
 *
 * <p>Une résolution prend de quelques secondes à plusieurs minutes : elle ne
 * s'exécute jamais dans une requête HTTP. L'API crée un job {@code QUEUED} et rend
 * un 202 ; le worker consomme, résout, écrit les affectations et le rapport, puis
 * bascule le job en {@code DONE}.
 *
 * <p>Les workers sont CPU-bound et sans état, ce qui les rend scalables
 * horizontalement — le bon axe pour un SaaS multi-tenant, et de toute façon le
 * seul disponible en édition Community.
 */
public class SolveJobRunner {

    private static final Logger log = LoggerFactory.getLogger(SolveJobRunner.class);

    private final ProblemBuilder builder;
    private final SolverService solver;
    private final ExplanationService explanation;
    private final SolveResultSink sink;
    private final ExecutorService workers;

    public SolveJobRunner(ProblemBuilder builder, SolverService solver,
                          ExplanationService explanation, SolveResultSink sink,
                          ExecutorService workers) {
        this.builder = builder;
        this.solver = solver;
        this.explanation = explanation;
        this.sink = sink;
        this.workers = workers;
    }

    public void submit(UUID jobId, UUID scenarioId, int timeLimitSeconds) {
        workers.submit(() -> run(jobId, scenarioId, timeLimitSeconds));
    }

    void run(UUID jobId, UUID scenarioId, int timeLimitSeconds) {
        try {
            sink.started(jobId);
            log.info("Job {} : construction du problème pour le scénario {}", jobId, scenarioId);

            BuiltProblem problem = builder.build(scenarioId);
            Schedule solved = solver.solve(problem.schedule(), timeLimitSeconds);
            solved.setHorizon(problem.schedule().getHorizon());

            SolveReport report = explanation.report(solved, problem.weights());
            log.info("Job {} terminé : score {} — {} violation(s) dure(s)",
                    jobId, solved.getScore(), report.hardViolations().size());

            sink.succeeded(jobId,
                    solved.getScore().hardScore(),
                    solved.getScore().softScore(),
                    assignmentsOf(solved),
                    report);

        } catch (PlanningException e) {
            // Message métier déjà exploitable : il part tel quel vers l'utilisateur.
            log.warn("Job {} en échec ({}) : {}", jobId, e.code(), e.getMessage());
            sink.failed(jobId, e.code(), e.getMessage());
        } catch (Exception e) {
            log.error("Job {} en échec inattendu", jobId, e);
            sink.failed(jobId, "INTERNAL_ERROR", String.valueOf(e.getMessage()));
        }
    }

    private List<AssignmentRow> assignmentsOf(Schedule solved) {
        return solved.getBookings().stream()
                .map(this::toRow)
                .toList();
    }

    private AssignmentRow toRow(ResourceBooking booking) {
        var activity = booking.getActivity();
        return new AssignmentRow(
                activity.getDemandId(),
                activity.getOccurrence(),
                booking.getResource() == null ? null : booking.getResource().id(),
                booking.getRole(),
                activity.getStart().dayInCycle(),
                // INVARIANT §6.14 : on stocke l'heure réelle, jamais un index de
                // créneau. Les exports et les archives ne dépendent alors plus du
                // modèle temporel du moment.
                activity.getStart().start(),
                activity.getStart().start().plusMinutes(activity.getDurationMinutes()),
                booking.isPinned());
    }
}
