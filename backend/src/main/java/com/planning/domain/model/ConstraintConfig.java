package com.planning.domain.model;

import com.planning.domain.criteria.Criterion;

import java.util.UUID;

/**
 * Une règle configurée (§7.7). <strong>Exception unique à la compilation</strong> :
 * ces lignes restent des faits vivants pendant toute la résolution, parce que les
 * contraintes doivent les joindre dynamiquement avec des affectations qui, elles,
 * changent en permanence (§2.3).
 *
 * @param level niveau effectif de la règle, résolu depuis {@code constraint_weight}
 *              au chargement. Porté ici pour que la traversée d'explicabilité
 *              n'ait pas à refaire la jointure pour chaque violation.
 */
public record ConstraintConfig(
        UUID id,
        String type,
        Criterion scope,
        Object params,
        int weight,
        boolean enabled,
        Level level) {

    /**
     * {@code equals} et {@code hashCode} sur l'{@code id} uniquement : cet objet
     * sert de clé d'agrégation dans les {@code groupBy}, et une comparaison
     * profonde serait rejouée à chaque hachage en boucle chaude.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ConstraintConfig other && other.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @SuppressWarnings("unchecked")
    public <T> T paramsAs(Class<T> type) {
        return (T) params;
    }

    @Override
    public String toString() {
        return "%s(%s)".formatted(type, id);
    }
}
