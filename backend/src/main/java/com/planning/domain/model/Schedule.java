package com.planning.domain.model;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.planning.domain.time.TimeHorizon;

import java.util.List;

/**
 * Une classe, deux états : {@code Schedule} décrit le problème <em>et</em>
 * contient la solution. On en construit une instance avec les variables à
 * {@code null}, le solveur en rend une autre avec ces variables remplies.
 *
 * <p>INVARIANT §8.2 : aucun {@code @ValueRangeProvider} ici. Tous les viviers
 * sont portés par les entités. {@code timeslots} et {@code resources} ne sont pas
 * des viviers, ce sont les données sur lesquelles les contraintes ont le droit de
 * raisonner — indispensables aux règles portant sur une <em>absence</em>
 * (« aucun enseignant sans cours ») que les seuls bookings ne révéleraient pas.
 */
@PlanningSolution
public class Schedule {

    @ProblemFactCollectionProperty
    private List<Timeslot> timeslots = List.of();

    @ProblemFactCollectionProperty
    private List<Resource> resources = List.of();

    @ProblemFactCollectionProperty
    private List<ConstraintConfig> configs = List.of();

    @PlanningEntityCollectionProperty
    private List<ScheduledActivity> activities = List.of();

    @PlanningEntityCollectionProperty
    private List<ResourceBooking> bookings = List.of();

    /**
     * Poids ① — détecté par Timefold sur le type du champ. Surcharge à la fois le
     * poids et le niveau de chaque contrainte : c'est le mécanisme de bascule
     * dur ↔ souple sans redéploiement (§13.2).
     */
    private ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides =
            ConstraintWeightOverrides.none();

    @PlanningScore
    private HardSoftScore score;

    /** Hors modèle Timefold, transporté pour le builder et l'explicabilité. */
    private TimeHorizon horizon;

    public List<Timeslot> getTimeslots() {
        return timeslots;
    }

    public void setTimeslots(List<Timeslot> timeslots) {
        this.timeslots = timeslots;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public List<ConstraintConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(List<ConstraintConfig> configs) {
        this.configs = configs;
    }

    public List<ScheduledActivity> getActivities() {
        return activities;
    }

    public void setActivities(List<ScheduledActivity> activities) {
        this.activities = activities;
    }

    public List<ResourceBooking> getBookings() {
        return bookings;
    }

    public void setBookings(List<ResourceBooking> bookings) {
        this.bookings = bookings;
    }

    public ConstraintWeightOverrides<HardSoftScore> getConstraintWeightOverrides() {
        return constraintWeightOverrides;
    }

    public void setConstraintWeightOverrides(ConstraintWeightOverrides<HardSoftScore> overrides) {
        this.constraintWeightOverrides = overrides;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public TimeHorizon getHorizon() {
        return horizon;
    }

    public void setHorizon(TimeHorizon horizon) {
        this.horizon = horizon;
    }
}
