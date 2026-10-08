package com.planning.support;

import com.planning.domain.builder.HorizonFactory;
import com.planning.domain.criteria.CriterionParser;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.Level;
import com.planning.domain.model.Resource;
import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.ScheduledActivity;
import com.planning.domain.model.TimeProfile;
import com.planning.domain.model.Timeslot;
import com.planning.domain.time.TimeHorizon;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monte une solution figée à la main, pour éprouver la traversée d'explicabilité
 * d'un plugin sans lancer le solveur.
 */
public final class ScheduleBuilder {

    private final TimeHorizon horizon =
            new HorizonFactory().create(SchoolFixture.timeModel(), SchoolFixture.grid());

    private final List<Resource> resources = new ArrayList<>();
    private final List<ScheduledActivity> activities = new ArrayList<>();
    private final List<ResourceBooking> bookings = new ArrayList<>();
    private final List<ConstraintConfig> configs = new ArrayList<>();

    public TimeHorizon horizon() {
        return horizon;
    }

    public Timeslot slot(int dayInCycle, LocalTime start) {
        return horizon.slots().stream()
                .filter(candidate -> candidate.dayInCycle() == dayInCycle
                        && candidate.start().equals(start))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun créneau le jour %d à %s".formatted(dayInCycle, start)));
    }

    public Resource resource(String label, String attributesJson) {
        Resource resource = new Resource(UUID.randomUUID(), UUID.randomUUID(), label,
                TimeProfile.alwaysAvailable(horizon.size()), SchoolFixture.json(attributesJson));
        resources.add(resource);
        return resource;
    }

    /** Une séance placée, avec ses postes déjà pourvus. */
    public ScheduledActivity activity(String demandLabel, UUID demandId, int occurrence,
                                      int durationMinutes, Timeslot start, String attributesJson) {
        ScheduledActivity activity = new ScheduledActivity(UUID.randomUUID(), demandId, demandLabel,
                occurrence, durationMinutes, durationMinutes / 60,
                SchoolFixture.json(attributesJson), horizon.slots());
        activity.setStart(start);
        activities.add(activity);
        return activity;
    }

    public ResourceBooking booking(ScheduledActivity activity, String role, Resource resource) {
        ResourceBooking booking = new ResourceBooking(UUID.randomUUID(), activity, role,
                List.of(resource), "{}");
        booking.setResource(resource);
        booking.setStart(activity.getStart());
        activity.getBookings().add(booking);
        bookings.add(booking);
        return booking;
    }

    public ConstraintConfig config(String type, String scopeJson, Object params, int weight, Level level) {
        ConstraintConfig config = new ConstraintConfig(UUID.randomUUID(), type,
                CriterionParser.parse(SchoolFixture.json(scopeJson)), params, weight, true, level);
        configs.add(config);
        return config;
    }

    public Schedule build() {
        Schedule schedule = new Schedule();
        schedule.setTimeslots(horizon.slots());
        schedule.setResources(List.copyOf(resources));
        schedule.setActivities(List.copyOf(activities));
        schedule.setBookings(List.copyOf(bookings));
        schedule.setConfigs(List.copyOf(configs));
        schedule.setHorizon(horizon);
        return schedule;
    }
}
