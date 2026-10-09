package com.planning.api.controller;

import com.planning.api.dto.WorkforceDtos.AbsenceDto;
import com.planning.api.dto.WorkforceDtos.AbsenceInput;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import com.planning.api.dto.WorkforceDtos.EmployeeInput;
import com.planning.api.dto.WorkforceDtos.OverviewDto;
import com.planning.api.dto.WorkforceDtos.PolicyDto;
import com.planning.api.dto.WorkforceDtos.ShiftDto;
import com.planning.api.dto.WorkforceDtos.ShiftInput;
import com.planning.api.dto.WorkforceDtos.WorkspaceCreated;
import com.planning.api.dto.WorkforceDtos.WorkspaceDto;
import com.planning.api.dto.WorkforceDtos.WorkspaceInput;
import com.planning.application.workforce.WorkforceAbsenceService;
import com.planning.application.workforce.WorkforceEmployeeService;
import com.planning.application.workforce.WorkforceOverviewService;
import com.planning.application.workforce.WorkforcePolicyService;
import com.planning.application.workforce.WorkforceShiftService;
import com.planning.application.workforce.WorkspaceBootstrapService;
import com.planning.domain.model.BusinessProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workforce")
public class WorkforceController {
    private final WorkspaceBootstrapService workspaces;
    private final WorkforceOverviewService overview;
    private final WorkforceEmployeeService employees;
    private final WorkforceShiftService shifts;
    private final WorkforceAbsenceService absences;
    private final WorkforcePolicyService policy;

    public WorkforceController(WorkspaceBootstrapService workspaces, WorkforceOverviewService overview,
                               WorkforceEmployeeService employees, WorkforceShiftService shifts,
                               WorkforceAbsenceService absences, WorkforcePolicyService policy) {
        this.workspaces = workspaces;
        this.overview = overview;
        this.employees = employees;
        this.shifts = shifts;
        this.absences = absences;
        this.policy = policy;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> list(@RequestParam(required = false) BusinessProfile profile) {
        return overview.workspaces(profile);
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceCreated create(@Valid @RequestBody WorkspaceInput input) {
        return workspaces.create(input);
    }

    @GetMapping("/scenarios/{scenarioId}/overview")
    public OverviewDto overview(@PathVariable UUID scenarioId) {
        return overview.overview(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDto createEmployee(@PathVariable UUID scenarioId, @Valid @RequestBody EmployeeInput input) {
        return employees.create(scenarioId, input);
    }

    @PutMapping("/scenarios/{scenarioId}/employees/{employeeId}")
    public EmployeeDto updateEmployee(@PathVariable UUID scenarioId, @PathVariable UUID employeeId,
                                      @Valid @RequestBody EmployeeInput input) {
        return employees.update(scenarioId, employeeId, input);
    }

    @DeleteMapping("/scenarios/{scenarioId}/employees/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployee(@PathVariable UUID scenarioId, @PathVariable UUID employeeId) {
        employees.delete(scenarioId, employeeId);
    }

    @PostMapping("/scenarios/{scenarioId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftDto createShift(@PathVariable UUID scenarioId, @Valid @RequestBody ShiftInput input) {
        return shifts.create(scenarioId, input);
    }

    @PutMapping("/scenarios/{scenarioId}/shifts/{shiftId}")
    public ShiftDto updateShift(@PathVariable UUID scenarioId, @PathVariable UUID shiftId,
                                @Valid @RequestBody ShiftInput input) {
        return shifts.update(scenarioId, shiftId, input);
    }

    @DeleteMapping("/scenarios/{scenarioId}/shifts/{shiftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable UUID scenarioId, @PathVariable UUID shiftId) {
        shifts.delete(scenarioId, shiftId);
    }

    @GetMapping("/scenarios/{scenarioId}/absences")
    public List<AbsenceDto> absences(@PathVariable UUID scenarioId) {
        return absences.list(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/absences")
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceDto createAbsence(@PathVariable UUID scenarioId, @Valid @RequestBody AbsenceInput input) {
        return absences.create(scenarioId, input);
    }

    @DeleteMapping("/scenarios/{scenarioId}/absences/{absenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAbsence(@PathVariable UUID scenarioId, @PathVariable UUID absenceId) {
        absences.delete(scenarioId, absenceId);
    }

    @PutMapping("/scenarios/{scenarioId}/policy")
    public PolicyDto updatePolicy(@PathVariable UUID scenarioId, @Valid @RequestBody PolicyDto input) {
        return policy.update(scenarioId, input);
    }
}
