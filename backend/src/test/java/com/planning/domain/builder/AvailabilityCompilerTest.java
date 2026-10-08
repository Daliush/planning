package com.planning.domain.builder;

import com.planning.domain.model.Effect;
import com.planning.domain.model.TimeProfile;
import com.planning.domain.model.Timeslot;
import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityCompilerTest {

    private static final UUID RESOURCE = UUID.randomUUID();

    private final AvailabilityCompiler compiler = new AvailabilityCompiler();
    private final List<Timeslot> slots =
            new HorizonFactory().create(SchoolFixture.timeModel(), SchoolFixture.grid()).slots();

    private ResourceRow resource(Effect defaultEffect) {
        return new ResourceRow(RESOURCE, UUID.randomUUID(), "enseignant", "Test",
                defaultEffect, SchoolFixture.json("{}"));
    }

    private TimeProfile compile(Effect defaultEffect, AvailabilityRow... rules) {
        return compiler.compile(resource(defaultEffect), List.of(rules), slots);
    }

    private int indexOf(int day, LocalTime start) {
        return slots.stream()
                .filter(slot -> slot.dayInCycle() == day && slot.start().equals(start))
                .findFirst().orElseThrow().index();
    }

    @Test
    void leDefautSappliqueAToutLHorizon() {
        TimeProfile profile = compile(Effect.UNAVAILABLE);
        assertThat(profile.isAllowed(0, slots.size())).isFalse();
        assertThat(profile.forbiddenSlots(0, slots.size())).isEqualTo(slots.size());
    }

    /** L'archétype « indisponible sauf » : Jean, vacataire, deux jours par semaine. */
    @Test
    void uneRegleDOuvertureRouvreExactementSonJour() {
        TimeProfile profile = compile(Effect.UNAVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.AVAILABLE, 1, null, null, 0));

        assertThat(profile.isAllowed(indexOf(1, LocalTime.of(8, 0)), indexOf(1, LocalTime.of(8, 0)) + 1))
                .isTrue();
        assertThat(profile.isAllowed(indexOf(0, LocalTime.of(8, 0)), indexOf(0, LocalTime.of(8, 0)) + 1))
                .isFalse();
    }

    /**
     * PIÈGE §9.3 — une colonne {@code NULL} signifie « pas de restriction sur cet
     * axe ». Une implémentation naïve testant l'égalité ne matcherait rien du tout,
     * silencieusement.
     */
    @Test
    void unJourNulCouvreTousLesJours() {
        TimeProfile profile = compile(Effect.AVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.UNAVAILABLE, null, LocalTime.of(11, 0), null, 0));

        assertThat(profile.isAllowed(indexOf(0, LocalTime.of(11, 15)), indexOf(0, LocalTime.of(11, 15)) + 1))
                .isFalse();
        assertThat(profile.isAllowed(indexOf(2, LocalTime.of(11, 15)), indexOf(2, LocalTime.of(11, 15)) + 1))
                .isFalse();
    }

    /**
     * PIÈGE §6.5 — le test de couverture n'est pas symétrique. Une fermeture qui
     * ne recouvre que la moitié d'un créneau doit le fermer ; une ouverture qui ne
     * recouvre que la moitié ne doit pas l'ouvrir.
     */
    @Test
    void uneFermetureChevauchanteFermeMaisUneOuvertureChevauchanteNOuvrePas() {
        int index = indexOf(0, LocalTime.of(9, 0));

        TimeProfile ferme = compile(Effect.AVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.UNAVAILABLE, 0,
                        LocalTime.of(9, 30), LocalTime.of(10, 0), 0));
        assertThat(ferme.isAllowed(index, index + 1)).isFalse();

        TimeProfile ouvre = compile(Effect.UNAVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.AVAILABLE, 0,
                        LocalTime.of(9, 30), LocalTime.of(10, 0), 0));
        assertThat(ouvre.isAllowed(index, index + 1)).isFalse();
    }

    @Test
    void laPrioriteLaPlusHauteGagne() {
        int index = indexOf(1, LocalTime.of(8, 0));
        TimeProfile profile = compile(Effect.AVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.UNAVAILABLE, 1, null, null, 0),
                new AvailabilityRow(RESOURCE, Effect.AVAILABLE, 1, null, null, 5));

        assertThat(profile.isAllowed(index, index + 1)).isTrue();
    }

    @Test
    void aPrioriteEgaleLIndisponibiliteLEmporte() {
        int index = indexOf(1, LocalTime.of(8, 0));
        TimeProfile profile = compile(Effect.UNAVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.AVAILABLE, 1, null, null, 0),
                new AvailabilityRow(RESOURCE, Effect.UNAVAILABLE, 1, null, null, 0));

        assertThat(profile.isAllowed(index, index + 1)).isFalse();
    }

    /**
     * PIÈGE §9.3 — écrire les deux bits à chaque passage. Sinon un DISCOURAGED
     * suivi d'un UNAVAILABLE prioritaire laisserait le créneau interdit
     * <em>et</em> découragé, déclenchant les deux contraintes et comptant double.
     */
    @Test
    void uneFermeturePrioritaireEffaceLeDecouragementQuElleRecouvre() {
        int index = indexOf(1, LocalTime.of(8, 0));
        TimeProfile profile = compile(Effect.AVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.DISCOURAGED, 1, null, null, 0),
                new AvailabilityRow(RESOURCE, Effect.UNAVAILABLE, 1, null, null, 1));

        assertThat(profile.isAllowed(index, index + 1)).isFalse();
        assertThat(profile.isDiscouraged(index, index + 1)).isFalse();
    }

    @Test
    void unCreneauDecourageResteAutorise() {
        int index = indexOf(1, LocalTime.of(8, 0));
        TimeProfile profile = compile(Effect.AVAILABLE,
                new AvailabilityRow(RESOURCE, Effect.DISCOURAGED, 1, null, null, 0));

        assertThat(profile.isAllowed(index, index + 1)).isTrue();
        assertThat(profile.isDiscouraged(index, index + 1)).isTrue();
    }
}
