package com.planning.domain.model;

/**
 * Niveau d'une contrainte. Une contrainte est soit dure, soit souple, jamais les
 * deux : le score étant lexicographique, une pénalité comptée en dur rend la
 * solution invalide et les points souples associés ne seraient jamais regardés
 * (§13.7).
 */
public enum Level {
    HARD,
    SOFT
}
