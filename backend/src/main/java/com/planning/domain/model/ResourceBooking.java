package com.planning.domain.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un poste à pourvoir. <strong>Une seule variable génuine : la ressource.</strong>
 *
 * <p>{@code ResourceBooking} transforme un nombre variable de champs en un nombre
 * variable d'objets (§7.6) : un TP dédoublé a deux enseignants et deux salles là
 * où un cours magistral en a un de chaque, sans qu'aucune classe Java ne change.
 *
 * <p>{@code allowsUnassigned} permet un planning partiel — « 5 postes sur 6 » —
 * plutôt qu'un échec d'initialisation. Le plugin {@code booking.unassigned}
 * pénalise les postes vides.
 *
 * <p><strong>Pourquoi {@code start} est une variable ombre.</strong> Le créneau est
 * décidé par la {@link ScheduledActivity}, une autre entité. Si les contraintes
 * lisaient {@code activity.getStart()} par simple déréférencement, Timefold
 * ignorerait que déplacer l'activité invalide les tuples des bookings : le score
 * incrémental deviendrait faux, silencieusement. La variable ombre déclare la
 * dépendance et la rend observable par le réseau de propagation.
 */
@PlanningEntity
public class ResourceBooking {

    @PlanningId
    private UUID id;

    /** Lien FIXE vers l'activité, jamais une variable de planification. */
    private ScheduledActivity activity;
    private String role;

    /**
     * Description lisible des critères d'éligibilité, partagée par tous les
     * bookings d'un même requirement. Sert uniquement aux messages d'erreur du
     * validateur — jamais lue en boucle chaude.
     */
    private String criteriaSummary;

    private List<Resource> eligible;

    @PlanningVariable(valueRangeProviderRefs = "eligible", allowsUnassigned = true)
    private Resource resource;

    @ShadowVariable(variableListenerClass = BookingStartListener.class,
            sourceEntityClass = ScheduledActivity.class, sourceVariableName = "start")
    private Timeslot start;

    @PlanningPin
    private boolean pinned;

    /** Requis par Timefold. */
    public ResourceBooking() {
    }

    public ResourceBooking(UUID id, ScheduledActivity activity, String role,
                           List<Resource> eligible, String criteriaSummary) {
        this.id = id;
        this.activity = activity;
        this.role = role;
        this.eligible = eligible;
        this.criteriaSummary = criteriaSummary;
    }

    @ValueRangeProvider(id = "eligible")
    public List<Resource> getEligible() {
        return eligible;
    }

    /**
     * Un poste non encore placé porte l'intervalle vide {@code [-1, -1)}.
     *
     * <p>Ce n'est pas une commodité : les jointures de chevauchement sont évaluées
     * avant tout {@code filter}, donc sur des bookings potentiellement non
     * initialisés pendant la phase de construction. L'intervalle vide ne
     * chevauche rien, ce qui rend la jointure sûre sans garde supplémentaire en
     * boucle chaude.
     */
    public int startIndex() {
        return start == null ? -1 : start.index();
    }

    public int endIndex() {
        return start == null ? -1 : start.index() + activity.getDurationSlots();
    }

    public int durationMinutes() {
        return activity.getDurationMinutes();
    }

    public int durationInSlots() {
        return activity.getDurationSlots();
    }

    public int dayInCycle() {
        return start.dayInCycle();
    }

    public LocalTime startTime() {
        return start.start();
    }

    /**
     * Heure de fin du dernier créneau occupé, pauses et passage à minuit inclus.
     */
    public LocalTime endTime() {
        return endDateTime().toLocalTime();
    }

    public LocalDateTime startDateTime() { return start.startDateTime(); }
    public LocalDateTime endDateTime() { return activity.endDateTime(start); }

    public UUID getId() {
        return id;
    }

    public ScheduledActivity getActivity() {
        return activity;
    }

    public String getRole() {
        return role;
    }

    public String getCriteriaSummary() {
        return criteriaSummary;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
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

    /** Le poste est-il exploitable par une contrainte : affecté et placé ? */
    public boolean isPlaced() {
        return resource != null && start != null;
    }

    @Override
    public String toString() {
        return "%s / %s".formatted(activity, role);
    }
}
