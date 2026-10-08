package com.planning.domain.builder;

import com.planning.domain.exception.IncompatibleTimeModelException;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Timeslot;
import com.planning.domain.port.Rows.TimeModelRow;
import com.planning.domain.time.TimeHorizon;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartsCompilerTest {

    private final StartsCompiler compiler = new StartsCompiler();

    private TimeHorizon horizon(int maxGapMinutes) {
        TimeModelRow model = new TimeModelRow(UUID.randomUUID(), "test", HorizonKind.CYCLIC,
                7, null, null, maxGapMinutes);
        return new HorizonFactory().create(model, SchoolFixture.grid());
    }

    @Test
    void uneSeanceDUneHeurePeutDemarrerPartout() {
        assertThat(compiler.feasibleStarts(horizon(20), 60))
                .hasSize(SchoolFixture.grid().size());
    }

    /**
     * PIÈGE §10.3 — deux créneaux d'index successifs ne sont pas nécessairement
     * collés. Une séance de 2 h peut enjamber la récréation de 15 min en politique
     * tolérante, jamais en politique stricte.
     */
    @Test
    void laPolitiqueDeCoupureDecideSiUneSeanceEnjambeLaRecreation() {
        List<Timeslot> tolerante = compiler.feasibleStarts(horizon(20), 120);
        List<Timeslot> stricte = compiler.feasibleStarts(horizon(0), 120);

        assertThat(tolerante).anyMatch(slot -> slot.start().equals(LocalTime.of(9, 0)));
        assertThat(stricte).noneMatch(slot -> slot.start().equals(LocalTime.of(9, 0)));
        // 08:00 → 10:00 est collé dans les deux politiques.
        assertThat(stricte).anyMatch(slot -> slot.start().equals(LocalTime.of(8, 0)));
    }

    @Test
    void uneSeanceNePeutPasDeborderSurLeJourSuivant() {
        assertThat(compiler.feasibleStarts(horizon(20), 120))
                .noneMatch(slot -> slot.start().equals(LocalTime.of(11, 15)));
    }

    /**
     * {@code remaining == 0} : mieux vaut aucun départ valide — et l'exception
     * explicite du validateur — qu'un cours silencieusement rallongé (§10.2).
     */
    @Test
    void uneDureeNonRepresentableSurLaGrilleEstRefusee() {
        assertThatThrownBy(() -> horizon(20).grid().toSlots(90))
                .isInstanceOf(IncompatibleTimeModelException.class)
                .hasMessageContaining("90")
                .hasMessageContaining("60");
    }
}
