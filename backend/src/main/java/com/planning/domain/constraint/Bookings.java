package com.planning.domain.constraint;

import com.planning.domain.model.ResourceBooking;
import com.planning.domain.model.PeriodBucket;
import com.planning.domain.time.TemporalBuckets;
import com.planning.domain.time.CycleBuckets;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Les calculs de séquence sur la journée d'une ressource.
 *
 * <p>PIÈGE §6.7 : ces fonctions raisonnent sur les <strong>heures réelles</strong>
 * et jamais sur {@code ordinal + 1}. Deux ordinaux successifs peuvent être
 * séparés par une récréation ; la version naïve produit des résultats faux
 * uniquement autour des pauses — un bug rare, plausible et pénible à reproduire.
 *
 * <p>Elles sont appelées par les deux traversées de chaque plugin : c'est ici que
 * vit la règle, pas dans le pipeline.
 */
public final class Bookings {

    public static final Comparator<ResourceBooking> BY_START =
            Comparator.comparingInt(ResourceBooking::startIndex);

    private Bookings() {
    }

    public static List<ResourceBooking> sorted(List<ResourceBooking> bookings) {
        List<ResourceBooking> copy = new ArrayList<>(bookings);
        copy.sort(BY_START);
        return copy;
    }

    /**
     * Minutes de trou dans une journée, au-delà de la tolérance. Les intervalles
     * inférieurs ou égaux à {@code toleranceMinutes} — typiquement la récréation —
     * ne comptent pas.
     */
    public static int gapMinutes(List<ResourceBooking> sameDay, int toleranceMinutes) {
        List<ResourceBooking> sorted = sorted(sameDay);
        int total = 0;
        LocalDateTime cursor = null;
        for (ResourceBooking booking : sorted) {
            if (cursor != null) {
                int gap = minutesBetween(cursor, booking.startDateTime());
                if (gap > toleranceMinutes) {
                    total += gap;
                }
            }
            LocalDateTime end = booking.endDateTime();
            cursor = cursor == null || end.isAfter(cursor) ? end : cursor;
        }
        return total;
    }

    /** Amplitude de la journée : de la première minute travaillée à la dernière. */
    public static int spanMinutes(List<ResourceBooking> sameDay) {
        if (sameDay.isEmpty()) {
            return 0;
        }
        LocalDateTime first = sameDay.stream().map(ResourceBooking::startDateTime).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime last = sameDay.stream().map(ResourceBooking::endDateTime).max(LocalDateTime::compareTo).orElseThrow();
        return minutesBetween(first, last);
    }

    /**
     * La plus longue suite de séances enchaînées sans interruption réelle. Un
     * intervalle inférieur ou égal à la tolérance ne rompt pas la série.
     */
    public static int longestRunMinutes(List<ResourceBooking> sameDay, int toleranceMinutes) {
        List<ResourceBooking> sorted = sorted(sameDay);
        int longest = 0;
        int current = 0;
        LocalDateTime cursor = null;
        for (ResourceBooking booking : sorted) {
            if (cursor == null || minutesBetween(cursor, booking.startDateTime()) > toleranceMinutes) {
                current = booking.durationMinutes();
            } else {
                current += booking.durationMinutes();
            }
            longest = Math.max(longest, current);
            LocalDateTime end = booking.endDateTime();
            cursor = cursor == null || end.isAfter(cursor) ? end : cursor;
        }
        return longest;
    }

    /** Créneaux de chevauchement entre deux affectations d'une même ressource. */
    public static int overlapSlots(ResourceBooking a, ResourceBooking b) {
        return Math.min(a.endIndex(), b.endIndex()) - Math.max(a.startIndex(), b.startIndex());
    }

    public static int minutesBetween(LocalTime from, LocalTime to) {
        return (int) Duration.between(from, to).toMinutes();
    }

    public static int minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Math.toIntExact(Duration.between(from, to).toMinutes());
    }

    /** Minutes travaillées dans une période réelle ; les pauses sont exclues. */
    public static int minutesIn(ResourceBooking booking, PeriodBucket bucket) {
        if (!booking.isPlaced()) return 0;
        if (booking.getStart().date() == null) {
            return CycleBuckets.bucketOf(booking.getStart(), bucket.period()) == bucket.index()
                    ? booking.durationMinutes() : 0;
        }
        int minutes = 0;
        var slots = booking.getActivity().getTimeSlots();
        for (int index = booking.startIndex(); index < booking.endIndex(); index++) {
            var slot = slots.get(index);
            for (var date : TemporalBuckets.occupiedDates(slot.startDateTime(), slot.endDateTime())) {
                if (TemporalBuckets.bucketOf(date, bucket.period()) != bucket.index()) continue;
                LocalDateTime from = slot.startDateTime().isAfter(date.atStartOfDay())
                        ? slot.startDateTime() : date.atStartOfDay();
                LocalDateTime to = slot.endDateTime().isBefore(date.plusDays(1).atStartOfDay())
                        ? slot.endDateTime() : date.plusDays(1).atStartOfDay();
                minutes = Math.addExact(minutes, minutesBetween(from, to));
            }
        }
        return minutes;
    }

    /** « 5 h 30 » plutôt que « 330 min » : le message est destiné à un humain. */
    public static String hoursMinutes(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : "%d h %02d".formatted(hours, rest);
    }
}
