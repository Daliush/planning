package com.planning.api.controller;

import com.planning.api.dto.Dtos.AssignmentDto;
import com.planning.api.dto.Dtos.ScheduleViewDto;
import com.planning.api.dto.Dtos.SolveJobDto;
import com.planning.api.dto.Dtos.SolveReportDto;
import com.planning.api.dto.Dtos.SolveRequest;
import com.planning.application.SolveService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SolveController {

    private final SolveService solves;

    public SolveController(SolveService solves) {
        this.solves = solves;
    }

    /** 202 : le job est accepté, pas terminé. La progression se suit par polling. */
    @PostMapping("/scenarios/{scenarioId}/solve-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SolveJobDto submit(@PathVariable UUID scenarioId,
                              @RequestBody(required = false) SolveRequest request) {
        return solves.submit(scenarioId, request);
    }

    @GetMapping("/scenarios/{scenarioId}/solve-jobs")
    public List<SolveJobDto> list(@PathVariable UUID scenarioId) {
        return solves.list(scenarioId);
    }

    @GetMapping("/solve-jobs/{jobId}")
    public SolveJobDto get(@PathVariable UUID jobId) {
        return solves.get(jobId);
    }

    /** Le rapport d'explicabilité (§16.6). */
    @GetMapping("/solve-jobs/{jobId}/report")
    public SolveReportDto report(@PathVariable UUID jobId) {
        return solves.report(jobId);
    }

    /**
     * « L'emploi du temps de la 6e A », « celui de Jean » et « l'occupation de la
     * salle B12 » sont trois filtres sur cette même liste plate (§6.14).
     */
    @GetMapping("/solve-jobs/{jobId}/assignments")
    public List<AssignmentDto> assignments(@PathVariable UUID jobId,
                                           @RequestParam(required = false) UUID resourceId,
                                           @RequestParam(required = false) UUID demandId) {
        return solves.assignments(jobId, resourceId, demandId);
    }

    @GetMapping("/solve-jobs/{jobId}/schedule")
    public ScheduleViewDto schedule(@PathVariable UUID jobId) {
        return solves.schedule(jobId);
    }
}
