package com.planning.domain.solver;

import com.planning.domain.builder.WeightBook;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.SolveReport;
import com.planning.domain.model.Violation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La couche d'explicabilité construite en interne (ADR-003, §16).
 *
 * <p>Elle n'est pas un raffinement de confort : c'est la <strong>seule source
 * d'information</strong> quand une résolution échoue. Sans elle, un planning
 * invalide se présente comme un écran vide sans explication.
 *
 * <p>Le service se contente d'orchestrer : il interroge chaque plugin pour chaque
 * ligne de configuration, applique le poids ① et trie. Les phrases, elles, sont
 * produites par les plugins, en vocabulaire métier.
 */
public class ExplanationService {

    private final ConstraintRegistry registry;

    public ExplanationService(ConstraintRegistry registry) {
        this.registry = registry;
    }

    /** Le total pondéré des violations, par niveau. Base du test de cohérence §16.5. */
    public record Totals(int hard, int soft) {
    }

    public SolveReport report(Schedule solution, WeightBook weights) {
        List<Violation> hard = new ArrayList<>();
        List<Violation> soft = new ArrayList<>();
        Map<String, Integer> penaltyByConstraint = new LinkedHashMap<>();

        for (Violation violation : collect(solution)) {
            int weighted = Math.multiplyExact(violation.penalty(), weights.weightOf(violation.constraintId()));
            penaltyByConstraint.merge(violation.constraintId(), weighted, Math::addExact);
            if (violation.level() == Level.HARD) {
                hard.add(violation);
            } else {
                soft.add(violation);
            }
        }

        Comparator<Violation> byPenaltyDesc = Comparator
                .comparingInt((Violation violation) ->
                        Math.multiplyExact(violation.penalty(), weights.weightOf(violation.constraintId())))
                .reversed();
        hard.sort(byPenaltyDesc);
        soft.sort(byPenaltyDesc);

        Totals totals = totals(solution, weights);
        return new SolveReport(
                -totals.hard(),
                -totals.soft(),
                totals.hard() == 0,
                List.copyOf(hard),
                List.copyOf(soft),
                Map.copyOf(penaltyByConstraint));
    }

    public Totals totals(Schedule solution, WeightBook weights) {
        int hard = 0;
        int soft = 0;
        for (Violation violation : collect(solution)) {
            int weighted = Math.multiplyExact(violation.penalty(), weights.weightOf(violation.constraintId()));
            if (violation.level() == Level.HARD) {
                hard = Math.addExact(hard, weighted);
            } else {
                soft = Math.addExact(soft, weighted);
            }
        }
        return new Totals(hard, soft);
    }

    public List<Violation> collect(Schedule solution) {
        List<Violation> violations = new ArrayList<>();
        for (ConstraintPlugin plugin : registry.all()) {
            for (ConstraintConfig config : solution.getConfigs()) {
                if (config.type().equals(plugin.type())) {
                    violations.addAll(plugin.validate(solution, config));
                }
            }
        }
        return violations;
    }
}
