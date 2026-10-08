package com.planning.domain.model;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;

/**
 * Recopie le créneau de l'activité sur chacun de ses bookings.
 *
 * <p>C'est ce qui rend la dépendance « le poste dépend du moment » visible par le
 * réseau de propagation incrémentale. Sans elle, déplacer une séance laisserait
 * les tuples des bookings intacts et le score deviendrait faux — le genre de bug
 * que {@code environmentMode=FULL_ASSERT} fait tomber immédiatement (§11.8).
 */
public class BookingStartListener implements VariableListener<Schedule, ScheduledActivity> {

    @Override
    public void beforeEntityAdded(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        // rien
    }

    @Override
    public void afterEntityAdded(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        propagate(scoreDirector, activity);
    }

    @Override
    public void beforeVariableChanged(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        // rien
    }

    @Override
    public void afterVariableChanged(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        propagate(scoreDirector, activity);
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        // rien
    }

    @Override
    public void afterEntityRemoved(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        // rien
    }

    private void propagate(ScoreDirector<Schedule> scoreDirector, ScheduledActivity activity) {
        for (ResourceBooking booking : activity.getBookings()) {
            scoreDirector.beforeVariableChanged(booking, "start");
            booking.setStart(activity.getStart());
            scoreDirector.afterVariableChanged(booking, "start");
        }
    }
}
