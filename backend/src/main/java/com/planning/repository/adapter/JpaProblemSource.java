package com.planning.repository.adapter;

import com.planning.domain.exception.NotFoundException;
import com.planning.domain.port.ProblemSource;
import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ConstraintConfigRow;
import com.planning.domain.port.Rows.ConstraintWeightRow;
import com.planning.domain.port.Rows.DemandRow;
import com.planning.domain.port.Rows.RequirementRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.domain.port.Rows.SlotRow;
import com.planning.domain.port.Rows.TimeModelRow;
import com.planning.domain.port.Rows.AbsenceRow;
import com.planning.repository.jpa.AbsenceJpaRepository;
import com.planning.repository.entity.Entities.RequirementEntity;
import com.planning.repository.entity.Entities.ResourceTypeEntity;
import com.planning.repository.entity.Entities.ScenarioEntity;
import com.planning.repository.jpa.ConstraintConfigJpaRepository;
import com.planning.repository.jpa.ConstraintWeightJpaRepository;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.RequirementJpaRepository;
import com.planning.repository.jpa.ResourceAvailabilityJpaRepository;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import com.planning.repository.jpa.ScenarioJpaRepository;
import com.planning.repository.jpa.SlotTemplateJpaRepository;
import com.planning.repository.jpa.TimeModelJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le seul point où la persistance parle au domaine.
 *
 * <p>Chaque méthode rend des {@code record} immuables : aucune entité Hibernate ne
 * franchit cette frontière (INVARIANT §4.4). Sans cette barrière, une relation
 * paresseuse touchée pendant la résolution déclencherait une requête SQL au milieu
 * de la boucle chaude — ou une {@code LazyInitializationException} hors
 * transaction.
 */
@Component
@Transactional(readOnly = true)
public class JpaProblemSource implements ProblemSource {

    private final ScenarioJpaRepository scenarios;
    private final TimeModelJpaRepository timeModels;
    private final SlotTemplateJpaRepository slots;
    private final ResourceJpaRepository resources;
    private final ResourceTypeJpaRepository resourceTypes;
    private final ResourceAvailabilityJpaRepository availabilities;
    private final DemandJpaRepository demands;
    private final RequirementJpaRepository requirements;
    private final ConstraintConfigJpaRepository configs;
    private final ConstraintWeightJpaRepository weights;
    private final AbsenceJpaRepository absences;

    public JpaProblemSource(ScenarioJpaRepository scenarios, TimeModelJpaRepository timeModels,
                            SlotTemplateJpaRepository slots, ResourceJpaRepository resources,
                            ResourceTypeJpaRepository resourceTypes,
                            ResourceAvailabilityJpaRepository availabilities,
                            DemandJpaRepository demands, RequirementJpaRepository requirements,
                            ConstraintConfigJpaRepository configs,
                            ConstraintWeightJpaRepository weights, AbsenceJpaRepository absences) {
        this.scenarios = scenarios;
        this.timeModels = timeModels;
        this.slots = slots;
        this.resources = resources;
        this.resourceTypes = resourceTypes;
        this.availabilities = availabilities;
        this.demands = demands;
        this.requirements = requirements;
        this.configs = configs;
        this.weights = weights;
        this.absences = absences;
    }

    @Override
    public List<AbsenceRow> absences(UUID scenarioId) {
        return absences.findByScenarioIdOrderByFromDateAsc(scenarioId).stream()
                .map(entity -> new AbsenceRow(entity.resourceId, entity.fromDate, entity.toDate)).toList();
    }

    @Override
    public TimeModelRow timeModel(UUID scenarioId) {
        ScenarioEntity scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> NotFoundException.of("Scénario", scenarioId));
        return timeModels.findById(scenario.timeModelId)
                .map(entity -> new TimeModelRow(entity.id, entity.label, entity.horizonKind,
                        entity.cycleDays, entity.horizonFrom, entity.horizonTo, entity.maxGapMinutes))
                .orElseThrow(() -> NotFoundException.of("Modèle temporel", scenario.timeModelId));
    }

    @Override
    public List<SlotRow> slots(UUID timeModelId) {
        return slots.findByTimeModelIdOrderByDayInCycleAscOrdinalAsc(timeModelId).stream()
                .map(entity -> new SlotRow(entity.dayInCycle, entity.ordinal,
                        entity.startTime, entity.endTime))
                .toList();
    }

    @Override
    public List<ResourceRow> resources(UUID scenarioId) {
        Map<UUID, String> codeByType = resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));

        return resources.findByScenarioIdOrderByLabel(scenarioId).stream()
                .map(entity -> new ResourceRow(entity.id, entity.typeId,
                        codeByType.get(entity.typeId), entity.label,
                        entity.defaultEffect, entity.attributes))
                .toList();
    }

    @Override
    public Map<UUID, List<AvailabilityRow>> availabilityByResource(UUID scenarioId) {
        return availabilities.findByScenarioId(scenarioId).stream()
                .map(entity -> new AvailabilityRow(entity.resourceId, entity.effect,
                        entity.dayInCycle, entity.startTime, entity.endTime, entity.priority))
                .collect(Collectors.groupingBy(AvailabilityRow::resourceId));
    }

    @Override
    public List<DemandRow> demands(UUID scenarioId) {
        Map<UUID, List<RequirementEntity>> byDemand = requirements.findByScenarioId(scenarioId).stream()
                .collect(Collectors.groupingBy(entity -> entity.demandId));

        return demands.findByScenarioIdOrderByLabel(scenarioId).stream()
                .map(entity -> new DemandRow(entity.id, entity.label, entity.durationMinutes,
                        entity.occurrences, entity.attributes, entity.placement,
                        byDemand.getOrDefault(entity.id, List.of()).stream()
                                .map(this::toRequirement)
                                .toList()))
                .toList();
    }

    @Override
    public List<ConstraintConfigRow> enabledConstraintConfigs(UUID scenarioId) {
        return configs.findByScenarioIdAndEnabledTrue(scenarioId).stream()
                .map(entity -> new ConstraintConfigRow(entity.id, entity.type, entity.scope,
                        entity.params, entity.weight, entity.enabled))
                .toList();
    }

    @Override
    public List<ConstraintWeightRow> constraintWeights(UUID scenarioId) {
        return weights.findByScenarioIdOrderByConstraintId(scenarioId).stream()
                .map(entity -> new ConstraintWeightRow(entity.constraintId, entity.level, entity.weight))
                .toList();
    }

    private RequirementRow toRequirement(RequirementEntity entity) {
        return new RequirementRow(entity.id, entity.role, entity.typeId, entity.quantity, entity.criteria);
    }

    /** Utilitaire partagé par les services d'API. */
    public Map<UUID, ResourceTypeEntity> typesById() {
        return resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, Function.identity()));
    }
}
