package com.planning.domain.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.Period;
import com.planning.domain.model.Resource;

/** Crédit d'absence explicite, partagé par les règles de charge et leurs projections. */
public final class AbsenceLoadAdjustment {
    public static final String CONTRACT_MINUTES = "quota_hebdo_minutes";
    public static final String DAILY_MINUTES = "minutes_absence_jour";

    private AbsenceLoadAdjustment() { }

    /** Le crédit ne consomme jamais la marge d'heures supplémentaires. */
    public static int deductionMinutes(int contractMinutes, int dailyMinutes, int absenceDays) {
        if (contractMinutes < 0 || dailyMinutes < 0 || absenceDays < 0) {
            throw new IllegalArgumentException("Le contrat, la durée journalière et les jours d'absence doivent être positifs ou nuls.");
        }
        return (int) Math.min(contractMinutes, Math.multiplyExact((long) dailyMinutes, absenceDays));
    }

    public static int adjustedMinutes(int baseMinutes, int deductionMinutes) {
        if (baseMinutes < 0 || deductionMinutes < 0) {
            throw new IllegalArgumentException("La charge et le crédit d'absence doivent être positifs ou nuls.");
        }
        return Math.max(0, baseMinutes - deductionMinutes);
    }

    public static boolean hasContract(Resource resource) {
        return resource.attributes().hasNonNull(CONTRACT_MINUTES);
    }

    public static int contractMinutes(Resource resource) {
        return attributeMinutes(resource, CONTRACT_MINUTES, "Le quota hebdomadaire");
    }

    public static int deductionMinutes(Resource resource, Period period, int periodIndex, boolean deductAbsences) {
        if (!deductAbsences || period != Period.WEEK) return 0;
        int absenceDays = resource.time().absenceDaysByWeek().getOrDefault(periodIndex, 0);
        if (absenceDays == 0 || !hasContract(resource) || !resource.attributes().hasNonNull(DAILY_MINUTES)) return 0;
        return deductionMinutes(contractMinutes(resource),
                attributeMinutes(resource, DAILY_MINUTES, "La durée d'absence journalière"), absenceDays);
    }

    private static int attributeMinutes(Resource resource, String key, String label) {
        JsonNode minutes = resource.attributes().get(key);
        if (minutes == null || !minutes.isIntegralNumber() || !minutes.canConvertToInt() || minutes.intValue() < 0) {
            throw new IncompatibleConfigException("%s de %s doit être un nombre entier positif ou nul."
                    .formatted(label, resource.label()));
        }
        return minutes.intValue();
    }
}
