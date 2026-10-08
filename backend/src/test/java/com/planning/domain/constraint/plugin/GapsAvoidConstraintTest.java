package com.planning.domain.constraint.plugin;

import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ScheduledActivity;
import com.planning.support.ScheduleBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GapsAvoidConstraintTest {

    private final GapsAvoidConstraint plugin = new GapsAvoidConstraint();
    private final ScheduleBuilder builder = new ScheduleBuilder();

    private Resource groupe() {
        return builder.resource("6e A", """
                {"type":"groupe","code":"6A"}""");
    }

    private void seance(Resource resource, int day, LocalTime start) {
        ScheduledActivity activity = builder.activity("Cours", UUID.randomUUID(), 0, 60,
                builder.slot(day, start), "{}");
        builder.booking(activity, "groupe", resource);
    }

    private ConstraintConfig rule(int toleranceMinutes, int weight) {
        return builder.config(GapsAvoidConstraint.TYPE, """
                        {"eq":{"path":"/type","value":"groupe"}}""",
                new GapsAvoidConstraint.Params(toleranceMinutes), weight, Level.SOFT);
    }

    @Test
    void deuxSeancesCollesNeCreentAucunTrou() {
        Resource groupe = groupe();
        seance(groupe, 0, LocalTime.of(8, 0));
        seance(groupe, 0, LocalTime.of(9, 0));

        assertThat(plugin.validate(builder.build(), rule(20, 1))).isEmpty();
    }

    /**
     * PIÈGE §6.7 — la récréation sépare deux ordinaux successifs. Sans tolérance,
     * chaque journée normale d'un collège compterait un « trou » de 15 minutes.
     */
    @Test
    void laRecreationEstAbsorbeeParLaTolerance() {
        Resource groupe = groupe();
        seance(groupe, 0, LocalTime.of(9, 0));
        seance(groupe, 0, LocalTime.of(10, 15));

        assertThat(plugin.validate(builder.build(), rule(20, 1))).isEmpty();
        assertThat(plugin.validate(builder.build(), rule(0, 1))).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(15));
    }

    @Test
    void unVraiTrouEstPenaliseAuProrataDeSaDuree() {
        Resource groupe = groupe();
        seance(groupe, 0, LocalTime.of(8, 0));
        seance(groupe, 0, LocalTime.of(11, 15));

        assertThat(plugin.validate(builder.build(), rule(20, 1))).singleElement()
                .satisfies(violation -> {
                    // 09:00 → 11:15 = 135 minutes de trou.
                    assertThat(violation.penalty()).isEqualTo(135);
                    assertThat(violation.message()).contains("6e A").contains("lundi");
                });
    }

    /**
     * §13.4 — « les trous des élèves comptent quatre fois plus que ceux des
     * professeurs » passe par le poids ②, avec un seul plugin.
     */
    @Test
    void lePoidsDeLaRegleMultiplieLaPenalite() {
        Resource groupe = groupe();
        seance(groupe, 0, LocalTime.of(8, 0));
        seance(groupe, 0, LocalTime.of(11, 15));

        assertThat(plugin.validate(builder.build(), rule(20, 4))).singleElement()
                .satisfies(violation -> assertThat(violation.penalty()).isEqualTo(540));
    }

    @Test
    void lesJourneesSontIndependantes() {
        Resource groupe = groupe();
        seance(groupe, 0, LocalTime.of(8, 0));
        seance(groupe, 1, LocalTime.of(11, 15));

        assertThat(plugin.validate(builder.build(), rule(20, 1))).isEmpty();
    }
}
