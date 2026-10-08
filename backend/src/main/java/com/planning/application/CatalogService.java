package com.planning.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.api.dto.Dtos.AttributeDefinitionDto;
import com.planning.api.dto.Dtos.CatalogDto;
import com.planning.domain.attribute.AttributeCatalog;
import com.planning.domain.constraint.Schemas;
import com.planning.api.dto.Dtos.PlacementStrategyDto;
import com.planning.api.dto.Dtos.PluginDto;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.model.BusinessProfile;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Period;
import com.planning.domain.placement.PlacementRegistry;
import com.planning.domain.time.CycleBuckets;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le vocabulaire disponible, exposé à l'interface d'administration.
 *
 * <p>« Le code fournit un langage, la base contient les phrases » (§2.1) : cet
 * endpoint est la grammaire. C'est lui qui permet au frontend de générer les
 * formulaires de configuration sans rien coder en dur.
 */
@Service
public class CatalogService {

    private final ConstraintRegistry registry;
    private final PlacementRegistry placements;

    public CatalogService(ConstraintRegistry registry, PlacementRegistry placements) {
        this.registry = registry;
        this.placements = placements;
    }

    public CatalogDto catalog() {
        return catalogFor(null, null);
    }

    /**
     * Le catalogue restreint à ce qui est réellement proposable sur cet horizon
     * et dans cette verticale.
     *
     * <p>Sans ce filtrage, l'interface offrirait `MONTH` sur un horizon cyclique —
     * une valeur que le chargement refuse systématiquement. Proposer un réglage
     * qui échouera à coup sûr est une faute d'interface, pas une souplesse.
     *
     * <p>Le profil, lui, ne coupe rien d'obligatoire : un attribut hors verticale
     * reste saisissable par l'éditeur avancé. Il choisit ce que le formulaire
     * <em>propose</em>, et {@code null} propose tout.
     */
    public CatalogDto catalogFor(HorizonKind horizon, BusinessProfile profile) {
        List<PluginDto> plugins = registry.all().stream()
                .filter(plugin -> horizon == null || plugin.horizons().contains(horizon))
                .map(plugin -> new PluginDto(
                        plugin.type(),
                        plugin.label(),
                        plugin.description(),
                        plugin.defaultLevel(),
                        plugin.configDriven(),
                        plugin.scopeTarget().name(),
                        plugin.horizons().stream().map(HorizonKind::name).sorted().toList(),
                        restrictEnums(plugin.paramSchema(), horizon)))
                .toList();

        List<PlacementStrategyDto> strategies = placements.all().stream()
                .map(strategy -> new PlacementStrategyDto(strategy.id(), strategy.paramSchema()))
                .toList();

        return new CatalogDto(
                plugins,
                strategies,
                CriterionParser.operators().stream().sorted().toList(),
                periodsFor(horizon),
                Arrays.stream(Effect.values()).map(Effect::name).toList(),
                attributesFor(profile));
    }

    /**
     * Le vocabulaire métier, chaque attribut accompagné des plugins qui le lisent.
     *
     * <p>{@code readBy} est <strong>calculé</strong> en parcourant le registre, pas
     * déclaré : la seule source est {@code ConstraintPlugin.readsAttributes()}.
     * Une liste tenue en double finirait par mentir, et un catalogue qui ment sur
     * ce qui lit un attribut est pire que pas de catalogue du tout.
     */
    private List<AttributeDefinitionDto> attributesFor(BusinessProfile profile) {
        Map<String, List<String>> readers = new HashMap<>();
        registry.all().forEach(plugin -> plugin.readsAttributes().forEach(
                key -> readers.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plugin.type())));

        return AttributeCatalog.forProfile(profile).stream()
                .map(definition -> new AttributeDefinitionDto(
                        definition.key(),
                        definition.title(),
                        definition.description(),
                        definition.kind().name(),
                        definition.options(),
                        definition.minimum(),
                        definition.profiles().stream().map(BusinessProfile::name).sorted().toList(),
                        definition.resourceTypes().stream().sorted().toList(),
                        readers.getOrDefault(definition.key(), List.of()).stream().sorted().toList()))
                .toList();
    }

    /**
     * Retire des énumérations les valeurs que cet horizon refusera au chargement.
     *
     * <p>Filtrer la liste globale des périodes ne suffit pas : la liste déroulante
     * du formulaire est générée depuis l'{@code enum} du schéma du plugin. C'est
     * là qu'il faut couper, sinon l'interface propose « par mois » sur un horizon
     * cyclique — un réglage qui fera systématiquement échouer la résolution.
     */
    private JsonNode restrictEnums(JsonNode paramSchema, HorizonKind horizon) {
        if (horizon == null || !paramSchema.has("properties")) {
            return paramSchema;
        }
        ObjectNode copy = paramSchema.deepCopy();
        List<String> allowed = periodsFor(horizon);

        copy.get("properties").forEach(property -> {
            if (!Schemas.ENUM_PERIOD.equals(property.path(Schemas.ENUM_KIND).asText(null))) {
                return;
            }
            ArrayNode values = ((ObjectNode) property).putArray("enum");
            allowed.forEach(values::add);
        });
        return copy;
    }

    private List<String> periodsFor(HorizonKind horizon) {
        return Arrays.stream(Period.values())
                .filter(period -> horizon != HorizonKind.CYCLIC || CycleBuckets.supports(period))
                .map(Period::name)
                .toList();
    }
}
