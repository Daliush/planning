package com.planning.config;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.domain.builder.AvailabilityCompiler;
import com.planning.domain.builder.HorizonFactory;
import com.planning.domain.builder.ProblemBuilder;
import com.planning.domain.builder.ProblemValidator;
import com.planning.domain.builder.StartsCompiler;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.ParamsCodec;
import com.planning.domain.placement.PlacementRegistry;
import com.planning.domain.port.ProblemSource;
import com.planning.domain.port.SolveResultSink;
import com.planning.domain.solver.ExplanationService;
import com.planning.domain.solver.SolveJobRunner;
import com.planning.domain.solver.SolverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Le câblage du domaine.
 *
 * <p>Aucune classe du domaine ne porte d'annotation Spring : elles restent des
 * objets Java ordinaires, instanciables dans un test sans contexte ni base de
 * données. C'est le pendant concret de la règle de dépendance du §4.3.
 */
@Configuration
public class DomainConfiguration {

    @Bean
    public ConstraintRegistry constraintRegistry() {
        return ConstraintRegistry.standard();
    }

    @Bean
    public PlacementRegistry placementRegistry() {
        return PlacementRegistry.standard();
    }

    @Bean
    public ParamsCodec paramsCodec(ObjectMapper mapper) {
        return new ParamsCodec(mapper);
    }

    @Bean
    public HorizonFactory horizonFactory() {
        return new HorizonFactory();
    }

    @Bean
    public AvailabilityCompiler availabilityCompiler() {
        return new AvailabilityCompiler();
    }

    @Bean
    public StartsCompiler startsCompiler() {
        return new StartsCompiler();
    }

    @Bean
    public ProblemValidator problemValidator() {
        return new ProblemValidator();
    }

    @Bean
    public ProblemBuilder problemBuilder(ProblemSource source, ObjectMapper mapper,
                                         ConstraintRegistry registry, ParamsCodec paramsCodec,
                                         PlacementRegistry placements, HorizonFactory horizons,
                                         AvailabilityCompiler availabilityCompiler,
                                         StartsCompiler startsCompiler, ProblemValidator validator) {
        return new ProblemBuilder(source, mapper, registry, paramsCodec, placements, horizons,
                availabilityCompiler, startsCompiler, validator);
    }

    @Bean
    public SolverService solverService(PlanningProperties properties) {
        return new SolverService(EnvironmentMode.valueOf(properties.solver().environmentMode()));
    }

    @Bean
    public ExplanationService explanationService(ConstraintRegistry registry) {
        return new ExplanationService(registry);
    }

    /** Les workers sont CPU-bound et sans état (ADR-004). */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService solveWorkers(PlanningProperties properties) {
        return Executors.newFixedThreadPool(properties.solver().workerThreads(), runnable -> {
            Thread thread = new Thread(runnable, "planning-solver");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public SolveJobRunner solveJobRunner(ProblemBuilder builder, SolverService solver,
                                         ExplanationService explanation, SolveResultSink sink,
                                         ExecutorService solveWorkers) {
        return new SolveJobRunner(builder, solver, explanation, sink, solveWorkers);
    }
}
