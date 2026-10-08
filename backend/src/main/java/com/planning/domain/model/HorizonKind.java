package com.planning.domain.model;

/** Mode d'horizon temporel, choisi par le profil métier (ADR-006). */
public enum HorizonKind {
    /** Position dans un cycle de N jours. Cible : scolaire. Seul mode implémenté en V1. */
    CYCLIC,
    /** Dates réelles sur un horizon glissant. Cible : entreprise, restauration. Jalon 4. */
    DATED
}
