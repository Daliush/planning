package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.ScopeTarget;
import com.planning.domain.constraint.Streams;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.CycleBuckets;
import com.planning.domain.time.TimeHorizon;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * « Les mathématiques plutôt le matin. »
 *
 * <p>C'est l'exemple canonique de la frontière du §2.4 : une préférence reste une
 * contrainte souple et n'entre <strong>jamais</strong> dans le vivier. Placée en
 * {@code placement.window}, elle produirait « aucune solution » sans que
 * l'utilisateur sache jamais quelle règle bloque ; ici, elle apparaît noir sur
 * blanc dans le rapport.
 *
 * <p>Le scope s'évalue contre {@code demand.attributes} : la préférence porte sur
 * la matière, pas sur la ressource.
 */
public final class PreferredWindowConstraint implements ConstraintPlugin {

    public static final String TYPE = "time.preferred_window";

    public record Params(String from, String to) {
    }

    // ═══ LOGIQUE MÉTIER — ÉCRITE UNE SEULE FOIS ═══

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    /** Minutes de la séance tombant hors de la fenêtre souhaitée. */
    private static int outsideMinutes(ScheduledActivity activity, Params params) {
        LocalTime from = LocalTime.parse(params.from());
        LocalTime to = LocalTime.parse(params.to());
        LocalTime start = activity.getStart().start();
        LocalTime end = start.plusMinutes(activity.getDurationMinutes());

        int before = Math.max(0, Bookings.minutesBetween(start, min(from, end)));
        int after = Math.max(0, Bookings.minutesBetween(max(to, start), end));
        return before + after;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static int penalty(int outsideMinutes, int ruleWeight) {
        return ruleWeight * outsideMinutes;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String label() {
        return "Fenêtre horaire préférée";
    }

    @Override
    public String description() {
        return "Souhaiter — sans l'imposer — que certaines séances tombent dans une plage horaire.";
    }

    @Override
    public Level defaultLevel() {
        return Level.SOFT;
    }

    @Override
    public ScopeTarget scopeTarget() {
        // Le scope parle de la demande — matière, niveau — pas de la ressource.
        return ScopeTarget.DEMAND;
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public JsonNode paramSchema() {
        return Schemas.properties()
                .add("from", Schemas.time("Début de la fenêtre souhaitée"))
                .add("to", Schemas.time("Fin de la fenêtre souhaitée"))
                .required("from", "to");
    }

    @Override
    public void validate(ConstraintConfig config, TimeHorizon horizon) {
        Params params = params(config);
        LocalTime from = LocalTime.parse(params.from());
        LocalTime to = LocalTime.parse(params.to());
        if (!from.isBefore(to)) {
            throw new InvalidConfigException(
                    "Fenêtre horaire inversée : « from » (%s) doit précéder « to » (%s)."
                            .formatted(from, to));
        }
    }

    // ═══ TRAVERSÉE A ═══

    @Override
    public Constraint build(ConstraintFactory factory) {
        return Streams.scopedActivities(factory, TYPE)
                .filter((rule, activity) -> outsideMinutes(activity, params(rule)) > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, activity) -> penalty(outsideMinutes(activity, params(rule)), rule.weight()))
                .asConstraint(TYPE);
    }

    // ═══ TRAVERSÉE B ═══

    @Override
    public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        return Streams.scopedActivities(solution.getActivities(), rule)
                .filter(activity -> outsideMinutes(activity, params) > 0)
                .map(activity -> new Violation(
                        TYPE,
                        rule.level(),
                        penalty(outsideMinutes(activity, params), rule.weight()),
                        "« %s » tombe %s à %s, soit %s hors de la plage souhaitée %s–%s."
                                .formatted(activity.getDemandLabel(),
                                        CycleBuckets.label(com.planning.domain.model.Period.DAY,
                                                activity.dayInCycle()),
                                        activity.getStart().start(),
                                        Bookings.hoursMinutes(outsideMinutes(activity, params)),
                                        params.from(), params.to()),
                        List.of(),
                        List.of(activity.getDemandId()),
                        Map.of("outsideMinutes", outsideMinutes(activity, params),
                                "from", params.from(),
                                "to", params.to(),
                                "startTime", activity.getStart().start().toString())))
                .toList();
    }
}
