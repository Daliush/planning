package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.tri.TriConstraintStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.planning.domain.constraint.AbsenceLoadAdjustment;
import com.planning.domain.constraint.AggregationScope;
import com.planning.domain.constraint.Bookings;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.Schemas;
import com.planning.domain.constraint.Streams;
import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Period;
import com.planning.domain.model.PeriodBucket;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Timeslot;
import com.planning.domain.model.Violation;
import com.planning.domain.time.TemporalBuckets;
import com.planning.domain.time.TimeHorizon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cible contractuelle individuelle, distincte d'un plafond uniforme ou d'un équilibre. */
public final class ContractLoadConstraint implements ConstraintPlugin {
    public static final String TYPE = "load.contract";
    private static final String EMPTY_WEEK = "load.contract.empty_week";
    public record Params(boolean deductAbsences) {
        public Params() { this(false); }
    }

    private static Params params(ConstraintConfig rule) {
        return rule.paramsAs(Params.class);
    }

    private static boolean hasQuota(Resource resource) {
        return AbsenceLoadAdjustment.hasContract(resource);
    }

    private static int quotaMinutes(Resource resource) {
        return AbsenceLoadAdjustment.contractMinutes(resource);
    }

    private static boolean belongsTo(ResourceBooking booking, PeriodBucket bucket) {
        return booking.isPlaced() && Bookings.minutesIn(booking, bucket) > 0;
    }

    private static boolean weekApplies(Resource resource, Timeslot slot, PeriodBucket bucket, Params params) {
        return TemporalBuckets.bucketOf(slot, Period.WEEK) == bucket.index()
                && ((params.deductAbsences() && slot.date() != null)
                || resource.time().isAllowed(slot.index(), slot.index() + 1));
    }

    private static int targetMinutes(Resource resource, int weekIndex, Params params) {
        return AbsenceLoadAdjustment.adjustedMinutes(quotaMinutes(resource),
                AbsenceLoadAdjustment.deductionMinutes(resource, Period.WEEK, weekIndex, params.deductAbsences()));
    }

    private static int penalty(int totalMinutes, Resource resource, int weekIndex, Params params, int ruleWeight) {
        return Math.toIntExact(Math.multiplyExact(Math.abs((long) totalMinutes - targetMinutes(resource, weekIndex, params)), ruleWeight));
    }

    private static TriConstraintStream<ConstraintConfig, Resource, PeriodBucket> scopedWeeks(ConstraintFactory factory) {
        return factory.forEach(ConstraintConfig.class)
                .filter(rule -> rule.type().equals(TYPE))
                .join(Resource.class, Joiners.filtering((rule, resource) -> rule.scope().matches(resource) && hasQuota(resource)))
                .join(PeriodBucket.class, Joiners.filtering((rule, resource, bucket) -> bucket.period() == Period.WEEK))
                .ifExists(Timeslot.class,
                        Joiners.equal((rule, resource, bucket) -> bucket.index(), slot -> TemporalBuckets.bucketOf(slot, Period.WEEK)),
                        Joiners.filtering((rule, resource, bucket, slot) -> weekApplies(resource, slot, bucket, params(rule))));
    }

    @Override public String type() { return TYPE; }
    @Override public String label() { return "Charge contractuelle hebdomadaire"; }
    @Override public String description() {
        return "Rapprocher la charge hebdomadaire du quota propre à chaque ressource (quota_hebdo_minutes). "
                + "Le réglage de déduction crédite chaque jour d'absence datée selon sa durée explicite, dans la limite du contrat. "
                + "Sans ce réglage, une semaine entièrement indisponible est exclue et les semaines partielles gardent la cible du contrat ; "
                + "le travail de nuit est réparti entre les semaines réellement travaillées.";
    }
    @Override public Level defaultLevel() { return Level.SOFT; }
    @Override public Class<?> paramsType() { return Params.class; }
    @Override public JsonNode paramSchema() {
        return Schemas.properties()
                .add("deductAbsences", JsonNodeFactory.instance.objectNode().put("type", "boolean")
                        .put("default", false).put("description", "Déduire les absences datées de la charge hebdomadaire"))
                .required();
    }
    @Override public Set<String> readsAttributes() {
        return Set.of(AbsenceLoadAdjustment.CONTRACT_MINUTES, AbsenceLoadAdjustment.DAILY_MINUTES);
    }
    @Override public void validate(ConstraintConfig rule, TimeHorizon horizon) {
        if (!horizon.supports(Period.WEEK)) {
            throw new IncompatibleConfigException("La charge contractuelle exige un horizon découpé en semaines.");
        }
    }

    @Override public Constraint build(ConstraintFactory factory) {
        return scopedWeeks(factory)
                .join(ResourceBooking.class,
                        Joiners.equal((rule, resource, bucket) -> resource, ResourceBooking::getResource),
                        Joiners.filtering((rule, resource, bucket, booking) -> belongsTo(booking, bucket)))
                .groupBy((rule, resource, bucket, booking) -> rule,
                        (rule, resource, bucket, booking) -> new AggregationScope(resource, Period.WEEK, bucket.index()),
                        ConstraintCollectors.sum((rule, resource, bucket, booking) -> Bookings.minutesIn(booking, bucket)))
                .filter((rule, scope, total) -> penalty(total, scope.resource(), scope.periodIndex(), params(rule), rule.weight()) > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, scope, total) -> penalty(total, scope.resource(), scope.periodIndex(), params(rule), rule.weight()))
                .asConstraint(TYPE);
    }

    private Constraint buildEmptyWeek(ConstraintFactory factory) {
        return scopedWeeks(factory)
                .ifNotExists(ResourceBooking.class,
                        Joiners.equal((rule, resource, bucket) -> resource, ResourceBooking::getResource),
                        Joiners.filtering((rule, resource, bucket, booking) -> belongsTo(booking, bucket)))
                .filter((rule, resource, bucket) -> penalty(0, resource, bucket.index(), params(rule), rule.weight()) > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (rule, resource, bucket) -> penalty(0, resource, bucket.index(), params(rule), rule.weight()))
                .asConstraint(EMPTY_WEEK);
    }

    @Override public List<Constraint> buildAll(ConstraintFactory factory) {
        return List.of(build(factory), buildEmptyWeek(factory));
    }
    @Override public List<String> constraintNames() { return List.of(TYPE, EMPTY_WEEK); }

    @Override public List<Violation> validate(Schedule solution, ConstraintConfig rule) {
        Params params = params(rule);
        Map<AggregationScope, List<ResourceBooking>> members = new LinkedHashMap<>();
        Streams.placedBookings(solution.getBookings(), rule).forEach(booking -> {
            for (PeriodBucket bucket : solution.getPeriodBuckets()) {
                if (bucket.period() == Period.WEEK && belongsTo(booking, bucket)) {
                    members.computeIfAbsent(new AggregationScope(booking.getResource(), Period.WEEK,
                            bucket.index()), key -> new ArrayList<>()).add(booking);
                }
            }
        });
        List<Violation> violations = new ArrayList<>();
        for (Resource resource : solution.getResources()) {
            if (!rule.scope().matches(resource) || !hasQuota(resource)) {
                continue;
            }
            for (PeriodBucket bucket : solution.getPeriodBuckets()) {
                if (bucket.period() != Period.WEEK
                        || solution.getTimeslots().stream().noneMatch(slot -> weekApplies(resource, slot, bucket, params))) {
                    continue;
                }
                List<ResourceBooking> bookings = members.getOrDefault(
                        new AggregationScope(resource, Period.WEEK, bucket.index()), List.of());
                int total = bookings.stream().mapToInt(booking -> Bookings.minutesIn(booking, bucket)).reduce(0, Math::addExact);
                int targetMinutes = targetMinutes(resource, bucket.index(), params);
                int penalty = penalty(total, resource, bucket.index(), params, rule.weight());
                if (penalty == 0) {
                    continue;
                }
                violations.add(new Violation(TYPE, rule.level(), penalty,
                        "%s travaille %s %s, pour une cible contractuelle de %s."
                                .formatted(resource.label(), Bookings.hoursMinutes(total), bucket.label(),
                                        Bookings.hoursMinutes(targetMinutes)),
                        List.of(resource.id()), bookings.stream().map(booking -> booking.getActivity().getDemandId()).distinct().toList(),
                        Map.of("totalMinutes", total, "targetMinutes", targetMinutes,
                                "period", Period.WEEK.name(), "periodIndex", bucket.index())));
            }
        }
        return violations;
    }
}
