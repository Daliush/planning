package com.planning.domain.model;

/** Période réelle du problème, présente même quand aucune affectation ne la remplit. */
public record PeriodBucket(Period period, int index, String label) {
}
