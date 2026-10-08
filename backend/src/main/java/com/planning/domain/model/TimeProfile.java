package com.planning.domain.model;

import java.util.BitSet;

/**
 * Disponibilités compilées d'une ressource (§7.3).
 *
 * <p>Les deux {@link BitSet} ne sont pas deux niveaux d'un même axe :
 * {@code allowed} répond « est-ce permis ? », {@code discouraged} répond
 * « est-ce souhaitable ? ». Un créneau découragé est permis <em>et</em> pénalisé.
 *
 * <p>Nommer {@code allowed} et jamais {@code available} : le second se lit
 * spontanément comme « est-ce souhaitable ? », ce qui rend le code de la
 * contrainte dure trompeur.
 */
public record TimeProfile(BitSet allowed, BitSet discouraged) {

    public TimeProfile {
        BitSet orphans = (BitSet) discouraged.clone();
        orphans.andNot(allowed);
        if (!orphans.isEmpty()) {
            throw new IllegalStateException(
                    "Créneaux découragés hors des créneaux permis : " + orphans);
        }
        // Clonage défensif : BitSet est le seul champ mutable subsistant dans les
        // faits, et les faits sont partagés par référence entre tous les clones
        // de solution (§8.4).
        allowed = (BitSet) allowed.clone();
        discouraged = (BitSet) discouraged.clone();
    }

    /** Toute la plage [from, toExclusive) est-elle permise ? */
    public boolean isAllowed(int from, int toExclusive) {
        return allowed.nextClearBit(from) >= toExclusive;
    }

    /** Au moins un créneau de la plage est-il découragé ? */
    public boolean isDiscouraged(int from, int toExclusive) {
        int next = discouraged.nextSetBit(from);
        return next >= 0 && next < toExclusive;
    }

    /**
     * Nombre de créneaux interdits dans la plage — sert de poids ③ : une séance
     * débordant d'un seul créneau coûte moins qu'une séance entièrement en zone
     * fermée.
     */
    public int forbiddenSlots(int from, int toExclusive) {
        BitSet window = allowed.get(from, toExclusive);
        return (toExclusive - from) - window.cardinality();
    }

    /** Nombre de créneaux découragés dans la plage. */
    public int discouragedSlots(int from, int toExclusive) {
        return discouraged.get(from, toExclusive).cardinality();
    }

    public static TimeProfile alwaysAvailable(int size) {
        BitSet all = new BitSet(size);
        all.set(0, size);
        return new TimeProfile(all, new BitSet(size));
    }
}
