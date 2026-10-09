package com.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.api.dto.Dtos.ConstraintConfigDto;
import com.planning.api.dto.Dtos.ConstraintConfigInput;
import com.planning.api.dto.Dtos.ConstraintWeightDto;
import com.planning.api.dto.Dtos.ConstraintWeightInput;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.ParamsCodec;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.repository.entity.Entities.ConstraintConfigEntity;
import com.planning.repository.entity.Entities.ConstraintWeightEntity;
import com.planning.repository.jpa.ConstraintConfigJpaRepository;
import com.planning.repository.jpa.ConstraintWeightJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Les règles configurées et leurs poids.
 *
 * <p>C'est le niveau 1 du modèle d'extension (§18.1) : ajouter ou repondérer une
 * règle est un {@code INSERT} ou un {@code UPDATE}, sans redéploiement.
 */
@Service
@Transactional(readOnly = true)
public class ConstraintService {

    private final ConstraintConfigJpaRepository configs;
    private final ConstraintWeightJpaRepository weights;
    private final ConstraintRegistry registry;
    private final ParamsCodec paramsCodec;
    private final ObjectMapper mapper;

    public ConstraintService(ConstraintConfigJpaRepository configs,
                             ConstraintWeightJpaRepository weights,
                             ConstraintRegistry registry, ParamsCodec paramsCodec,
                             ObjectMapper mapper) {
        this.configs = configs;
        this.weights = weights;
        this.registry = registry;
        this.paramsCodec = paramsCodec;
        this.mapper = mapper;
    }

    public List<ConstraintConfigDto> listConfigs(UUID scenarioId) {
        return configs.findByScenarioIdOrderByType(scenarioId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ConstraintConfigDto createConfig(UUID scenarioId, ConstraintConfigInput input) {
        ConstraintConfigEntity entity = new ConstraintConfigEntity();
        entity.id = UUID.randomUUID();
        entity.scenarioId = scenarioId;
        apply(entity, input);
        configs.save(entity);
        return toDto(entity);
    }

    @Transactional
    public ConstraintConfigDto updateConfig(UUID configId, ConstraintConfigInput input) {
        ConstraintConfigEntity entity = configs.findById(configId)
                .orElseThrow(() -> NotFoundException.of("Règle", configId));
        apply(entity, input);
        configs.save(entity);
        return toDto(entity);
    }

    @Transactional
    public void deleteConfig(UUID configId) {
        configs.deleteById(configId);
    }

    /**
     * Les poids de toutes les contraintes du registre, la valeur par défaut du
     * plugin comblant les lignes absentes en base. L'interface montre ainsi
     * l'intégralité des leviers, pas seulement ceux déjà configurés.
     */
    public List<ConstraintWeightDto> listWeights(UUID scenarioId) {
        Map<String, ConstraintWeightEntity> stored =
                weights.findByScenarioIdOrderByConstraintId(scenarioId).stream()
                        .collect(Collectors.toMap(entity -> entity.constraintId, entity -> entity));

        List<ConstraintWeightDto> result = new ArrayList<>();
        for (ConstraintPlugin plugin : registry.all()) {
            ConstraintWeightEntity entity = stored.get(plugin.type());
            result.add(entity == null
                    ? new ConstraintWeightDto(plugin.type(), plugin.label(), plugin.defaultLevel(), 1, true)
                    : new ConstraintWeightDto(plugin.type(), plugin.label(), entity.level, entity.weight, false));
        }
        return result;
    }

    @Transactional
    public ConstraintWeightDto upsertWeight(UUID scenarioId, ConstraintWeightInput input) {
        ConstraintPlugin plugin = registry.get(input.constraintId());
        if (input.weight() < 0) {
            throw new InvalidConfigException("Un poids ne peut pas être négatif.");
        }
        ConstraintWeightEntity entity = new ConstraintWeightEntity();
        entity.scenarioId = scenarioId;
        entity.constraintId = input.constraintId();
        entity.level = input.level();
        entity.weight = input.weight();
        weights.save(entity);
        return new ConstraintWeightDto(plugin.type(), plugin.label(), entity.level, entity.weight, false);
    }

    private void apply(ConstraintConfigEntity entity, ConstraintConfigInput input) {
        ConstraintPlugin plugin = registry.get(input.type());
        if (!plugin.configDriven()) {
            throw new InvalidConfigException("Cette règle structurelle se règle dans les poids.");
        }
        if (input.weight() != null && input.weight() < 0) {
            throw new InvalidConfigException("Un poids ne peut pas être négatif.");
        }
        // Contrôle 8 du §15, côté saisie : un message immédiat et exploitable,
        // plutôt qu'une résolution qui échouera dans quinze jours.
        paramsCodec.check(plugin, input.params());
        CriterionParser.parse(input.scope());

        entity.type = input.type();
        entity.scope = input.scope() == null ? mapper.createObjectNode() : input.scope();
        entity.params = input.params() == null ? mapper.createObjectNode() : input.params();
        entity.weight = input.weight() == null ? 1 : input.weight();
        entity.enabled = input.enabled() == null || input.enabled();
    }

    private ConstraintConfigDto toDto(ConstraintConfigEntity entity) {
        String label = registry.contains(entity.type) ? registry.get(entity.type).label() : entity.type;
        return new ConstraintConfigDto(entity.id, entity.scenarioId, entity.type, label,
                entity.scope, entity.params, entity.weight, entity.enabled);
    }
}
