package com.planning.domain.builder;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.exception.UnknownConstraintException;
import com.planning.domain.model.Level;
import com.planning.domain.port.Rows.ConstraintWeightRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le poids ① résolu pour un scénario : niveau et pondération de chaque contrainte.
 *
 * <p>La table est complétée par les valeurs par défaut des plugins, si bien que
 * <strong>chaque contrainte enregistrée a toujours un poids explicite</strong>.
 * Ce même livre sert à construire les {@link ConstraintWeightOverrides} donnés au
 * solveur <em>et</em> à pondérer les violations du rapport : c'est ce qui rend le
 * test de cohérence du §16.5 exact plutôt qu'approximatif.
 */
public final class WeightBook {

    private final Map<String, Level> levels = new LinkedHashMap<>();
    private final Map<String, Integer> weights = new LinkedHashMap<>();

    private WeightBook() {
    }

    /** Nom de contrainte Timefold → type du plugin qui la produit. */
    private final Map<String, String> ownerByConstraintName = new LinkedHashMap<>();

    public static WeightBook of(ConstraintRegistry registry, List<ConstraintWeightRow> rows) {
        WeightBook book = new WeightBook();
        for (ConstraintPlugin plugin : registry.all()) {
            book.levels.put(plugin.type(), plugin.defaultLevel());
            book.weights.put(plugin.type(), 1);
            plugin.constraintNames()
                    .forEach(name -> book.ownerByConstraintName.put(name, plugin.type()));
        }
        for (ConstraintWeightRow row : rows) {
            if (!registry.contains(row.constraintId())) {
                throw new UnknownConstraintException(
                        "Poids configuré pour « %s », qui n'existe pas dans le registre."
                                .formatted(row.constraintId()));
            }
            book.levels.put(row.constraintId(), row.level());
            book.weights.put(row.constraintId(), row.weight());
        }
        return book;
    }

    public Level levelOf(String constraintId) {
        return levels.get(constraintId);
    }

    public int weightOf(String constraintId) {
        return weights.get(constraintId);
    }

    /**
     * Une entrée par contrainte Timefold réellement définie — pipelines
     * auxiliaires compris, qui reçoivent le poids de leur plugin. Sans cela, un
     * pipeline auxiliaire garderait la valeur écrite en dur dans le code et
     * ignorerait la configuration.
     */
    public ConstraintWeightOverrides<HardSoftScore> overrides() {
        Map<String, HardSoftScore> scores = new LinkedHashMap<>();
        ownerByConstraintName.forEach((name, owner) -> scores.put(name,
                levels.get(owner) == Level.HARD
                        ? HardSoftScore.ofHard(weights.get(owner))
                        : HardSoftScore.ofSoft(weights.get(owner))));
        return ConstraintWeightOverrides.of(scores);
    }

    public Map<String, Level> levels() {
        return Map.copyOf(levels);
    }

    public Map<String, Integer> weights() {
        return Map.copyOf(weights);
    }
}
