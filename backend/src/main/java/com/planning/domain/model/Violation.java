package com.planning.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une violation nommée, produite par la seconde traversée d'un plugin (§16.3).
 *
 * <p>{@code resourceIds} et {@code activityIds} sont l'équivalent des
 * <em>indictments</em> de Timefold : ils permettent à l'interface de surligner
 * Jean et ses trois séances du mardi, au lieu de se contenter d'une phrase.
 *
 * @param penalty poids ② × poids ③, <strong>avant</strong> application du poids ①
 */
public record Violation(
        String constraintId,
        Level level,
        int penalty,
        String message,
        List<UUID> resourceIds,
        List<UUID> activityIds,
        Map<String, Object> context) {
}
