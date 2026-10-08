package com.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.AvailabilityDto;
import com.planning.api.dto.Dtos.AvailabilityInput;
import com.planning.api.dto.Dtos.ResourceDto;
import com.planning.api.dto.Dtos.ResourceInput;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.model.Effect;
import com.planning.repository.entity.Entities.ResourceAvailabilityEntity;
import com.planning.repository.entity.Entities.ResourceEntity;
import com.planning.repository.jpa.ResourceAvailabilityJpaRepository;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Le référentiel des ressources et leurs disponibilités.
 *
 * <p>Les disponibilités se remplacent en bloc plutôt que ligne à ligne : elles
 * forment un jeu de règles dont la résolution dépend des priorités relatives
 * (§6.5), et les éditer une par une exposerait à des états intermédiaires
 * incohérents.
 */
@Service
@Transactional(readOnly = true)
public class ResourceCatalogService {

    private final ResourceJpaRepository resources;
    private final ResourceTypeJpaRepository resourceTypes;
    private final ResourceAvailabilityJpaRepository availabilities;
    private final ObjectMapper mapper;

    public ResourceCatalogService(ResourceJpaRepository resources,
                                  ResourceTypeJpaRepository resourceTypes,
                                  ResourceAvailabilityJpaRepository availabilities,
                                  ObjectMapper mapper) {
        this.resources = resources;
        this.resourceTypes = resourceTypes;
        this.availabilities = availabilities;
        this.mapper = mapper;
    }

    public List<ResourceDto> list(UUID scenarioId, UUID typeId) {
        List<ResourceEntity> entities = typeId == null
                ? resources.findByScenarioIdOrderByLabel(scenarioId)
                : resources.findByScenarioIdAndTypeIdOrderByLabel(scenarioId, typeId);

        Map<UUID, String> codeByType = typeCodes();
        Map<UUID, List<ResourceAvailabilityEntity>> rules =
                availabilities.findByResourceIdIn(entities.stream().map(e -> e.id).toList()).stream()
                        .collect(Collectors.groupingBy(rule -> rule.resourceId));

        return entities.stream()
                .map(entity -> toDto(entity, codeByType, rules.getOrDefault(entity.id, List.of())))
                .toList();
    }

    public ResourceDto get(UUID resourceId) {
        ResourceEntity entity = resources.findById(resourceId)
                .orElseThrow(() -> NotFoundException.of("Ressource", resourceId));
        return toDto(entity, typeCodes(), availabilities.findByResourceIdOrderByPriority(resourceId));
    }

    @Transactional
    public ResourceDto create(UUID scenarioId, ResourceInput input) {
        ResourceEntity entity = new ResourceEntity();
        entity.id = UUID.randomUUID();
        entity.scenarioId = scenarioId;
        apply(entity, input);
        resources.save(entity);
        return get(entity.id);
    }

    @Transactional
    public ResourceDto update(UUID resourceId, ResourceInput input) {
        ResourceEntity entity = resources.findById(resourceId)
                .orElseThrow(() -> NotFoundException.of("Ressource", resourceId));
        apply(entity, input);
        resources.save(entity);
        return get(resourceId);
    }

    @Transactional
    public void delete(UUID resourceId) {
        resources.deleteById(resourceId);
    }

    @Transactional
    public List<AvailabilityDto> replaceAvailability(UUID resourceId, List<AvailabilityInput> inputs) {
        if (!resources.existsById(resourceId)) {
            throw NotFoundException.of("Ressource", resourceId);
        }
        availabilities.deleteAll(availabilities.findByResourceIdOrderByPriority(resourceId));

        List<ResourceAvailabilityEntity> entities = inputs.stream().map(input -> {
            if (input.startTime() != null && input.endTime() != null
                    && !input.startTime().isBefore(input.endTime())) {
                throw new InvalidConfigException(
                        "Plage de disponibilité inversée : %s doit précéder %s."
                                .formatted(input.startTime(), input.endTime()));
            }
            ResourceAvailabilityEntity entity = new ResourceAvailabilityEntity();
            entity.id = UUID.randomUUID();
            entity.resourceId = resourceId;
            entity.effect = input.effect();
            entity.dayInCycle = input.dayInCycle();
            entity.startTime = input.startTime();
            entity.endTime = input.endTime();
            entity.priority = input.priority() == null ? 0 : input.priority();
            return entity;
        }).toList();

        availabilities.saveAll(entities);
        return entities.stream().map(this::toDto).toList();
    }

    private void apply(ResourceEntity entity, ResourceInput input) {
        if (!resourceTypes.existsById(input.typeId())) {
            throw NotFoundException.of("Type de ressource", input.typeId());
        }
        entity.typeId = input.typeId();
        entity.label = input.label();
        entity.defaultEffect = input.defaultEffect() == null ? Effect.AVAILABLE : input.defaultEffect();
        entity.attributes = input.attributes() == null ? mapper.createObjectNode() : input.attributes();
    }

    private Map<UUID, String> typeCodes() {
        return resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));
    }

    private ResourceDto toDto(ResourceEntity entity, Map<UUID, String> codeByType,
                              List<ResourceAvailabilityEntity> rules) {
        return new ResourceDto(entity.id, entity.scenarioId, entity.typeId,
                codeByType.get(entity.typeId), entity.label, entity.defaultEffect, entity.attributes,
                rules.stream()
                        .sorted((a, b) -> Integer.compare(a.priority, b.priority))
                        .map(this::toDto)
                        .toList());
    }

    private AvailabilityDto toDto(ResourceAvailabilityEntity entity) {
        return new AvailabilityDto(entity.id, entity.effect, entity.dayInCycle,
                entity.startTime, entity.endTime, entity.priority);
    }

    /** Exposé pour l'éditeur de critères du frontend. */
    public List<String> criterionOperators() {
        return CriterionParser.operators().stream().sorted().toList();
    }
}
