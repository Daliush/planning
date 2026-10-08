package com.planning.domain.model;

/** Découpage d'agrégation d'une règle de charge. */
public enum Period {
    DAY,
    WEEK,
    CYCLE,
    /** Refusé en horizon CYCLIC : un mois n'est ni aligné ni de longueur fixe (§14.4). */
    MONTH
}
