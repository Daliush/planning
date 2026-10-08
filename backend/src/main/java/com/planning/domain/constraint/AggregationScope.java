package com.planning.domain.constraint;

import com.planning.domain.model.Period;
import com.planning.domain.model.Resource;

/**
 * La clé d'agrégation d'une règle de charge (§11.6).
 *
 * <p><strong>Sa composition définit la sémantique de la contrainte.</strong>
 * Retirer {@code resource}, et le quota individuel devient un quota collectif
 * d'équipe. Retirer {@code periodIndex}, et le quota journalier devient un quota
 * sur tout l'horizon. Le code est identique, la règle exprimée n'a plus rien à
 * voir : ce n'est pas de la plomberie technique.
 */
public record AggregationScope(Resource resource, Period period, int periodIndex) {
}
