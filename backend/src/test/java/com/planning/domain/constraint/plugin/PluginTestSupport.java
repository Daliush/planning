package com.planning.domain.constraint.plugin;

import ai.timefold.solver.core.api.score.ScoreManager;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.planning.domain.builder.WeightBook;
import com.planning.domain.builder.AvailabilityCompiler;
import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.GenericConstraintProvider;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Effect;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.TimeProfile;
import com.planning.domain.model.Timeslot;
import com.planning.domain.port.Rows.ConstraintWeightRow;
import com.planning.domain.port.Rows.AbsenceRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.domain.solver.ExplanationService;
import com.planning.domain.time.GapPolicy;
import com.planning.domain.time.TimeGrid;
import com.planning.domain.time.TimeHorizon;
import com.planning.support.SchoolFixture;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fixtures datées et contrôle commun : score exact, deux poids et rapport métier. */
final class PluginTestSupport {
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private final TimeHorizon horizon;
    private final List<Resource> resources = new ArrayList<>();
    private final List<ScheduledActivity> activities = new ArrayList<>();
    private final List<ResourceBooking> bookings = new ArrayList<>();
    private final List<ConstraintConfig> configs = new ArrayList<>();

    PluginTestSupport(int days) {
        this(MONDAY, days);
    }

    PluginTestSupport(LocalDate from, int days) {
        List<Timeslot> slots = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            LocalDate date = from.plusDays(day);
            for (int hour = 0; hour < 24; hour++) {
                slots.add(new Timeslot(slots.size(), day, hour, date.getDayOfWeek(),
                        LocalTime.of(hour, 0), LocalTime.of((hour + 1) % 24, 0), date));
            }
        }
        horizon = new TimeHorizon.Dated(from, from.plusDays(days - 1), slots, new TimeGrid(60), GapPolicy.STRICT);
    }

    TimeHorizon horizon() { return horizon; }

    Resource resource(String label, String attributes) {
        return resource(label, attributes, TimeProfile.alwaysAvailable(horizon.size()));
    }

    Resource resource(String label, String attributes, TimeProfile time) {
        Resource resource = new Resource(UUID.randomUUID(), UUID.randomUUID(), label, time, SchoolFixture.json(attributes));
        resources.add(resource);
        return resource;
    }

    TimeProfile unavailableDays(int fromDay, int toDayExclusive) {
        BitSet allowed = new BitSet(horizon.size());
        allowed.set(0, horizon.size());
        allowed.clear(fromDay * 24, toDayExclusive * 24);
        return new TimeProfile(allowed, new BitSet());
    }

    TimeProfile absentDays(int fromDay, int toDayExclusive) {
        LocalDate from = ((TimeHorizon.Dated) horizon).from();
        UUID id = UUID.randomUUID();
        ResourceRow row = new ResourceRow(id, UUID.randomUUID(), "personne", "Absence",
                Effect.AVAILABLE, SchoolFixture.json("{}"));
        return new AvailabilityCompiler().compile(row, List.of(), horizon,
                List.of(new AbsenceRow(id, from.plusDays(fromDay), from.plusDays(toDayExclusive - 1))));
    }

    ScheduledActivity activity(UUID demandId, int occurrence, int day, int hour, int durationMinutes) {
        int durationSlots = durationMinutes / 60;
        List<Timeslot> validStarts = horizon.slots().subList(0,
                Math.max(0, horizon.size() - durationSlots + 1));
        ScheduledActivity activity = new ScheduledActivity(UUID.randomUUID(), demandId, "Poste",
                occurrence, durationMinutes, durationSlots, SchoolFixture.json("{}"), validStarts);
        activity.setTimeSlots(horizon.slots());
        activity.setStart(horizon.slotAt(day * 24 + hour));
        activities.add(activity);
        return activity;
    }

    ResourceBooking booking(ScheduledActivity activity, Resource resource) {
        ResourceBooking booking = new ResourceBooking(UUID.randomUUID(), activity, "participant", List.copyOf(resources), "{}");
        booking.setResource(resource);
        booking.setStart(activity.getStart());
        activity.getBookings().add(booking);
        bookings.add(booking);
        return booking;
    }

    ResourceBooking work(Resource resource, int day, int hour, int durationMinutes) {
        return booking(activity(UUID.randomUUID(), 0, day, hour, durationMinutes), resource);
    }

    ConstraintConfig rule(ConstraintPlugin plugin, Object params, int weight) {
        return rule(plugin, "{}", params, weight);
    }

    ConstraintConfig rule(ConstraintPlugin plugin, String scope, Object params, int weight) {
        ConstraintConfig config = new ConstraintConfig(UUID.randomUUID(), plugin.type(),
                CriterionParser.parse(SchoolFixture.json(scope)), params, weight, true, plugin.defaultLevel());
        configs.add(config);
        return config;
    }

    Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setHorizon(horizon);
        schedule.setTimeslots(horizon.slots());
        schedule.setResources(List.copyOf(resources));
        schedule.setActivities(List.copyOf(activities));
        schedule.setBookings(List.copyOf(bookings));
        schedule.setConfigs(List.copyOf(configs));
        return schedule;
    }

    static void assertScore(ConstraintPlugin plugin, Schedule schedule, int rulePenalty) {
        ConstraintRegistry registry = ConstraintRegistry.standard();
        List<ConstraintWeightRow> rows = registry.all().stream().map(candidate ->
                new ConstraintWeightRow(candidate.type(), candidate.defaultLevel(), candidate.type().equals(plugin.type()) ? 2 : 0))
                .toList();
        WeightBook weights = WeightBook.of(registry, rows);
        schedule.setConstraintWeightOverrides(weights.overrides());
        SolverConfig config = new SolverConfig().withSolutionClass(Schedule.class)
                .withEntityClasses(ScheduledActivity.class, ResourceBooking.class)
                .withConstraintProviderClass(GenericConstraintProvider.class);
        SolverFactory<Schedule> factory = SolverFactory.create(config);
        ScoreManager<Schedule, HardSoftScore> scores = ScoreManager.create(factory);
        HardSoftScore expected = plugin.defaultLevel() == Level.HARD
                ? HardSoftScore.ofHard(-2 * rulePenalty) : HardSoftScore.ofSoft(-2 * rulePenalty);
        assertThat(scores.updateScore(schedule)).isEqualTo(expected);
        ExplanationService explanation = new ExplanationService(registry);
        assertThat(explanation.collect(schedule).stream().mapToInt(violation -> violation.penalty()).sum()).isEqualTo(rulePenalty);
        ExplanationService.Totals totals = explanation.totals(schedule, weights);
        assertThat(schedule.getScore()).isEqualTo(HardSoftScore.of(-totals.hard(), -totals.soft()));
    }
}
