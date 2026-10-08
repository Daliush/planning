package com.planning.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Le rapport rendu par {@code GET /solve-jobs/{id}/report} (§16.6).
 *
 * @param feasible        {@code hardScore == 0}
 * @param hardViolations  triées par pénalité décroissante : ce que l'utilisateur
 *                        doit corriger en premier
 * @param penaltyByConstraint total pondéré par contrainte, pour repérer une règle
 *                        massivement violée — signe d'une configuration
 *                        incohérente plutôt que d'un planning difficile
 */
public record SolveReport(
        int hardScore,
        int softScore,
        boolean feasible,
        List<Violation> hardViolations,
        List<Violation> softViolations,
        Map<String, Integer> penaltyByConstraint) {
}
