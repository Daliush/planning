package com.planning.application.workforce;

import com.planning.api.dto.Dtos;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import com.planning.api.dto.WorkforceDtos.KpisDto;
import com.planning.api.dto.WorkforceDtos.OverviewDto;
import com.planning.api.dto.WorkforceDtos.PeriodDto;
import com.planning.api.dto.WorkforceDtos.ShiftDto;
import com.planning.api.dto.WorkforceDtos.WorkspaceDto;
import com.planning.application.ConstraintService;
import com.planning.application.SolveService;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.HorizonKind;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.TenantJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkforceOverviewService {
    private final WorkforceAccess access;
    private final WorkforceEmployeeService employees;
    private final WorkforceShiftService shifts;
    private final WorkforceAbsenceService absences;
    private final WorkforcePolicyService policy;
    private final ConstraintService constraints;
    private final SolveService solves;
    private final TenantJpaRepository tenants;
    private final ScenarioJpaRepository scenarios;
    private final TimeModelJpaRepository models;
    private final ResourceTypeJpaRepository types;
    private final ResourceJpaRepository resources;

    public WorkforceOverviewService(WorkforceAccess access, WorkforceEmployeeService employees,
                                    WorkforceShiftService shifts, WorkforceAbsenceService absences,
                                    WorkforcePolicyService policy, ConstraintService constraints,
                                    SolveService solves, TenantJpaRepository tenants,
                                    ScenarioJpaRepository scenarios, TimeModelJpaRepository models,
                                    ResourceTypeJpaRepository types, ResourceJpaRepository resources) {
        this.access = access;
        this.employees = employees;
        this.shifts = shifts;
        this.absences = absences;
        this.policy = policy;
        this.constraints = constraints;
        this.solves = solves;
        this.tenants = tenants;
        this.scenarios = scenarios;
        this.models = models;
        this.types = types;
        this.resources = resources;
    }

    public List<WorkspaceDto> workspaces(BusinessProfile profile) {
        if (profile != null) WorkforceAccess.businessProfile(profile);
        List<WorkspaceDto> workspaces = new ArrayList<>();
        tenants.findAll().stream()
                .filter(tenant -> "RESTAURANT".equals(tenant.profile) || "COMPANY".equals(tenant.profile))
                .filter(tenant -> profile == null || profile.name().equals(tenant.profile))
                .forEach(tenant -> {
                    var employeeTypeIds = types.findByTenantIdOrderByLabel(tenant.id).stream()
                            .filter(type -> "employee".equals(type.code)).map(type -> type.id)
                            .collect(Collectors.toSet());
                    scenarios.findByTenantIdOrderByCreatedAtDesc(tenant.id).stream()
                        .filter(scenario -> scenario.profile != null && scenario.profile.name().equals(tenant.profile))
                        .forEach(scenario -> models.findById(scenario.timeModelId)
                                .filter(model -> tenant.id.equals(model.tenantId) && model.horizonKind == HorizonKind.DATED)
                                .ifPresent(model -> {
                                    int count = (int) resources.findByScenarioIdOrderByLabel(scenario.id).stream()
                                            .filter(resource -> employeeTypeIds.contains(resource.typeId)).count();
                                    workspaces.add(new WorkspaceDto(tenant.id, scenario.id, tenant.label,
                                            scenario.profile, model.horizonFrom, model.horizonTo, count));
                                }));
                });
        return workspaces.stream().sorted(Comparator.comparing(WorkspaceDto::label)).toList();
    }

    public OverviewDto overview(UUID scenarioId) {
        WorkforceAccess.Context context = access.context(scenarioId);
        List<EmployeeDto> staff = employees.list(context);
        List<ShiftDto> services = shifts.list(scenarioId);
        var leaves = absences.list(scenarioId);
        List<Dtos.SolveJobDto> jobs = solves.list(scenarioId);
        Dtos.SolveJobDto latest = jobs.isEmpty() ? null : jobs.getFirst();
        boolean outdated = latest != null && "DRAFT".equals(context.scenario().status);
        List<Dtos.AssignmentDto> assignments = latest != null && "DONE".equals(latest.status()) && !outdated
                ? solves.assignments(latest.id(), null, null) : List.of();
        Dtos.SolveReportDto report = latest != null && "DONE".equals(latest.status()) && !outdated
                ? solves.report(latest.id()) : null;
        Map<UUID, ShiftDto> shiftById = services.stream().collect(Collectors.toMap(ShiftDto::id, shift -> shift));
        int required = services.stream().mapToInt(shift -> shift.roles().stream().mapToInt(role -> role.quantity()).sum()).sum();
        int assigned = (int) assignments.stream().filter(booking -> booking.resourceId() != null).count();
        long requiredMinutes = services.stream().mapToLong(shift -> (long) shift.durationMinutes()
                * shift.roles().stream().mapToInt(role -> role.quantity()).sum()).sum();
        long assignedMinutes = assignments.stream().filter(booking -> booking.resourceId() != null)
                .mapToLong(booking -> shiftById.containsKey(booking.demandId())
                        ? shiftById.get(booking.demandId()).durationMinutes() : 0).sum();
        long absentDays = leaves.stream().mapToLong(leave -> ChronoUnit.DAYS.between(leave.from(), leave.to()) + 1).sum();
        KpisDto kpis = new KpisDto(staff.size(), services.size(), required, assigned,
                Math.max(0, required - assigned), required == 0 ? 0 : (int) Math.round(100.0 * assigned / required),
                requiredMinutes, assignedMinutes, absentDays);
        var tenant = context.tenant();
        var model = context.model();
        List<Dtos.ResourceTypeDto> resourceTypes = types.findByTenantIdOrderByLabel(tenant.id).stream()
                .map(type -> new Dtos.ResourceTypeDto(type.id, type.tenantId, type.code, type.label, type.attrSchema)).toList();
        return new OverviewDto(new Dtos.TenantDto(tenant.id, tenant.code, tenant.label, tenant.profile),
                scenarioDto(context.scenario(), model.label), new PeriodDto(model.horizonFrom, model.horizonTo, 30),
                resourceTypes, staff, services, leaves, latest, assignments, report, kpis,
                constraints.listConfigs(scenarioId), policy.get(scenarioId), outdated);
    }

    private Dtos.ScenarioDto scenarioDto(ScenarioEntity scenario, String modelLabel) {
        return new Dtos.ScenarioDto(scenario.id, scenario.tenantId, scenario.label, scenario.timeModelId,
                modelLabel, scenario.status, scenario.parentId, scenario.profile, scenario.createdAt);
    }
}
