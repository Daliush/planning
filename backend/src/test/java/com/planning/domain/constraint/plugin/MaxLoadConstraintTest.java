package com.planning.domain.constraint.plugin;

import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.Resource;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.Violation;
import com.planning.support.ScheduleBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaxLoadConstraintTest {

    private final MaxLoadConstraint plugin = new MaxLoadConstraint();
    private final ScheduleBuilder builder = new ScheduleBuilder();
    private final UUID demand = UUID.randomUUID();

    private Resource jean() {
        return builder.resource("Jean Dupont", """
                {"type":"enseignant","statut":"vacataire"}""");
    }

    private void seance(Resource resource, int occurrence, int day, LocalTime start) {
        ScheduledActivity activity = builder.activity("Maths 6e A", demand, occurrence, 60,
                builder.slot(day, start), """
                        {"matiere":"maths"}""");
        builder.booking(activity, "enseignant", resource);
    }

    private ConstraintConfig quota(String scope, int maxMinutes, int weight) {
        return builder.config(MaxLoadConstraint.TYPE, scope,
                new MaxLoadConstraint.Params(maxMinutes, Period.DAY), weight, Level.HARD);
    }

    @Test
    void uneChargeSousLeSeuilNeProduitAucuneViolation() {
        Resource jean = jean();
        seance(jean, 0, 1, LocalTime.of(8, 0));
        seance(jean, 1, 1, LocalTime.of(9, 0));
        ConstraintConfig rule = quota("""
                {"eq":{"path":"/type","value":"enseignant"}}""", 240, 1);

        assertThat(plugin.validate(builder.build(), rule)).isEmpty();
    }

    @Test
    void leDepassementEstPenaliseAuProrataDeSonAmpleur() {
        Resource jean = jean();
        seance(jean, 0, 1, LocalTime.of(8, 0));
        seance(jean, 1, 1, LocalTime.of(9, 0));
        seance(jean, 2, 1, LocalTime.of(10, 15));
        ConstraintConfig rule = quota("""
                {"eq":{"path":"/type","value":"enseignant"}}""", 120, 1);

        List<Violation> violations = plugin.validate(builder.build(), rule);

        // 180 min réalisées, 120 autorisées : poids ② 1 × poids ③ 60.
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(60);
            assertThat(violation.message()).contains("Jean Dupont").contains("mardi");
            assertThat(violation.resourceIds()).containsExactly(jean.id());
            assertThat(violation.context()).containsEntry("totalMinutes", 180);
        });
    }

    /**
     * Le cas qui attrape les erreurs de clé d'agrégation : Jean est enseignant
     * <em>et</em> vacataire, donc soumis aux deux quotas simultanément. Si la règle
     * ne faisait pas partie de la clé, les deux totaux fusionneraient (§11.6).
     */
    @Test
    void deuxQuotasConcurrentsSurLaMemeRessourceSeCumulentSansSeMelanger() {
        Resource jean = jean();
        seance(jean, 0, 1, LocalTime.of(8, 0));
        seance(jean, 1, 1, LocalTime.of(9, 0));
        seance(jean, 2, 1, LocalTime.of(10, 15));

        ConstraintConfig general = quota("""
                {"eq":{"path":"/type","value":"enseignant"}}""", 120, 1);
        ConstraintConfig vacataire = quota("""
                {"eq":{"path":"/statut","value":"vacataire"}}""", 60, 3);

        Schedule schedule = builder.build();

        assertThat(plugin.validate(schedule, general)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(60));
        // 180 − 60 = 120 minutes de dépassement, pondérées par le poids ② de 3.
        assertThat(plugin.validate(schedule, vacataire)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(360));
    }

    @Test
    void lesJoursSontComptesSeparement() {
        Resource jean = jean();
        seance(jean, 0, 1, LocalTime.of(8, 0));
        seance(jean, 1, 1, LocalTime.of(9, 0));
        seance(jean, 2, 2, LocalTime.of(8, 0));
        seance(jean, 3, 2, LocalTime.of(9, 0));
        ConstraintConfig rule = quota("""
                {"eq":{"path":"/type","value":"enseignant"}}""", 120, 1);

        assertThat(plugin.validate(builder.build(), rule)).isEmpty();
    }
}
