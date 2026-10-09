package com.planning.domain.constraint.plugin;

import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ScheduledActivity;
import com.planning.support.ScheduleBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MinLoadConstraintTest {

    private final MinLoadConstraint plugin = new MinLoadConstraint();
    private final ScheduleBuilder builder = new ScheduleBuilder();

    private Resource enseignant(String label) {
        return builder.resource(label, """
                {"type":"enseignant","statut":"titulaire"}""");
    }

    private void seance(Resource resource, int day, LocalTime start) {
        ScheduledActivity activity = builder.activity("Cours", UUID.randomUUID(), 0, 60,
                builder.slot(day, start), "{}");
        builder.booking(activity, "enseignant", resource);
    }

    private ConstraintConfig rule(int minMinutes, int weight) {
        return builder.config(MinLoadConstraint.TYPE, """
                        {"eq":{"path":"/type","value":"enseignant"}}""",
                new MinLoadConstraint.Params(minMinutes, Period.CYCLE), weight, Level.SOFT);
    }

    @Test
    void unePlancherAtteintNeProduitAucuneViolation() {
        Resource prof = enseignant("Prof A");
        seance(prof, 0, LocalTime.of(8, 0));
        seance(prof, 0, LocalTime.of(9, 0));

        assertThat(plugin.validate(builder.build(), rule(120, 1))).isEmpty();
    }

    @Test
    void unDeficitEstPenaliseAuProrataDesMinutesManquantes() {
        Resource prof = enseignant("Prof A");
        seance(prof, 0, LocalTime.of(8, 0));

        assertThat(plugin.validate(builder.build(), rule(180, 1))).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.penalty()).isEqualTo(120);
                    assertThat(violation.message()).contains("Prof A").contains("1 h");
                });
    }

    /**
     * Le cas qui motive le second pipeline : sans lui, une ressource à zéro heure
     * n'apparaîtrait dans aucun panier d'agrégation et resterait invisible — alors
     * que c'est précisément le symptôme que cette règle existe pour attraper.
     */
    @Test
    void uneRessourceSansAucuneAffectationEstDetectee() {
        Resource actif = enseignant("Prof Actif");
        Resource oisif = enseignant("Prof Oisif");
        seance(actif, 0, LocalTime.of(8, 0));
        seance(actif, 0, LocalTime.of(9, 0));
        seance(actif, 1, LocalTime.of(8, 0));

        var violations = plugin.validate(builder.build(), rule(120, 1));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.message()).contains("Prof Oisif").contains("aucune affectation");
            assertThat(violation.penalty()).isEqualTo(120);
            assertThat(violation.resourceIds()).containsExactly(oisif.id());
            assertThat(violation.context()).containsEntry("neverUsed", true);
        });
    }

    @Test
    void lePoidsDeLaRegleMultiplieLaPenaliteDuneRessourceOisive() {
        enseignant("Prof Oisif");

        assertThat(plugin.validate(builder.build(), rule(120, 3))).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(360));
    }

    @Test
    void leScopeEcarteLesRessourcesQuiNeSontPasVisees() {
        builder.resource("Salle B12", """
                {"type":"salle","capacite":30}""");

        assertThat(plugin.validate(builder.build(), rule(120, 1))).isEmpty();
    }

    @Test
    void chaqueJourVideEstDetecteMemeSiLaRessourceTravailleUnAutreJour() {
        PluginTestSupport fixture = new PluginTestSupport(3);
        Resource person = fixture.resource("Alex", "{}");
        fixture.work(person, 0, 8, 60);
        var rule = fixture.rule(plugin, new MinLoadConstraint.Params(60, Period.DAY), 1);

        assertThat(plugin.validate(fixture.schedule(), rule)).hasSize(2)
                .allSatisfy(violation -> {
                    assertThat(violation.penalty()).isEqualTo(60);
                    assertThat(violation.context()).containsEntry("neverUsed", false);
                });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 120);
    }

    @Test
    void uneSemaineVideEstDetecteeEtLesConfigurationsRestentIndependantes() {
        PluginTestSupport fixture = new PluginTestSupport(14);
        Resource person = fixture.resource("Alex", "{}");
        fixture.work(person, 0, 8, 120);
        fixture.rule(plugin, new MinLoadConstraint.Params(60, Period.WEEK), 1);
        fixture.rule(plugin, new MinLoadConstraint.Params(120, Period.WEEK), 3);

        PluginTestSupport.assertScore(plugin, fixture.schedule(), 420);
    }

    @Test
    void uneNuitDeDimancheALundiRemplitChaquePanierAvecSesMinutesReelles() {
        PluginTestSupport fixture = new PluginTestSupport(LocalDate.of(2026, 10, 11), 2);
        Resource person = fixture.resource("Alex", "{}");
        var night = fixture.work(person, 0, 22, 240);
        fixture.work(person, 1, 8, 240);
        var weekly = fixture.rule(plugin, new MinLoadConstraint.Params(300, Period.WEEK), 2);
        var daily = fixture.rule(plugin, new MinLoadConstraint.Params(180, Period.DAY), 3);

        assertThat(plugin.validate(fixture.schedule(), weekly)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(360);
            assertThat(violation.context()).containsEntry("totalMinutes", 120);
            assertThat(violation.activityIds()).containsExactly(night.getActivity().getDemandId());
        });
        assertThat(plugin.validate(fixture.schedule(), daily)).singleElement().satisfies(violation -> {
            assertThat(violation.penalty()).isEqualTo(180);
            assertThat(violation.message()).contains("dimanche 11 octobre");
        });
        PluginTestSupport.assertScore(plugin, fixture.schedule(), 540);
    }
}
