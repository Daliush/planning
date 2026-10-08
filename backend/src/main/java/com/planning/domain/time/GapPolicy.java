package com.planning.domain.time;

/**
 * La politique de coupure (§10.3).
 *
 * <p>PIÈGE central : deux créneaux d'index successifs ne sont pas nécessairement
 * collés dans le temps. Un cours de 2 h à cheval sur la récréation est normal en
 * collège ; une réunion client coupée en deux ne l'est pas. Une pause déjeuner de
 * 105 minutes reste infranchissable dans les deux cas.
 */
public record GapPolicy(int maxGapMinutes) {

    public static final GapPolicy STRICT = new GapPolicy(0);
    public static final GapPolicy BREAKS = new GapPolicy(20);

    public GapPolicy {
        if (maxGapMinutes < 0) {
            throw new IllegalArgumentException("maxGapMinutes doit être positif ou nul");
        }
    }

    public boolean tolerates(int gapMinutes) {
        return gapMinutes <= maxGapMinutes;
    }
}
