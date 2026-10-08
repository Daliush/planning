package com.planning.domain.constraint;

import java.util.UUID;

/** « Les séances d'un même cours tombant le même jour du cycle ». */
public record DemandDayKey(UUID demandId, int dayInCycle) {
}
