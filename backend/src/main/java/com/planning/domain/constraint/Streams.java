package com.planning.domain.constraint;

import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.ScheduledActivity;

import java.util.List;
import java.util.stream.Stream;

/**
 * Les têtes de pipeline partagées par les plugins.
 *
 * <p>Chaque paire {@code (règle, booking)} produite ici est déjà filtrée par le
 * scope : le plugin n'a plus qu'à agréger. Filtrer tôt allège tout ce qui suit
 * (§11.8).
 */
public final class Streams {

    private Streams() {
    }

    /** Les postes pourvus et placés — les seuls sur lesquels une règle a prise. */
    public static UniConstraintStream<ResourceBooking> placedBookings(ConstraintFactory factory) {
        return factory.forEach(ResourceBooking.class).filter(ResourceBooking::isPlaced);
    }

    /**
     * Forme B du §11.3 : les paramètres viennent de la configuration. Le
     * {@code filter} sur le type ne réduit jamais à une seule ligne — c'est tout
     * l'intérêt du système, et c'est pourquoi la règle doit ensuite faire partie
     * de la clé d'agrégation.
     */
    public static BiConstraintStream<ConstraintConfig, ResourceBooking> scopedBookings(
            ConstraintFactory factory, String type) {
        return factory.forEach(ConstraintConfig.class)
                .filter(rule -> rule.type().equals(type))
                .join(ResourceBooking.class, Joiners.filtering(
                        (rule, booking) -> booking.isPlaced()
                                && rule.scope().matches(booking.getResource())));
    }

    /**
     * Variante pour les règles qui parlent de la <em>demande</em> et non de la
     * ressource — « les maths plutôt le matin ». Le scope est alors évalué contre
     * {@code demand.attributes}, jamais contre {@code placement} : les séparer
     * évite qu'un scope matche accidentellement une clé de placement (§6.9).
     */
    public static BiConstraintStream<ConstraintConfig, ScheduledActivity> scopedActivities(
            ConstraintFactory factory, String type) {
        return factory.forEach(ConstraintConfig.class)
                .filter(rule -> rule.type().equals(type))
                .join(ScheduledActivity.class, Joiners.filtering(
                        (rule, activity) -> rule.scope().test(activity.getAttributes())));
    }

    // ─── Équivalents pour la traversée d'explicabilité ──────────────────────
    //
    // Mêmes prédicats que ci-dessus, exprimés sur une solution figée. Le fait
    // qu'ils soient écrits ici, à côté de leurs jumeaux, rend une divergence
    // visible à la relecture.

    public static Stream<ResourceBooking> placedBookings(List<ResourceBooking> bookings,
                                                         ConstraintConfig rule) {
        return bookings.stream()
                .filter(ResourceBooking::isPlaced)
                .filter(booking -> rule.scope().matches(booking.getResource()));
    }

    public static Stream<ScheduledActivity> scopedActivities(List<ScheduledActivity> activities,
                                                             ConstraintConfig rule) {
        return activities.stream()
                .filter(activity -> activity.getStart() != null)
                .filter(activity -> rule.scope().test(activity.getAttributes()));
    }
}
