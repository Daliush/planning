package com.planning.domain.constraint;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Le pont entre le registre de plugins et Timefold.
 *
 * <p>{@code defineConstraints()} n'est appelé <strong>qu'une fois</strong>, à la
 * construction du solveur. Le nombre de méthodes Java est donc figé, alors que le
 * nombre de lignes de configuration est libre et change à chaque scénario : c'est
 * précisément pourquoi une méthode traite N lignes via une jointure, au lieu de
 * générer une contrainte Java par ligne (§11.2).
 *
 * <p>Tous les plugins sont enregistrés, y compris ceux restreints à un mode
 * d'horizon. {@code horizons()} sert de filtre d'affichage pour l'interface
 * d'administration ; le refus des configurations incompatibles se fait au
 * chargement, dans {@code ProblemValidator} — sans quoi une ligne serait ignorée
 * sans un mot et l'utilisateur croirait sa règle appliquée (§15, contrôle 7).
 */
public class GenericConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return ConstraintRegistry.standard().all().stream()
                .flatMap(plugin -> plugin.buildAll(factory).stream())
                .toArray(Constraint[]::new);
    }
}
