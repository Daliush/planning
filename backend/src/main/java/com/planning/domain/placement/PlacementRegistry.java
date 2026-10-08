package com.planning.domain.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le catalogue des stratégies de placement.
 *
 * <p>Même échelle d'extension que les contraintes (§10.5) : un nouveau réglage
 * d'une stratégie existante est du JSON — niveau 1 ; une nouvelle façon de placer
 * est une classe de plus — niveau 2.
 */
public final class PlacementRegistry {

    private final Map<String, PlacementStrategy> strategies = new LinkedHashMap<>();

    public PlacementRegistry(List<PlacementStrategy> strategies) {
        strategies.forEach(strategy -> this.strategies.put(strategy.id(), strategy));
    }

    public static PlacementRegistry standard() {
        return new PlacementRegistry(List.of(
                new FreePlacement(),
                new FixedPlacement(),
                new WindowPlacement()));
    }

    public PlacementStrategy get(String id) {
        PlacementStrategy strategy = strategies.get(id);
        if (strategy == null) {
            throw new InvalidConfigException(
                    "Stratégie de placement inconnue : « %s ». Disponibles : %s"
                            .formatted(id, String.join(", ", strategies.keySet())));
        }
        return strategy;
    }

    /** Lit {@code demand.placement} et rend la stratégie correspondante. */
    public PlacementStrategy resolve(JsonNode placement) {
        if (placement == null || placement.isNull() || placement.isEmpty()) {
            return get(FreePlacement.ID);
        }
        JsonNode strategy = placement.get("strategy");
        return get(strategy == null || strategy.isNull() ? FreePlacement.ID : strategy.asText());
    }

    public List<PlacementStrategy> all() {
        return List.copyOf(strategies.values());
    }
}
