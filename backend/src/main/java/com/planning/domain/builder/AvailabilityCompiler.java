package com.planning.domain.builder;

import com.planning.domain.model.Effect;
import com.planning.domain.model.TimeProfile;
import com.planning.domain.model.Timeslot;
import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ResourceRow;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

/**
 * Traduit les lignes lisibles de {@code resource_availability} en deux
 * {@link BitSet} (§9.3). C'est l'exemple canonique du §2.3 : une forme éditable en
 * base, une forme rapide en mémoire, et un builder qui traduit une fois.
 */
public class AvailabilityCompiler {

    private static final Comparator<AvailabilityRow> BY_PRIORITY =
            Comparator.comparingInt(AvailabilityRow::priority);

    public TimeProfile compile(ResourceRow resource, List<AvailabilityRow> rules, List<Timeslot> slots) {
        int size = slots.size();
        BitSet allowed = new BitSet(size);
        BitSet discouraged = new BitSet(size);

        Effect defaultEffect = resource.defaultEffect();
        allowed.set(0, size, defaultEffect.allowed());
        discouraged.set(0, size, defaultEffect.discouraged());

        for (AvailabilityRow rule : sortedByPriority(rules)) {
            Effect effect = rule.effect();
            for (Timeslot slot : slots) {
                if (covers(rule, slot, effect)) {
                    // PIÈGE §9.3 : écrire les DEUX bits à chaque passage. Avec un
                    // set conditionnel, un DISCOURAGED suivi d'un UNAVAILABLE
                    // prioritaire laisserait le créneau interdit ET découragé,
                    // déclenchant les deux contraintes et comptant double.
                    allowed.set(slot.index(), effect.allowed());
                    discouraged.set(slot.index(), effect.discouraged());
                }
            }
        }
        // L'invariant discouraged ⊆ allowed est vérifié par le constructeur.
        return new TimeProfile(allowed, discouraged);
    }

    private List<AvailabilityRow> sortedByPriority(List<AvailabilityRow> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<AvailabilityRow> sorted = new ArrayList<>(rules);
        // Priorité croissante, la dernière appliquée gagne. À priorité égale, on
        // départage par l'ordre de déclaration de l'énumération — AVAILABLE,
        // DISCOURAGED, UNAVAILABLE — de sorte que la règle la plus restrictive
        // s'applique en dernier : en cas de doute, on ne planifie pas (§6.5).
        sorted.sort(BY_PRIORITY.thenComparing(AvailabilityRow::effect));
        return sorted;
    }

    /**
     * PIÈGE §6.5 — le test de couverture n'est pas symétrique. Pour
     * {@code UNAVAILABLE} un chevauchement suffit ; pour {@code AVAILABLE} il faut
     * une inclusion complète, sans quoi un créneau à moitié couvert par une
     * ouverture serait considéré comme entièrement disponible alors que la
     * ressource n'est là que la moitié du temps.
     */
    private boolean covers(AvailabilityRow rule, Timeslot slot, Effect effect) {
        // PIÈGE §9.3 : une colonne NULL signifie « pas de restriction sur cet
        // axe ». Un test naïf d'égalité ne matcherait rien, silencieusement.
        if (rule.dayInCycle() != null && rule.dayInCycle() != slot.dayInCycle()) {
            return false;
        }
        LocalTime from = rule.startTime() == null ? LocalTime.MIN : rule.startTime();
        LocalTime to = rule.endTime() == null ? LocalTime.MAX : rule.endTime();

        return effect.allowed()
                ? !slot.start().isBefore(from) && !slot.end().isAfter(to)
                : slot.start().isBefore(to) && from.isBefore(slot.end());
    }
}
