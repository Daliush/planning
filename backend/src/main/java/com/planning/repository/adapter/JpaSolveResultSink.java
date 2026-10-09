package com.planning.repository.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.model.SolveReport;
import com.planning.domain.port.Rows.AssignmentRow;
import com.planning.domain.port.SolveResultSink;
import com.planning.repository.entity.Entities.AssignmentEntity;
import com.planning.repository.entity.Entities.SolveJobEntity;
import com.planning.repository.jpa.AssignmentJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SolveJobJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaSolveResultSink implements SolveResultSink {

    private final SolveJobJpaRepository jobs;
    private final AssignmentJpaRepository assignments;
    private final ScenarioJpaRepository scenarios;
    private final ObjectMapper mapper;

    public JpaSolveResultSink(SolveJobJpaRepository jobs, AssignmentJpaRepository assignments,
                              ScenarioJpaRepository scenarios, ObjectMapper mapper) {
        this.jobs = jobs;
        this.assignments = assignments;
        this.scenarios = scenarios;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void started(UUID jobId) {
        SolveJobEntity job = job(jobId);
        job.status = "RUNNING";
        job.startedAt = Instant.now();
        jobs.save(job);
        updateScenarioStatus(job.scenarioId, "SOLVING");
    }

    @Override
    @Transactional
    public void succeeded(UUID jobId, int hardScore, int softScore,
                          List<AssignmentRow> rows, SolveReport report) {
        assignments.deleteBySolveJobId(jobId);
        assignments.saveAll(rows.stream().map(row -> toEntity(jobId, row)).toList());

        SolveJobEntity job = job(jobId);
        job.status = "DONE";
        job.hardScore = hardScore;
        job.softScore = softScore;
        job.finishedAt = Instant.now();
        job.report = mapper.valueToTree(report);
        jobs.save(job);
        updateScenarioStatus(job.scenarioId, "SOLVED");
    }

    @Override
    @Transactional
    public void failed(UUID jobId, String errorCode, String errorDetail) {
        SolveJobEntity job = job(jobId);
        job.status = "FAILED";
        job.errorCode = errorCode;
        job.errorDetail = errorDetail;
        job.finishedAt = Instant.now();
        jobs.save(job);
        updateScenarioStatus(job.scenarioId, "DRAFT");
    }

    /**
     * PIÈGE — jamais {@code getReferenceById} ici. Il rend un proxy Hibernate, et
     * ces entités exposent des champs publics : un proxy n'intercepte que les
     * appels de méthode, si bien qu'une lecture directe de {@code job.scenarioId}
     * renverrait le champ du proxy, resté nul. Le chargement doit être réel.
     */
    private SolveJobEntity job(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> NotFoundException.of("Job de résolution", jobId));
    }

    private void updateScenarioStatus(UUID scenarioId, String status) {
        scenarios.findById(scenarioId).ifPresent(scenario -> {
            // Un scénario publié est figé : une résolution ne doit pas le déclasser.
            if (!"PUBLISHED".equals(scenario.status) && !"ARCHIVED".equals(scenario.status)) {
                scenario.status = status;
                scenarios.save(scenario);
            }
        });
    }

    private AssignmentEntity toEntity(UUID jobId, AssignmentRow row) {
        AssignmentEntity entity = new AssignmentEntity();
        entity.id = UUID.randomUUID();
        entity.solveJobId = jobId;
        entity.demandId = row.demandId();
        entity.occurrence = row.occurrence();
        entity.resourceId = row.resourceId();
        entity.role = row.role();
        entity.dayInCycle = row.dayInCycle();
        entity.startTime = row.startTime();
        entity.endTime = row.endTime();
        entity.pinned = row.pinned();
        entity.startDate = row.date();
        entity.endDate = row.endDate();
        return entity;
    }
}
