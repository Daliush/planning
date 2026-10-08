package com.planning.domain.builder;

import com.planning.domain.exception.InfeasibleProblemException;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.port.Rows.DemandRow;
import com.planning.domain.port.Rows.RequirementRow;
import com.planning.support.FakeProblemSource;
import com.planning.support.SchoolFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemBuilderTest {

    @Test
    void leCodeDuTypeEstProjeteDansLesAttributs() {
        Schedule schedule = SchoolFixture.builder().build(SchoolFixture.SCENARIO).schedule();

        // INVARIANT §6.4 : sans cette projection, un scope sur /type ne matcherait
        // jamais rien — silencieusement.
        assertThat(schedule.getResources())
                .allSatisfy(resource -> assertThat(resource.attributes().at("/type").asText())
                        .isIn("enseignant", "salle", "groupe"));
    }

    @Test
    void chaqueOccurrenceProduitUneSeanceEtChaqueQuantiteUnPoste() {
        Schedule schedule = SchoolFixture.builder().build(SchoolFixture.SCENARIO).schedule();

        // 2 demandes × 3 occurrences ; 3 postes par séance.
        assertThat(schedule.getActivities()).hasSize(6);
        assertThat(schedule.getBookings()).hasSize(18);
    }

    /**
     * §9.4 — les viviers sont mutualisés par {@code (type, critères canoniques)}.
     * Avec 10 000 bookings, la différence entre partager et dupliquer se compte en
     * centaines de mégaoctets.
     */
    @Test
    void lesViviersIdentiquesPartagentLaMemeInstance() {
        Schedule schedule = SchoolFixture.builder().build(SchoolFixture.SCENARIO).schedule();

        List<ResourceBooking> enseignants = schedule.getBookings().stream()
                .filter(booking -> booking.getRole().equals("enseignant"))
                .toList();

        assertThat(enseignants).hasSize(6);
        assertThat(enseignants).allSatisfy(booking ->
                assertThat(booking.getEligible()).isSameAs(enseignants.getFirst().getEligible()));
    }

    @Test
    void lesCriteresRestreignentReellementLeVivier() {
        Schedule schedule = SchoolFixture.builder().build(SchoolFixture.SCENARIO).schedule();

        // La 6e A compte 25 élèves : seule la grande salle convient.
        assertThat(schedule.getBookings().stream()
                .filter(booking -> booking.getRole().equals("salle"))
                .filter(booking -> booking.getActivity().getDemandLabel().contains("6e A"))
                .findFirst().orElseThrow()
                .getEligible()).hasSize(1);
    }

    /**
     * PIÈGE §2.4 — une exclusion par vivier est définitive et muette. Sans ce
     * rejet, le solveur échouerait à l'initialisation sans jamais dire pourquoi.
     */
    @Test
    void unVivierVideEstRefuseAvecUnMessageQuiNommeLeBesoin() {
        List<DemandRow> impossible = List.of(new DemandRow(UUID.randomUUID(),
                "Latin — 6e A", 60, 1,
                SchoolFixture.json("{}"),
                SchoolFixture.json("""
                        {"strategy":"free"}"""),
                List.of(new RequirementRow(UUID.randomUUID(), "enseignant",
                        SchoolFixture.T_ENSEIGNANT, 1,
                        SchoolFixture.json("""
                                {"contains":{"path":"/matieres","value":"latin"}}""")))));

        FakeProblemSource source = new FakeProblemSource(SchoolFixture.timeModel(),
                SchoolFixture.grid(), SchoolFixture.resources(), SchoolFixture.availability(),
                impossible, List.of(), SchoolFixture.weights());

        assertThatThrownBy(() -> SchoolFixture.builder(source).build(SchoolFixture.SCENARIO))
                .isInstanceOf(InfeasibleProblemException.class)
                .hasMessageContaining("enseignant")
                .hasMessageContaining("Latin — 6e A")
                .hasMessageContaining("latin");
    }

    /** §10.2 — une durée non représentable sur la grille ne rallonge rien. */
    @Test
    void uneDureeIncompatibleAvecLaGrilleEstRefusee() {
        List<DemandRow> bancale = List.of(new DemandRow(UUID.randomUUID(),
                "Atelier de 90 min", 90, 1,
                SchoolFixture.json("{}"),
                SchoolFixture.json("""
                        {"strategy":"free"}"""),
                List.of(new RequirementRow(UUID.randomUUID(), "enseignant",
                        SchoolFixture.T_ENSEIGNANT, 1, SchoolFixture.json("{}")))));

        FakeProblemSource source = new FakeProblemSource(SchoolFixture.timeModel(),
                SchoolFixture.grid(), SchoolFixture.resources(), SchoolFixture.availability(),
                bancale, List.of(), SchoolFixture.weights());

        assertThatThrownBy(() -> SchoolFixture.builder(source).build(SchoolFixture.SCENARIO))
                .hasMessageContaining("90")
                .hasMessageContaining("60");
    }
}
