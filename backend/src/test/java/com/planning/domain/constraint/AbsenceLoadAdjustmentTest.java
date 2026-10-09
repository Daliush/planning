package com.planning.domain.constraint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbsenceLoadAdjustmentTest {
    @Test
    void leCreditEstBorneAuContratEtPreserveLaMargeSupplementaire() {
        int deduction = AbsenceLoadAdjustment.deductionMinutes(600, 180, 7);
        assertThat(deduction).isEqualTo(600);
        assertThat(AbsenceLoadAdjustment.adjustedMinutes(720, deduction)).isEqualTo(120);
        assertThat(AbsenceLoadAdjustment.adjustedMinutes(600, deduction)).isZero();
        assertThat(AbsenceLoadAdjustment.deductionMinutes(0, 180, 7)).isZero();
    }

    @Test
    void leProduitDesDuréesNeDebordePasAvantLePlafonnement() {
        assertThat(AbsenceLoadAdjustment.deductionMinutes(600, Integer.MAX_VALUE, Integer.MAX_VALUE))
                .isEqualTo(600);
    }
}
