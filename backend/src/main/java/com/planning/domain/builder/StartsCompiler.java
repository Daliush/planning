package com.planning.domain.builder;

import com.planning.domain.constraint.Bookings;
import com.planning.domain.model.Timeslot;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.time.GapPolicy;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

/**
 * Le vivier de {@code ScheduledActivity} : les départs matériellement possibles
 * (§10.2).
 *
 * <p>Seule asymétrie avec {@code eligible} : ce filtre porte sur une
 * <em>relation entre créneaux voisins</em> — la durée doit tenir sur une suite
 * suffisamment contiguë — d'où une boucle avec regard en avant plutôt qu'un simple
 * {@code filter}. Du point de vue de Timefold, la différence n'existe pas : il
 * reçoit deux listes.
 */
public class StartsCompiler {

    /** Clé de mutualisation : deux demandes de même durée partagent leur vivier. */
    public record Key(int durationMinutes, int maxGapMinutes) {
    }

    public List<Timeslot> feasibleStarts(TimeHorizon horizon, int durationMinutes) {
        List<Timeslot> slots = horizon.slots();
        GapPolicy policy = horizon.gapPolicy();
        List<Timeslot> result = new ArrayList<>();

        for (int i = 0; i < slots.size(); i++) {
            if (fits(slots, i, durationMinutes, policy, horizon.kind() == HorizonKind.DATED)) {
                result.add(slots.get(i));
            }
        }
        return List.copyOf(result);
    }

    private boolean fits(List<Timeslot> slots, int from, int durationMinutes, GapPolicy policy,
                         boolean acrossDays) {
        Timeslot first = slots.get(from);
        int remaining = durationMinutes;

        for (int k = from; remaining > 0; k++) {
            if (k >= slots.size()) {
                return false;
            }
            Timeslot current = slots.get(k);
            if (!acrossDays && current.dayInCycle() != first.dayInCycle()) {
                return false;
            }
            if (k > from) {
                int gap = Math.toIntExact(Duration.between(slots.get(k - 1).endDateTime(),
                        current.startDateTime()).toMinutes());
                if (!policy.tolerates(gap)) {
                    return false;
                }
            }
            remaining -= current.durationMinutes();
        }
        // remaining == 0 : pas de débordement partiel. Sur une grille de 55 min,
        // une durée de 90 ne retombe jamais juste — mieux vaut aucun départ valide,
        // et une exception explicite du validateur, qu'un cours silencieusement
        // rallongé.
        return remaining == 0;
    }
}
