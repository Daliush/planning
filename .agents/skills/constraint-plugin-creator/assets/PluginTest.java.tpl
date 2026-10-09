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

/**
 * Trois cas minimum : règle respectée, règle violée, et deux configurations
 * concurrentes sur la même ressource — c'est ce dernier qui attrape les erreurs
 * de clé d'agrégation.
 *
 * <p>Ces tests éprouvent la traversée B. La traversée A est couverte par
 * {@code ScoreConsistencyTest}, qui vérifie que les deux redonnent le même score.
 */
class {{ClassName}}ConstraintTest {

    private final {{ClassName}}Constraint plugin = new {{ClassName}}Constraint();
    private final ScheduleBuilder builder = new ScheduleBuilder();
    private final UUID demand = UUID.randomUUID();

    private Resource ressource(String label, String attributsJson) {
        return builder.resource(label, attributsJson);
    }

    private void seance(Resource resource, int occurrence, int jour, LocalTime debut) {
        ScheduledActivity activity = builder.activity("{{Libellé de la demande}}", demand,
                occurrence, 60, builder.slot(jour, debut), """
                        {"competence":"support"}""");
        builder.booking(activity, "{{role}}", resource);
    }

    private ConstraintConfig regle(String scope, {{int seuil}}, int poids) {
        return builder.config({{ClassName}}Constraint.TYPE, scope,
                new {{ClassName}}Constraint.Params({{seuil}}, Period.DAY), poids, Level.{{SOFT}});
    }

    @Test
    void {{uneSituationConformeNeProduitAucuneViolation}}() {
        Resource sujet = ressource("Sujet", """
                {"type":"employee","competences":["support"]}""");
        seance(sujet, 0, 1, LocalTime.of(8, 0));

        assertThat(plugin.validate(builder.build(), regle("""
                {"eq":{"path":"/type","value":"employee"}}""", {{seuil_large}}, 1))).isEmpty();
    }

    @Test
    void {{laViolationEstPenaliseeAuProrataDeSonAmpleur}}() {
        Resource sujet = ressource("Sujet", """
                {"type":"employee","competences":["support"]}""");
        seance(sujet, 0, 1, LocalTime.of(8, 0));
        seance(sujet, 1, 1, LocalTime.of(9, 0));

        List<Violation> violations = plugin.validate(builder.build(), regle("""
                {"eq":{"path":"/type","value":"employee"}}""", {{seuil_serre}}, 1));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo({{penalite_attendue}});
            // Le message doit nommer la ressource, en vocabulaire métier.
            assertThat(violation.message()).contains("Sujet");
            // Les identifiants permettent le surlignage côté interface.
            assertThat(violation.resourceIds()).containsExactly(sujet.id());
            assertThat(violation.context()).containsEntry("{{cle}}", {{valeur}});
        });
    }

    /**
     * Le cas qui attrape les erreurs de clé d'agrégation : la ressource est visée
     * par les deux règles simultanément. Si la règle ne faisait pas partie de la
     * clé, les deux totaux fusionneraient.
     */
    @Test
    void deuxReglesConcurrentesSurLaMemeRessourceNeMelangentPasLeursTotaux() {
        Resource sujet = ressource("Sujet", """
                {"type":"employee","competences":["support"]}""");
        seance(sujet, 0, 1, LocalTime.of(8, 0));
        seance(sujet, 1, 1, LocalTime.of(9, 0));

        ConstraintConfig generale = regle("""
                {"eq":{"path":"/type","value":"employee"}}""", {{seuil_general}}, 1);
        ConstraintConfig specifique = regle("""
                {"contains":{"path":"/competences","value":"support"}}""", {{seuil_specifique}}, 3);

        Schedule schedule = builder.build();

        assertThat(plugin.validate(schedule, generale)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo({{p1}}));
        // Poids ② de 3 : la pénalité est triplée.
        assertThat(plugin.validate(schedule, specifique)).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo({{p2}}));
    }

    @Test
    void leScopeEcarteLesRessourcesQuiNeSontPasVisees() {
        ressource("Salle B12", """
                {"type":"salle","capacite":30}""");

        assertThat(plugin.validate(builder.build(), regle("""
                {"eq":{"path":"/type","value":"employee"}}""", {{seuil_serre}}, 1))).isEmpty();
    }
}
