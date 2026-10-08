package com.planning.domain.constraint;

import com.planning.domain.model.Resource;

/** « Une ressource, un jour du cycle » — la journée de travail de quelqu'un. */
public record ResourceDayKey(Resource resource, int dayInCycle) {
}
