package com.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.DemandDto;
import com.planning.api.dto.Dtos.DemandInput;
import com.planning.api.dto.Dtos.RequirementDto;
import com.planning.api.dto.Dtos.RequirementInput;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.placement.PlacementRegistry;
import com.planning.repository.entity.Entities.DemandEntity;
import com.planning.repository.entity.Entities.RequirementEntity;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.RequirementJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Les demandes et leurs postes à pourvoir.
 *
 * <p>Le {@code quantity} d'un requirement est le mécanisme du niveau 0 : « il faut
 * 6 personnes dont un manager » se traduit par deux lignes, et le builder boucle
 * sur la quantité sans savoir qu'il crée 6 bookings plutôt que 2 (§6.10).
 */
@Service
@Transactional(readOnly = true)
public class DemandService {

    private final DemandJpaRepository demands;
    private final RequirementJpaRepository requirements;
    private final ResourceTypeJpaRepository resourceTypes;
    private final PlacementRegistry placements;
    private final ObjectMapper mapper;

    public DemandService(DemandJpaRepository demands, RequirementJpaRepository requirements,
                         ResourceTypeJpaRepository resourceTypes, PlacementRegistry placements,
                         ObjectMapper mapper) {
        this.demands = demands;
        this.requirements = requirements;
        this.resourceTypes = resourceTypes;
        this.placements = placements;
        this.mapper = mapper;
    }

    public List<DemandDto> list(UUID scenarioId) {
        Map<UUID, String> codeByType = typeCodes();
        Map<UUID, List<RequirementEntity>> byDemand = requirements.findByScenarioId(scenarioId).stream()
                .collect(Collectors.groupingBy(entity -> entity.demandId));

        return demands.findByScenarioIdOrderByLabel(scenarioId).stream()
                .map(entity -> toDto(entity, byDemand.getOrDefault(entity.id, List.of()), codeByType))
                .toList();
    }

    public DemandDto get(UUID demandId) {
        DemandEntity entity = demands.findById(demandId)
                .orElseThrow(() -> NotFoundException.of("Demande", demandId));
        return toDto(entity, requirements.findByDemandIdOrderByRole(demandId), typeCodes());
    }

    @Transactional
    public DemandDto create(UUID scenarioId, DemandInput input) {
        DemandEntity entity = new DemandEntity();
        entity.id = UUID.randomUUID();
        entity.scenarioId = scenarioId;
        apply(entity, input);
        demands.save(entity);
        return get(entity.id);
    }

    @Transactional
    public DemandDto update(UUID demandId, DemandInput input) {
        DemandEntity entity = demands.findById(demandId)
                .orElseThrow(() -> NotFoundException.of("Demande", demandId));
        apply(entity, input);
        demands.save(entity);
        return get(demandId);
    }

    @Transactional
    public void delete(UUID demandId) {
        demands.deleteById(demandId);
    }

    @Transactional
    public RequirementDto addRequirement(UUID demandId, RequirementInput input) {
        if (!demands.existsById(demandId)) {
            throw NotFoundException.of("Demande", demandId);
        }
        if (!resourceTypes.existsById(input.typeId())) {
            throw NotFoundException.of("Type de ressource", input.typeId());
        }
        // Le parsing échoue tôt et bruyamment plutôt qu'au chargement du problème.
        CriterionParser.parse(input.criteria());

        RequirementEntity entity = new RequirementEntity();
        entity.id = UUID.randomUUID();
        entity.demandId = demandId;
        entity.role = input.role();
        entity.typeId = input.typeId();
        entity.quantity = input.quantity() == null ? 1 : input.quantity();
        entity.criteria = input.criteria() == null ? mapper.createObjectNode() : input.criteria();
        requirements.save(entity);
        return toDto(entity, typeCodes());
    }

    @Transactional
    public void deleteRequirement(UUID requirementId) {
        requirements.deleteById(requirementId);
    }

    private void apply(DemandEntity entity, DemandInput input) {
        if (input.durationMinutes() <= 0) {
            throw new InvalidConfigException("La durée d'une demande doit être strictement positive.");
        }
        if (input.occurrences() <= 0) {
            throw new InvalidConfigException("Le nombre d'occurrences doit être strictement positif.");
        }
        entity.label = input.label();
        entity.durationMinutes = input.durationMinutes();
        entity.occurrences = input.occurrences();
        entity.attributes = input.attributes() == null ? mapper.createObjectNode() : input.attributes();
        entity.placement = input.placement() == null
                ? mapper.createObjectNode().put("strategy", "free")
                : input.placement();
        // Rejette une stratégie inconnue à la saisie.
        placements.resolve(entity.placement);
    }

    private Map<UUID, String> typeCodes() {
        return resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));
    }

    private DemandDto toDto(DemandEntity entity, List<RequirementEntity> reqs, Map<UUID, String> codes) {
        return new DemandDto(entity.id, entity.scenarioId, entity.label, entity.durationMinutes,
                entity.occurrences, entity.attributes, entity.placement,
                reqs.stream().map(req -> toDto(req, codes)).toList());
    }

    private RequirementDto toDto(RequirementEntity entity, Map<UUID, String> codes) {
        return new RequirementDto(entity.id, entity.demandId, entity.role, entity.typeId,
                codes.get(entity.typeId), entity.quantity, entity.criteria);
    }

    private RequirementDto toDto(RequirementEntity entity) {
        return toDto(entity, typeCodes());
    }
}
