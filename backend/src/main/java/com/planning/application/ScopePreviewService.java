package com.planning.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.ScopeMatchDto;
import com.planning.api.dto.Dtos.ScopePreviewDto;
import com.planning.api.dto.Dtos.ScopePreviewRequest;
import com.planning.domain.builder.AttributeProjection;
import com.planning.domain.criteria.Criterion;
import com.planning.domain.criteria.CriterionParser;
import com.planning.repository.jpa.DemandJpaRepository;
import com.planning.repository.jpa.ResourceJpaRepository;
import com.planning.repository.jpa.ResourceTypeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Évalue un scope contre les données réelles du scénario, à la saisie.
 *
 * <p>Le calcul passe par le <strong>vrai moteur de critères</strong> et par la même
 * projection du code de type que le builder. C'est essentiel : une évaluation
 * réimplémentée côté interface finirait par diverger, et un aperçu qui ment est
 * pire que pas d'aperçu.
 */
@Service
@Transactional(readOnly = true)
public class ScopePreviewService {

    /** Assez d'exemples pour reconnaître la population, sans noyer l'écran. */
    private static final int SAMPLE_SIZE = 12;

    private final ResourceJpaRepository resources;
    private final ResourceTypeJpaRepository resourceTypes;
    private final DemandJpaRepository demands;
    private final ObjectMapper mapper;

    public ScopePreviewService(ResourceJpaRepository resources,
                               ResourceTypeJpaRepository resourceTypes,
                               DemandJpaRepository demands, ObjectMapper mapper) {
        this.resources = resources;
        this.resourceTypes = resourceTypes;
        this.demands = demands;
        this.mapper = mapper;
    }

    public ScopePreviewDto preview(UUID scenarioId, ScopePreviewRequest request) {
        // Un scope mal formé lève ici, avec un message exploitable : l'utilisateur
        // le voit en saisissant plutôt qu'au lancement de la résolution.
        Criterion scope = CriterionParser.parse(request.scope());

        return "DEMAND".equalsIgnoreCase(request.target())
                ? previewDemands(scenarioId, scope)
                : previewResources(scenarioId, scope);
    }

    private ScopePreviewDto previewResources(UUID scenarioId, Criterion scope) {
        Map<UUID, String> codeByType = resourceTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.id, type -> type.code));

        var all = resources.findByScenarioIdOrderByLabel(scenarioId);
        List<ScopeMatchDto> matched = new ArrayList<>();

        for (var resource : all) {
            String typeCode = codeByType.get(resource.typeId);
            JsonNode attributes =
                    AttributeProjection.withTypeCode(mapper, resource.attributes, typeCode);
            if (scope.test(attributes)) {
                matched.add(new ScopeMatchDto(resource.id, resource.label, typeCode));
            }
        }
        return new ScopePreviewDto(matched.size(), all.size(), sample(matched));
    }

    private ScopePreviewDto previewDemands(UUID scenarioId, Criterion scope) {
        var all = demands.findByScenarioIdOrderByLabel(scenarioId);
        List<ScopeMatchDto> matched = all.stream()
                .filter(demand -> scope.test(demand.attributes))
                .map(demand -> new ScopeMatchDto(demand.id, demand.label, null))
                .toList();
        return new ScopePreviewDto(matched.size(), all.size(), sample(matched));
    }

    private List<ScopeMatchDto> sample(List<ScopeMatchDto> matched) {
        return matched.stream().limit(SAMPLE_SIZE).toList();
    }
}
