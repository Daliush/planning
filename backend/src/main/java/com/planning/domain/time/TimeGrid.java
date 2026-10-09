package com.planning.domain.time;

import com.planning.domain.exception.IncompatibleTimeModelException;
import com.planning.domain.model.Timeslot;

import java.util.List;

/**
 * La granularité de la grille, dérivée de {@code slot_template} au chargement.
 *
 * <p><strong>Restriction d'implémentation assumée.</strong> Tous les créneaux d'un
 * modèle temporel doivent avoir la même durée. Les <em>intervalles</em> entre eux
 * restent libres — récréations, pause déjeuner, journée raccourcie le mercredi —
 * ce qui couvre les cas qui motivent la table {@code slot_template} (§6.7). Une
 * grille à durées mélangées rendrait {@code durationSlots} dépendant de la
 * position de départ, donc non calculable une fois pour toutes ; l'exception ici
 * vaut mieux qu'un planning silencieusement faux.
 */
public record TimeGrid(int slotMinutes) {

    public TimeGrid {
        if (slotMinutes <= 0) {
            throw new IncompatibleTimeModelException("La grille doit avoir une durée de créneau positive.");
        }
    }

    public static TimeGrid from(List<Timeslot> slots) {
        if (slots.isEmpty()) {
            throw new IncompatibleTimeModelException(
                    "Le modèle temporel ne contient aucun créneau : renseigner slot_template.");
        }
        if (slots.getFirst().index() != 0) {
            throw new IncompatibleTimeModelException("La grille doit commencer à l'index 0.");
        }
        int reference = slots.getFirst().durationMinutes();
        Timeslot previous = null;
        for (Timeslot slot : slots) {
            if (slot.index() < 0 || slot.ordinal() < 0 || slot.dayInCycle() < 0
                    || slot.durationMinutes() <= 0
                    || (previous != null && (slot.index() != previous.index() + 1
                    || slot.startDateTime().isBefore(previous.endDateTime())))) {
                throw new IncompatibleTimeModelException("La grille contient des créneaux inversés, chevauchants ou mal ordonnés : " + slot);
            }
            if (slot.durationMinutes() != reference) {
                throw new IncompatibleTimeModelException(
                        ("Les créneaux de la grille n'ont pas tous la même durée : %d min pour %s, "
                                + "%d min pour %s. Les intervalles entre créneaux peuvent varier, "
                                + "pas leur durée.")
                                .formatted(reference, slots.getFirst(), slot.durationMinutes(), slot));
            }
            previous = slot;
        }
        return new TimeGrid(reference);
    }

    /**
     * C'est le contrôle qui rend le changement de granularité sûr (§10.4). Passer
     * de 60 à 30 minutes ne casse rien ; passer de 30 à 45 lève une exception
     * explicite plutôt que de produire un planning faux.
     */
    public int toSlots(int minutes) {
        if (minutes % slotMinutes != 0) {
            throw new IncompatibleTimeModelException(
                    "Une durée de %d min n'est pas représentable sur une grille de %d min."
                            .formatted(minutes, slotMinutes));
        }
        return minutes / slotMinutes;
    }
}
