package com.planning.application.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.api.dto.Dtos.AvailabilityDto;
import com.planning.api.dto.WorkforceDtos.EmployeeDto;
import com.planning.api.dto.WorkforceDtos.EmployeeInput;
import com.planning.api.dto.WorkforceDtos.RecurringAvailabilityInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.Effect;
import com.planning.repository.entity.Entities.ResourceAvailabilityEntity;
import com.planning.repository.entity.Entities.ResourceEntity;
import com.planning.repository.jpa.AbsenceJpaRepository;
import com.planning.repository.jpa.ResourceAvailabilityJpaRepository;
import com.planning.repository.jpa.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkforceEmployeeService {
    private final WorkforceAccess access;
    private final ResourceJpaRepository resources;
    private final ResourceAvailabilityJpaRepository availabilities;
    private final AbsenceJpaRepository absences;
    private final WorkforcePolicyService policy;
    private final ObjectMapper mapper;

    public WorkforceEmployeeService(WorkforceAccess access, ResourceJpaRepository resources,
                                    ResourceAvailabilityJpaRepository availabilities,
                                    AbsenceJpaRepository absences, WorkforcePolicyService policy,
                                    ObjectMapper mapper) {
        this.access = access;
        this.resources = resources;
        this.availabilities = availabilities;
        this.absences = absences;
        this.policy = policy;
        this.mapper = mapper;
    }

    public List<EmployeeDto> list(WorkforceAccess.Context context) {
        return resources.findByScenarioIdAndTypeIdOrderByLabel(context.scenario().id, context.employeeType().id)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public EmployeeDto create(UUID scenarioId, EmployeeInput input) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        ResourceEntity employee = new ResourceEntity();
        employee.id = UUID.randomUUID();
        employee.scenarioId = scenarioId;
        employee.typeId = context.employeeType().id;
        employee.defaultEffect = Effect.AVAILABLE;
        employee.attributes = mapper.createObjectNode().put("code", employee.id.toString());
        apply(employee, input);
        resources.save(employee);
        replaceAvailability(employee, input.availability());
        policy.employeeContract(employee);
        access.changed(context);
        return toDto(employee);
    }

    @Transactional
    public EmployeeDto update(UUID scenarioId, UUID employeeId, EmployeeInput input) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        ResourceEntity employee = access.employee(context, employeeId);
        apply(employee, input);
        resources.save(employee);
        replaceAvailability(employee, input.availability());
        policy.employeeContract(employee);
        access.changed(context);
        return toDto(employee);
    }

    @Transactional
    public void delete(UUID scenarioId, UUID employeeId) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        ResourceEntity employee = access.employee(context, employeeId);
        policy.deleteEmployeeContract(scenarioId, employee.attributes.path("code").asText());
        absences.deleteAll(absences.findByResourceId(employeeId));
        availabilities.deleteAll(availabilities.findByResourceIdOrderByPriority(employeeId));
        resources.delete(employee);
        access.changed(context);
    }

    private void apply(ResourceEntity employee, EmployeeInput input) {
        if (input == null) throw new InvalidConfigException("La fiche de l'employé est obligatoire.");
        String label = WorkforceAccess.text(input.label(), "Le nom", 120);
        if (input.skills() == null || input.skills().isEmpty() || input.skills().size() > 30) {
            throw new InvalidConfigException("Renseignez de 1 à 30 compétences pour cet employé.");
        }
        List<String> skills = input.skills().stream()
                .map(skill -> WorkforceAccess.text(skill, "La compétence", 80)).distinct().toList();
        Integer minutes = input.contractWeeklyMinutes();
        if (minutes == null || minutes < 0 || minutes > 10080 || minutes % 30 != 0) {
            throw new InvalidConfigException("La durée hebdomadaire du contrat doit être un multiple de 30 minutes entre 0 et 168 h.");
        }
        int overtime = input.overtimeWeeklyMinutes() != null ? input.overtimeWeeklyMinutes()
                : employee.attributes == null ? 0 : employee.attributes.path("heures_supp_hebdo_minutes").asInt(0);
        if (overtime < 0 || overtime > 10080 || overtime % 30 != 0 || minutes + overtime > 10080) {
            throw new InvalidConfigException("Les heures supplémentaires autorisées doivent être un multiple de 30 minutes positif ou nul. Le contrat et ce supplément ne peuvent pas dépasser 168 h par semaine.");
        }
        int absenceDaily = input.absenceDailyMinutes() != null ? input.absenceDailyMinutes()
                : employee.attributes != null && employee.attributes.hasNonNull("minutes_absence_jour")
                ? employee.attributes.path("minutes_absence_jour").asInt() : defaultAbsenceDailyMinutes(minutes);
        if (absenceDaily < 0 || absenceDaily > 1440 || absenceDaily % 30 != 0) {
            throw new InvalidConfigException("La durée déduite par jour d'absence doit être un multiple de 30 minutes entre 0 et 24 h.");
        }
        validateAvailability(input.availability());
        employee.label = label;
        ObjectNode attributes = employee.attributes != null && employee.attributes.isObject()
                ? (ObjectNode) employee.attributes.deepCopy() : mapper.createObjectNode();
        if (!attributes.hasNonNull("code")) attributes.put("code", employee.id.toString());
        attributes.set("competences", mapper.valueToTree(skills));
        attributes.put("quota_hebdo_minutes", minutes);
        attributes.put("heures_supp_hebdo_minutes", overtime);
        attributes.put("minutes_absence_jour", absenceDaily);
        employee.attributes = attributes;
    }

    private void validateAvailability(List<RecurringAvailabilityInput> inputs) {
        if (inputs == null) return;
        if (inputs.size() > 100) throw new InvalidConfigException("Limitez les disponibilités récurrentes à 100 plages.");
        for (RecurringAvailabilityInput input : inputs) {
            if (input == null || input.effect() == null || input.dayInCycle() != null
                    && (input.dayInCycle() < 0 || input.dayInCycle() > 6)) {
                throw new InvalidConfigException("Une disponibilité exige un effet et un jour de semaine de 0 à 6, ou tous les jours.");
            }
            if (input.startTime() == null ^ input.endTime() == null) {
                throw new InvalidConfigException("Renseignez les deux heures de disponibilité, ou laissez les deux vides pour une journée entière.");
            }
            if (input.startTime() != null && (!aligned(input.startTime()) || !aligned(input.endTime())
                    || input.startTime().equals(input.endTime()))) {
                throw new InvalidConfigException("Une disponibilité exige deux heures distinctes par pas de 30 minutes. Une fin avant le début désigne le lendemain.");
            }
            if (input.priority() != null && (input.priority() < 0 || input.priority() > 100)) {
                throw new InvalidConfigException("La priorité d'une disponibilité doit être comprise entre 0 et 100.");
            }
        }
    }

    private void replaceAvailability(ResourceEntity employee, List<RecurringAvailabilityInput> inputs) {
        if (inputs == null) return;
        availabilities.deleteAll(availabilities.findByResourceIdOrderByPriority(employee.id));
        employee.defaultEffect = inputs.stream().anyMatch(input -> input.effect() == Effect.AVAILABLE)
                ? Effect.UNAVAILABLE : Effect.AVAILABLE;
        List<ResourceAvailabilityEntity> replacement = inputs.stream().map(input -> {
            ResourceAvailabilityEntity rule = new ResourceAvailabilityEntity();
            rule.id = UUID.randomUUID();
            rule.resourceId = employee.id;
            rule.effect = input.effect();
            rule.dayInCycle = input.dayInCycle();
            rule.startTime = input.startTime();
            rule.endTime = input.endTime();
            rule.priority = input.priority() == null ? 0 : input.priority();
            return rule;
        }).toList();
        availabilities.saveAll(replacement);
        resources.save(employee);
    }

    private EmployeeDto toDto(ResourceEntity employee) {
        List<String> skills = new ArrayList<>();
        employee.attributes.path("competences").forEach(skill -> skills.add(skill.asText()));
        List<AvailabilityDto> availability = availabilities.findByResourceIdOrderByPriority(employee.id).stream()
                .map(rule -> new AvailabilityDto(rule.id, rule.effect, rule.dayInCycle,
                        rule.startTime, rule.endTime, rule.priority)).toList();
        return new EmployeeDto(employee.id, employee.label, List.copyOf(skills),
                employee.attributes.path("quota_hebdo_minutes").asInt(),
                employee.attributes.path("heures_supp_hebdo_minutes").asInt(0),
                employee.attributes.path("minutes_absence_jour").asInt(
                        defaultAbsenceDailyMinutes(employee.attributes.path("quota_hebdo_minutes").asInt())), availability);
    }

    static int defaultAbsenceDailyMinutes(int contractWeeklyMinutes) {
        return (int) Math.min(1440, Math.round(contractWeeklyMinutes / 150.0) * 30);
    }

    static boolean aligned(LocalTime time) {
        return time != null && time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
