package com.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.CalendarWeekDto;
import com.planning.api.dto.Dtos.ResourceTypeDto;
import com.planning.api.dto.Dtos.ResourceTypeInput;
import com.planning.api.dto.Dtos.ScenarioDto;
import com.planning.api.dto.Dtos.ScenarioInput;
import com.planning.api.dto.Dtos.SlotDto;
import com.planning.api.dto.Dtos.TenantDto;
import com.planning.api.dto.Dtos.TimeModelDto;
import com.planning.api.dto.Dtos.TimeModelInput;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.model.HorizonKind;
import com.planning.repository.entity.Entities.CalendarWeekEntity;
import com.planning.repository.entity.Entities.ResourceTypeEntity;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.entity.Entities.SlotTemplateEntity;
import com.planning.repository.entity.Entities.TimeModelEntity;
import com.planning.repository.jpa.CalendarWeekJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SlotTemplateJpaRepository;
import com.planning.repository.jpa.TenantJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Tenants, types de ressources, modèles temporels et scénarios. */
@Service
@Transactional(readOnly = true)
public class ReferentialService {

    private final TenantJpaRepository tenants;
    private final ResourceTypeJpaRepository resourceTypes;
    private final TimeModelJpaRepository timeModels;
    private final SlotTemplateJpaRepository slots;
    private final ScenarioJpaRepository scenarios;
    private final CalendarWeekJpaRepository calendarWeeks;
    private final ObjectMapper mapper;

    public ReferentialService(TenantJpaRepository tenants, ResourceTypeJpaRepository resourceTypes,
                              TimeModelJpaRepository timeModels, SlotTemplateJpaRepository slots,
                              ScenarioJpaRepository scenarios, CalendarWeekJpaRepository calendarWeeks,
                              ObjectMapper mapper) {
        this.tenants = tenants;
        this.resourceTypes = resourceTypes;
        this.timeModels = timeModels;
        this.slots = slots;
        this.scenarios = scenarios;
        this.calendarWeeks = calendarWeeks;
        this.mapper = mapper;
    }

    public List<TenantDto> listTenants() {
        return tenants.findAll().stream()
                .map(entity -> new TenantDto(entity.id, entity.code, entity.label, entity.profile))
                .toList();
    }

    public List<ResourceTypeDto> listResourceTypes(UUID tenantId) {
        return resourceTypes.findByTenantIdOrderByLabel(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ResourceTypeDto createResourceType(UUID tenantId, ResourceTypeInput input) {
        ResourceTypeEntity entity = new ResourceTypeEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.code = input.code();
        entity.label = input.label();
        entity.attrSchema = input.attrSchema() == null ? mapper.createObjectNode() : input.attrSchema();
        return toDto(resourceTypes.save(entity));
    }

    public List<TimeModelDto> listTimeModels(UUID tenantId) {
        return timeModels.findByTenantIdOrderByLabel(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<SlotDto> listSlots(UUID timeModelId) {
        List<SlotTemplateEntity> rows =
                slots.findByTimeModelIdOrderByDayInCycleAscOrdinalAsc(timeModelId);
        List<SlotDto> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            SlotTemplateEntity row = rows.get(index);
            // L'index global n'existe qu'en mémoire (INVARIANT §5.3) : il est
            // recalculé ici pour l'affichage, jamais lu depuis la base.
            result.add(new SlotDto(index, row.dayInCycle, row.ordinal, row.startTime, row.endTime));
        }
        return result;
    }

    /**
     * Crée un modèle temporel et remplit sa grille régulière. Les pauses sont
     * exprimées en minutes insérées après un ordinal donné : c'est ce qui permet
     * de décrire une récréation ou une pause déjeuner sans sortir du générateur.
     */
    @Transactional
    public TimeModelDto createTimeModel(UUID tenantId, TimeModelInput input) {
        if (input.workingDays() > input.cycleDays()) {
            throw new InvalidConfigException(
                    "Le nombre de jours travaillés (%d) dépasse la longueur du cycle (%d)."
                            .formatted(input.workingDays(), input.cycleDays()));
        }

        TimeModelEntity model = new TimeModelEntity();
        model.id = UUID.randomUUID();
        model.tenantId = tenantId;
        model.label = input.label();
        model.horizonKind = HorizonKind.CYCLIC;
        model.cycleDays = input.cycleDays();
        model.maxGapMinutes = input.maxGapMinutes();
        timeModels.save(model);

        Map<Integer, Integer> breaks = input.breaks() == null ? Map.of()
                : input.breaks().stream().collect(Collectors.toMap(
                        b -> b.afterOrdinal(), b -> b.minutes(), Integer::sum));

        List<SlotTemplateEntity> grid = new ArrayList<>();
        for (int day = 0; day < input.workingDays(); day++) {
            LocalTime cursor = input.dayStart();
            for (int ordinal = 0; ordinal < input.slotsPerDay(); ordinal++) {
                SlotTemplateEntity slot = new SlotTemplateEntity();
                slot.id = UUID.randomUUID();
                slot.timeModelId = model.id;
                slot.dayInCycle = day;
                slot.ordinal = ordinal;
                slot.startTime = cursor;
                slot.endTime = cursor.plusMinutes(input.slotMinutes());
                grid.add(slot);
                cursor = slot.endTime.plusMinutes(breaks.getOrDefault(ordinal, 0));
            }
        }
        slots.saveAll(grid);
        return toDto(model);
    }

    public List<ScenarioDto> listScenarios(UUID tenantId) {
        Map<UUID, String> labels = timeModels.findAll().stream()
                .collect(Collectors.toMap(model -> model.id, model -> model.label));
        return scenarios.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toDto(entity, labels.get(entity.timeModelId)))
                .toList();
    }

    public ScenarioDto getScenario(UUID scenarioId) {
        ScenarioEntity entity = scenarios.findById(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        String label = timeModels.findById(entity.timeModelId)
                .map(model -> model.label)
                .orElse(null);
        return toDto(entity, label);
    }

    @Transactional
    public ScenarioDto createScenario(ScenarioInput input) {
        ScenarioEntity entity = new ScenarioEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = input.tenantId();
        entity.label = input.label();
        entity.timeModelId = input.timeModelId();
        entity.parentId = input.parentId();
        entity.profile = input.profile();
        entity.status = "DRAFT";
        entity.createdAt = Instant.now();
        scenarios.save(entity);
        return getScenario(entity.id);
    }

    /** L'horizon du scénario, pour restreindre le catalogue à ce qui y est proposable. */
    public HorizonKind horizonKindOf(UUID scenarioId) {
        ScenarioEntity scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        return timeModels.findById(scenario.timeModelId)
                .map(model -> model.horizonKind)
                .orElseThrow(() -> NotFoundException.of("Modèle temporel", scenario.timeModelId));
    }

    public List<CalendarWeekDto> listCalendarWeeks(UUID scenarioId) {
        return calendarWeeks.findByScenarioIdOrderByWeekNumber(scenarioId).stream()
                .map(entity -> new CalendarWeekDto(entity.weekNumber, entity.worked, entity.cycleOffset))
                .toList();
    }

    @Transactional
    public List<CalendarWeekDto> replaceCalendarWeeks(UUID scenarioId, List<CalendarWeekDto> weeks) {
        calendarWeeks.deleteAll(calendarWeeks.findByScenarioIdOrderByWeekNumber(scenarioId));
        List<CalendarWeekEntity> entities = weeks.stream().map(week -> {
            CalendarWeekEntity entity = new CalendarWeekEntity();
            entity.scenarioId = scenarioId;
            entity.weekNumber = week.weekNumber();
            entity.worked = week.worked();
            entity.cycleOffset = week.cycleOffset();
            return entity;
        }).toList();
        calendarWeeks.saveAll(entities);
        return listCalendarWeeks(scenarioId);
    }

    private ResourceTypeDto toDto(ResourceTypeEntity entity) {
        return new ResourceTypeDto(entity.id, entity.tenantId, entity.code, entity.label, entity.attrSchema);
    }

    private TimeModelDto toDto(TimeModelEntity entity) {
        int slotCount = slots.findByTimeModelIdOrderByDayInCycleAscOrdinalAsc(entity.id).size();
        return new TimeModelDto(entity.id, entity.tenantId, entity.label, entity.horizonKind,
                entity.cycleDays, entity.maxGapMinutes, slotCount);
    }

    private ScenarioDto toDto(ScenarioEntity entity, String timeModelLabel) {
        return new ScenarioDto(entity.id, entity.tenantId, entity.label, entity.timeModelId,
                timeModelLabel, entity.status, entity.parentId, entity.profile, entity.createdAt);
    }
}
