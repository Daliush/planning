package com.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.AssignmentDto;
import com.planning.api.dto.Dtos.ResourceBriefDto;
import com.planning.api.dto.Dtos.ScheduleViewDto;
import com.planning.api.dto.Dtos.SolveJobDto;
import com.planning.api.dto.Dtos.SolveReportDto;
import com.planning.api.dto.Dtos.SolveRequest;
import com.planning.config.PlanningProperties;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.model.SolveReport;
import com.planning.domain.solver.SolveJobRunner;
import com.planning.repository.entity.Entities.AssignmentEntity;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.entity.Entities.SolveJobEntity;
import com.planning.repository.jpa.AssignmentJpaRepository;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SolveJobJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Le pilotage des résolutions.
 *
 * <p>ADR-004 : cette classe crée un job {@code QUEUED} et rend la main
 * immédiatement. Aucune résolution ne se déroule dans le fil de la requête HTTP.
 */
@Service
@Transactional(readOnly = true)
public class SolveService {

    private final SolveJobJpaRepository jobs;
    private final AssignmentJpaRepository assignments;
    private final ScenarioJpaRepository scenarios;
    private final ResourceJpaRepository resources;
    private final ResourceTypeJpaRepository resourceTypes;
    private final DemandJpaRepository demands;
    private final TimeModelJpaRepository timeModels;
    private final ReferentialService referential;
    private final SolveJobRunner runner;
    private final ConstraintRegistry registry;
    private final PlanningProperties properties;
    private final ObjectMapper mapper;

    public SolveService(SolveJobJpaRepository jobs, AssignmentJpaRepository assignments,
                        ScenarioJpaRepository scenarios, ResourceJpaRepository resources,
                        ResourceTypeJpaRepository resourceTypes, DemandJpaRepository demands,
                        TimeModelJpaRepository timeModels, ReferentialService referential,
                        SolveJobRunner runner, ConstraintRegistry registry,
                        PlanningProperties properties, ObjectMapper mapper) {
        this.jobs = jobs;
        this.assignments = assignments;
        this.scenarios = scenarios;
        this.resources = resources;
        this.resourceTypes = resourceTypes;
        this.demands = demands;
        this.timeModels = timeModels;
        this.referential = referential;
        this.runner = runner;
        this.registry = registry;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Transactional
    public SolveJobDto submit(UUID scenarioId, SolveRequest request) {
        ScenarioEntity scenario = scenarios.findByIdForUpdate(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        if ("PUBLISHED".equals(scenario.status) || "ARCHIVED".equals(scenario.status)) {
            // INVARIANT §6.2 : un scénario publié est figé. Le dériver plutôt que
            // l'écraser préserve la lisibilité des plannings déjà diffusés.
            throw new InvalidConfigException(
                    "Le scénario « %s » est publié : le dériver pour relancer une résolution."
                            .formatted(scenario.label));
        }
        if (jobs.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .anyMatch(job -> "QUEUED".equals(job.status) || "RUNNING".equals(job.status))) {
            throw new InvalidConfigException("Un calcul est déjà en cours pour ce planning.");
        }

        int limit = request == null || request.timeLimitSeconds() == null
                ? properties.solver().defaultTimeLimitSeconds()
                : request.timeLimitSeconds();
        if (limit < 1 || limit > properties.solver().maxTimeLimitSeconds()) {
            throw new InvalidConfigException(
                    "Le budget de résolution doit être compris entre 1 et %d secondes."
                            .formatted(properties.solver().maxTimeLimitSeconds()));
        }

        SolveJobEntity job = new SolveJobEntity();
        job.id = UUID.randomUUID();
        job.scenarioId = scenarioId;
        job.status = "QUEUED";
        job.timeLimitSeconds = limit;
        job.createdAt = Instant.now();
        jobs.save(job);

        // Le worker ne démarre qu'une fois la ligne visible : lancé dans la
        // transaction, il pourrait interroger un job qui n'existe pas encore et
        // échouer avant même d'avoir commencé.
        UUID jobId = job.id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.submit(jobId, scenarioId, limit);
            }
        });
        return toDto(job);
    }

    public List<SolveJobDto> list(UUID scenarioId) {
        return jobs.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .map(this::toDto)
                .toList();
    }

    public SolveJobDto get(UUID jobId) {
        return toDto(job(jobId));
    }

    public SolveReportDto report(UUID jobId) {
        SolveJobEntity job = job(jobId);
        if (job.report == null) {
            throw new NotFoundException(
                    "Le job %s n'a pas encore produit de rapport (statut %s)."
                            .formatted(jobId, job.status));
        }
        SolveReport report = mapper.convertValue(job.report, SolveReport.class);
        Map<String, String> labels = registry.all().stream()
                .collect(Collectors.toMap(ConstraintPlugin::type, ConstraintPlugin::label));
        return new SolveReportDto(report, labels);
    }

    public List<AssignmentDto> assignments(UUID jobId, UUID resourceId, UUID demandId) {
        return assignmentsOf(job(jobId)).stream()
                .filter(dto -> resourceId == null || resourceId.equals(dto.resourceId()))
                .filter(dto -> demandId == null || demandId.equals(dto.demandId()))
                .toList();
    }

    /** Tout ce qu'il faut pour dessiner la grille, en une requête. */
    public ScheduleViewDto schedule(UUID jobId) {
        SolveJobEntity job = job(jobId);
        ScenarioEntity scenario = scenarios.findById(job.scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", job.scenarioId));
        int cycleDays = timeModels.findById(scenario.timeModelId)
                .map(model -> model.cycleDays == null
                        ? Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(model.horizonFrom, model.horizonTo) + 1)
                        : model.cycleDays)
                .orElse(7);

        Map<UUID, String> codeByType = resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));

        List<ResourceBriefDto> briefs = resources.findByScenarioIdOrderByLabel(job.scenarioId).stream()
                .map(entity -> new ResourceBriefDto(entity.id, entity.label, codeByType.get(entity.typeId)))
                .toList();

        return new ScheduleViewDto(toDto(job), cycleDays,
                referential.listSlots(scenario.timeModelId), briefs, assignmentsOf(job));
    }

    private List<AssignmentDto> assignmentsOf(SolveJobEntity job) {
        Map<UUID, String> demandLabels = demands.findByScenarioIdOrderByLabel(job.scenarioId).stream()
                .collect(Collectors.toMap(entity -> entity.id, entity -> entity.label));
        Map<UUID, String> codeByType = resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));
        Map<UUID, String[]> resourceInfo = new LinkedHashMap<>();
        resources.findByScenarioIdOrderByLabel(job.scenarioId).forEach(entity ->
                resourceInfo.put(entity.id, new String[]{entity.label, codeByType.get(entity.typeId)}));

        return assignments.findBySolveJobIdOrderByDayInCycleAscStartTimeAsc(job.id).stream()
                .map(entity -> toDto(entity, demandLabels, resourceInfo))
                .toList();
    }

    private SolveJobEntity job(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> NotFoundException.of("Job de résolution", jobId));
    }

    private AssignmentDto toDto(AssignmentEntity entity, Map<UUID, String> demandLabels,
                                Map<UUID, String[]> resourceInfo) {
        String[] resource = entity.resourceId == null ? null : resourceInfo.get(entity.resourceId);
        return new AssignmentDto(entity.id, entity.demandId,
                demandLabels.get(entity.demandId), entity.occurrence,
                entity.resourceId,
                resource == null ? null : resource[0],
                resource == null ? null : resource[1],
                entity.role, entity.dayInCycle, entity.startTime, entity.endTime, entity.pinned,
                entity.startDate, entity.endDate);
    }

    private SolveJobDto toDto(SolveJobEntity entity) {
        return new SolveJobDto(entity.id, entity.scenarioId, entity.status, entity.timeLimitSeconds,
                entity.hardScore, entity.softScore,
                entity.hardScore != null && entity.hardScore == 0,
                entity.createdAt, entity.startedAt, entity.finishedAt,
                entity.errorCode, entity.errorDetail);
    }
}
