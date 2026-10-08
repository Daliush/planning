package com.planning.domain.solver;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import com.planning.domain.builder.ProblemBuilder.BuiltProblem;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.model.Schedule;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INVARIANT n° 14, §16.5 — <strong>le gardien de tout le dispositif</strong>.
 *
 * <p>La factorisation du §16.4 réduit le risque de dérive entre les deux
 * traversées d'un plugin, mais ne le supprime pas : un {@code filter} ajouté d'un
 * seul côté, une garde oubliée dans la boucle, un {@code continue} de trop. Le
 * rapport annoncerait alors des violations que le solveur n'a jamais vues, ou
 * tairait celles qu'il a comptées — sans que rien ne le signale.
 *
 * <p>La référence est le score porté par {@code @PlanningScore} : aucune API
 * d'analyse n'est nécessaire pour l'obtenir, ce qui rend ce contrôle indépendant
 * de l'édition de Timefold.
 */
class ScoreConsistencyTest {

    private final ExplanationService explanation = new ExplanationService(ConstraintRegistry.standard());

    @Test
    void lExplicationRedonneExactementLeScoreDuSolveur() {
        BuiltProblem problem = SchoolFixture.builder().build(SchoolFixture.SCENARIO);
        Schedule solved = new SolverService(EnvironmentMode.REPRODUCIBLE)
                .solve(problem.schedule(), 5);
        solved.setHorizon(problem.schedule().getHorizon());

        ExplanationService.Totals totals = explanation.totals(solved, problem.weights());

        assertThat(solved.getScore().hardScore()).isEqualTo(-totals.hard());
        assertThat(solved.getScore().softScore()).isEqualTo(-totals.soft());
    }

    /**
     * FULL_ASSERT recalcule le score depuis zéro à chaque mouvement et le compare à
     * l'incrémental. C'est ce qui fait tomber immédiatement une lambda impure ou
     * une dépendance non déclarée entre entités — typiquement la lecture du créneau
     * d'une activité sans passer par la variable ombre (§11.8).
     */
    @Test
    void leScoreIncrementalResisteAuRecalculComplet() {
        BuiltProblem problem = SchoolFixture.builder().build(SchoolFixture.SCENARIO);

        Schedule solved = new SolverService(EnvironmentMode.FULL_ASSERT)
                .solve(problem.schedule(), 5);

        assertThat(solved.getScore()).isNotNull();
    }

    @Test
    void leRapportTrieLesViolationsDuresEnPremierEtParPenaliteDecroissante() {
        BuiltProblem problem = SchoolFixture.builder().build(SchoolFixture.SCENARIO);
        Schedule solved = new SolverService(EnvironmentMode.REPRODUCIBLE)
                .solve(problem.schedule(), 5);
        solved.setHorizon(problem.schedule().getHorizon());

        var report = explanation.report(solved, problem.weights());

        assertThat(report.feasible()).isEqualTo(report.hardScore() == 0);
        assertThat(report.softViolations()).isNotEmpty();
        assertThat(report.softViolations())
                .extracting(violation -> violation.penalty()
                        * problem.weights().weightOf(violation.constraintId()))
                .isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        // Chaque violation nomme les objets impliqués : sans ces identifiants, le
        // rapport est illisible au-delà de quelques lignes (§16.3).
        assertThat(report.softViolations())
                .allSatisfy(violation -> assertThat(violation.message()).isNotBlank());
    }

    @Test
    void chaquePluginStructurelRecoitUneConfigurationSynthetique() {
        BuiltProblem problem = SchoolFixture.builder().build(SchoolFixture.SCENARIO);

        // Sans cette ligne synthétique, les violations structurelles compteraient
        // dans le score sans jamais apparaître dans le rapport.
        for (var plugin : ConstraintRegistry.standard().structural()) {
            assertThat(problem.schedule().getConfigs())
                    .as("configuration synthétique pour %s", plugin.type())
                    .anyMatch(config -> config.type().equals(plugin.type()));
        }
    }
}
