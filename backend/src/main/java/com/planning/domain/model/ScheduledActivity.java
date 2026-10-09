package com.planning.domain.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * Une séance qui cherche son créneau. <strong>Une seule variable : le moment.</strong>
 *
 * <p>Le vivier {@code validStarts} est calculé une fois par le builder. Timefold
 * appelle {@link #getValidStarts()} au démarrage du solveur et n'y revient plus :
 * le getter doit rester un simple accesseur sur un champ (§9.4).
 */
@PlanningEntity
public class ScheduledActivity {

    @PlanningId
    private UUID id;

    private UUID demandId;
    private String demandLabel;
    private int occurrence;
    private int durationMinutes;
    private int durationSlots;
    private JsonNode attributes;

    private List<Timeslot> validStarts;
    private List<Timeslot> timeSlots;

    /**
     * Lien fixe vers les postes de cette séance, alimenté par le builder. Sert
     * uniquement à {@link BookingStartListener} pour propager le créneau.
     */
    private List<ResourceBooking> bookings = new ArrayList<>();

    @PlanningVariable(valueRangeProviderRefs = "startSlots")
    private Timeslot start;

    @PlanningPin
    private boolean pinned;

    /** Requis par Timefold. */
    public ScheduledActivity() {
    }

    public ScheduledActivity(UUID id, UUID demandId, String demandLabel, int occurrence,
                             int durationMinutes, int durationSlots, JsonNode attributes,
                             List<Timeslot> validStarts) {
        this.id = id;
        this.demandId = demandId;
        this.demandLabel = demandLabel;
        this.occurrence = occurrence;
        this.durationMinutes = durationMinutes;
        this.durationSlots = durationSlots;
        this.attributes = attributes;
        this.validStarts = validStarts;
        this.timeSlots = validStarts;
    }

    @ValueRangeProvider(id = "startSlots")
    public List<Timeslot> getValidStarts() {
        return validStarts;
    }

    public int startIndex() {
        return start.index();
    }

    public int endIndex() {
        return start.index() + durationSlots;
    }

    public int dayInCycle() {
        return start.dayInCycle();
    }

    public void setTimeSlots(List<Timeslot> slots) { this.timeSlots = slots; }
    public List<Timeslot> getTimeSlots() { return timeSlots; }

    public LocalDateTime endDateTime(Timeslot from) {
        int lastIndex = from.index() + durationSlots - 1;
        if (timeSlots != null && lastIndex >= 0 && lastIndex < timeSlots.size()
                && timeSlots.get(lastIndex).index() == lastIndex) {
            return timeSlots.get(lastIndex).endDateTime();
        }
        throw new IllegalStateException("La grille complète est requise pour calculer la fin de « " + demandLabel + " ».");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDemandId() {
        return demandId;
    }

    public String getDemandLabel() {
        return demandLabel;
    }

    public int getOccurrence() {
        return occurrence;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getDurationSlots() {
        return durationSlots;
    }

    public JsonNode getAttributes() {
        return attributes;
    }

    public List<ResourceBooking> getBookings() {
        return bookings;
    }

    public void setBookings(List<ResourceBooking> bookings) {
        this.bookings = bookings;
    }

    public Timeslot getStart() {
        return start;
    }

    public void setStart(Timeslot start) {
        this.start = start;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return "%s #%d".formatted(demandLabel, occurrence + 1);
    }
}
