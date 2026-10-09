package com.planning.application.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.WorkforceDtos.ShiftDto;
import com.planning.api.dto.WorkforceDtos.ShiftInput;
import com.planning.api.dto.WorkforceDtos.ShiftRoleDto;
import com.planning.api.dto.WorkforceDtos.ShiftRoleInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.repository.entity.Entities.DemandEntity;
import com.planning.repository.entity.Entities.RequirementEntity;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.RequirementJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkforceShiftService {
    private final WorkforceAccess access;
    private final DemandJpaRepository demands;
    private final RequirementJpaRepository requirements;
    private final ObjectMapper mapper;

    public WorkforceShiftService(WorkforceAccess access, DemandJpaRepository demands,
                                 RequirementJpaRepository requirements, ObjectMapper mapper) {
        this.access = access;
        this.demands = demands;
        this.requirements = requirements;
        this.mapper = mapper;
    }

    public List<ShiftDto> list(UUID scenarioId) {
        return demands.findByScenarioIdOrderByLabel(scenarioId).stream().map(this::toDto)
                .sorted(Comparator.comparing(ShiftDto::date).thenComparing(ShiftDto::startTime)).toList();
    }

    @Transactional
    public ShiftDto create(UUID scenarioId, ShiftInput input) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        DemandEntity demand = new DemandEntity();
        demand.id = UUID.randomUUID();
        demand.scenarioId = scenarioId;
        save(context, demand, input);
        return toDto(demand);
    }

    @Transactional
    public ShiftDto update(UUID scenarioId, UUID demandId, ShiftInput input) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        DemandEntity demand = demand(context, demandId);
        save(context, demand, input);
        return toDto(demand);
    }

    @Transactional
    public void delete(UUID scenarioId, UUID demandId) {
        WorkforceAccess.Context context = access.mutable(scenarioId);
        DemandEntity demand = demand(context, demandId);
        requirements.deleteAll(requirements.findByDemandIdOrderByRole(demandId));
        demands.delete(demand);
        access.changed(context);
    }

    private DemandEntity demand(WorkforceAccess.Context context, UUID demandId) {
        DemandEntity demand = demands.findById(demandId)
                .orElseThrow(() -> NotFoundException.of("Service", demandId));
        if (!context.scenario().id.equals(demand.scenarioId)) {
            throw new InvalidConfigException("Ce service n'appartient pas à ce planning.");
        }
        return demand;
    }

    private void save(WorkforceAccess.Context context, DemandEntity demand, ShiftInput input) {
        if (input == null) throw new InvalidConfigException("Le service à planifier est obligatoire.");
        String label = WorkforceAccess.text(input.label(), "Le nom du service", 120);
        WorkforceAccess.date(context, input.date());
        int duration = duration(input);
        if (Boolean.TRUE.equals(input.nextDay()) && !LocalTime.MIDNIGHT.equals(input.endTime())) {
            WorkforceAccess.date(context, input.date().plusDays(1));
        }
        validateRoles(input.roles());
        demand.label = label;
        demand.durationMinutes = duration;
        demand.occurrences = 1;
        demand.attributes = mapper.createObjectNode().put("date", input.date().toString())
                .put("service", label).put("nextDay", Boolean.TRUE.equals(input.nextDay()));
        demand.placement = mapper.createObjectNode().put("strategy", "fixed")
                .put("date", input.date().toString()).put("startTime", input.startTime().toString());
        demands.save(demand);
        requirements.deleteAll(requirements.findByDemandIdOrderByRole(demand.id));
        List<RequirementEntity> replacement = input.roles().stream().map(inputRole -> {
            RequirementEntity role = new RequirementEntity();
            role.id = UUID.randomUUID();
            role.demandId = demand.id;
            role.role = inputRole.role().trim();
            role.quantity = inputRole.quantity();
            role.typeId = context.employeeType().id;
            role.criteria = inputRole.skill() == null || inputRole.skill().isBlank()
                    ? mapper.createObjectNode()
                    : mapper.createObjectNode().set("contains", mapper.createObjectNode()
                            .put("path", "/competences").put("value", inputRole.skill().trim()));
            return role;
        }).toList();
        requirements.saveAll(replacement);
        access.changed(context);
    }

    static int duration(ShiftInput input) {
        if (!WorkforceEmployeeService.aligned(input.startTime()) || !WorkforceEmployeeService.aligned(input.endTime())) {
            throw new InvalidConfigException("Les heures du service doivent respecter le pas de 30 minutes.");
        }
        long minutes = Duration.between(input.startTime(), input.endTime()).toMinutes();
        if (Boolean.TRUE.equals(input.nextDay())) minutes += 1440;
        if (minutes <= 0 || minutes > 1440) {
            throw new InvalidConfigException("Un service doit durer de 30 minutes à 24 h ; indiquez « lendemain » s'il se termine après minuit.");
        }
        return (int) minutes;
    }

    private void validateRoles(List<ShiftRoleInput> roles) {
        if (roles == null || roles.isEmpty() || roles.size() > 20) {
            throw new InvalidConfigException("Renseignez de 1 à 20 rôles pour ce service.");
        }
        Set<String> names = new HashSet<>();
        int positions = 0;
        for (ShiftRoleInput role : roles) {
            if (role == null) throw new InvalidConfigException("Un rôle du service est vide.");
            String name = WorkforceAccess.text(role.role(), "Le rôle", 80);
            if (!names.add(name)) throw new InvalidConfigException("Chaque rôle doit apparaître une seule fois dans le service.");
            if (role.quantity() == null || role.quantity() < 1 || role.quantity() > 50) {
                throw new InvalidConfigException("Le nombre de personnes par rôle doit être compris entre 1 et 50.");
            }
            if (role.skill() != null && !role.skill().isBlank()) WorkforceAccess.text(role.skill(), "La compétence requise", 80);
            positions += role.quantity();
        }
        if (positions > 100) throw new InvalidConfigException("Un service ne peut pas dépasser 100 postes.");
    }

    private ShiftDto toDto(DemandEntity demand) {
        LocalDate date = LocalDate.parse(demand.placement.path("date").asText());
        LocalTime start = LocalTime.parse(demand.placement.path("startTime").asText());
        List<ShiftRoleDto> roles = requirements.findByDemandIdOrderByRole(demand.id).stream()
                .map(role -> new ShiftRoleDto(role.role, role.quantity,
                        role.criteria.path("contains").path("value").isMissingNode() ? null
                                : role.criteria.path("contains").path("value").asText())).toList();
        return new ShiftDto(demand.id, demand.label, date, start, start.plusMinutes(demand.durationMinutes),
                start.toSecondOfDay() / 60 + demand.durationMinutes >= 1440, demand.durationMinutes, roles);
    }
}
