package com.planning.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.domain.builder.AvailabilityCompiler;
import com.planning.domain.builder.HorizonFactory;
import com.planning.domain.builder.ProblemBuilder;
import com.planning.domain.builder.ProblemValidator;
import com.planning.domain.builder.StartsCompiler;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.constraint.ParamsCodec;
import com.planning.domain.model.Effect;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import com.planning.domain.placement.PlacementRegistry;
import com.planning.domain.port.Rows.AvailabilityRow;
import com.planning.domain.port.Rows.ConstraintConfigRow;
import com.planning.domain.port.Rows.ConstraintWeightRow;
import com.planning.domain.port.Rows.DemandRow;
import com.planning.domain.port.Rows.RequirementRow;
import com.planning.domain.port.Rows.ResourceRow;
import com.planning.domain.port.Rows.SlotRow;
import com.planning.domain.port.Rows.TimeModelRow;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un collège minuscule mais représentatif : grille irrégulière, un vacataire aux
 * disponibilités étroites, deux quotas concurrents sur la même ressource, et des
 * critères d'éligibilité qui mordent.
 *
 * <p>Assez petit pour se résoudre en quelques secondes, assez tendu pour produire
 * des violations de plusieurs plugins — ce qu'il faut pour que le test de
 * cohérence du §16.5 vérifie réellement quelque chose.
 */
public final class SchoolFixture {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static final UUID SCENARIO = UUID.randomUUID();
    public static final UUID TIME_MODEL = UUID.randomUUID();

    public static final UUID T_ENSEIGNANT = UUID.randomUUID();
    public static final UUID T_SALLE = UUID.randomUUID();
    public static final UUID T_GROUPE = UUID.randomUUID();

    public static final UUID PROF_TITULAIRE = UUID.randomUUID();
    public static final UUID PROF_VACATAIRE = UUID.randomUUID();
    public static final UUID GRANDE_SALLE = UUID.randomUUID();
    public static final UUID PETITE_SALLE = UUID.randomUUID();
    public static final UUID GROUPE_A = UUID.randomUUID();
    public static final UUID GROUPE_B = UUID.randomUUID();

    private SchoolFixture() {
    }

    public static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON de test invalide : " + raw, e);
        }
    }

    /** Trois jours, quatre créneaux d'une heure, une récréation de 15 min. */
    public static List<SlotRow> grid() {
        List<SlotRow> slots = new ArrayList<>();
        LocalTime[][] hours = {
                {LocalTime.of(8, 0), LocalTime.of(9, 0)},
                {LocalTime.of(9, 0), LocalTime.of(10, 0)},
                {LocalTime.of(10, 15), LocalTime.of(11, 15)},
                {LocalTime.of(11, 15), LocalTime.of(12, 15)}};
        for (int day = 0; day < 3; day++) {
            for (int ordinal = 0; ordinal < hours.length; ordinal++) {
                slots.add(new SlotRow(day, ordinal, hours[ordinal][0], hours[ordinal][1]));
            }
        }
        return slots;
    }

    public static TimeModelRow timeModel() {
        return new TimeModelRow(TIME_MODEL, "Semaine de test", HorizonKind.CYCLIC, 7, null, null, 20);
    }

    public static List<ResourceRow> resources() {
        return List.of(
                new ResourceRow(PROF_TITULAIRE, T_ENSEIGNANT, "enseignant", "Prof Titulaire",
                        Effect.AVAILABLE, json("""
                        {"code":"PT","matieres":["maths"],"statut":"titulaire"}""")),
                new ResourceRow(PROF_VACATAIRE, T_ENSEIGNANT, "enseignant", "Prof Vacataire",
                        Effect.UNAVAILABLE, json("""
                        {"code":"PV","matieres":["maths"],"statut":"vacataire"}""")),
                new ResourceRow(GRANDE_SALLE, T_SALLE, "salle", "Grande salle",
                        Effect.AVAILABLE, json("""
                        {"code":"GS","capacite":30,"usages":["cours"]}""")),
                new ResourceRow(PETITE_SALLE, T_SALLE, "salle", "Petite salle",
                        Effect.AVAILABLE, json("""
                        {"code":"PS","capacite":20,"usages":["cours"]}""")),
                new ResourceRow(GROUPE_A, T_GROUPE, "groupe", "6e A",
                        Effect.AVAILABLE, json("""
                        {"code":"6A","effectif":25}""")),
                new ResourceRow(GROUPE_B, T_GROUPE, "groupe", "6e B",
                        Effect.AVAILABLE, json("""
                        {"code":"6B","effectif":18}""")));
    }

    public static List<AvailabilityRow> availability() {
        return List.of(
                // Le vacataire n'est là que le mardi : « indisponible sauf ».
                new AvailabilityRow(PROF_VACATAIRE, Effect.AVAILABLE, 1, null, null, 0),
                // La petite salle est déconseillée en fin de matinée : permis, pénalisé.
                new AvailabilityRow(PETITE_SALLE, Effect.DISCOURAGED, 2, LocalTime.of(11, 0), null, 0));
    }

    public static List<DemandRow> demands() {
        return List.of(
                demand("Maths — 6e A", "6A", 25),
                demand("Maths — 6e B", "6B", 18));
    }

    private static DemandRow demand(String label, String groupe, int effectif) {
        UUID id = UUID.randomUUID();
        return new DemandRow(id, label, 60, 3,
                json("""
                        {"matiere":"maths","groupe":"%s","effectif":%d}""".formatted(groupe, effectif)),
                json("""
                        {"strategy":"free"}"""),
                List.of(
                        new RequirementRow(UUID.randomUUID(), "enseignant", T_ENSEIGNANT, 1,
                                json("""
                                        {"contains":{"path":"/matieres","value":"maths"}}""")),
                        new RequirementRow(UUID.randomUUID(), "salle", T_SALLE, 1,
                                json("""
                                        {"gte":{"path":"/capacite","value":%d}}""".formatted(effectif))),
                        new RequirementRow(UUID.randomUUID(), "groupe", T_GROUPE, 1,
                                json("""
                                        {"eq":{"path":"/code","value":"%s"}}""".formatted(groupe)))));
    }

    /** Un jeu de règles couvrant les dix plugins configurables. */
    public static List<ConstraintConfigRow> configs() {
        List<ConstraintConfigRow> configs = new ArrayList<>();
        configs.add(config("load.max",
                """
                        {"eq":{"path":"/type","value":"enseignant"}}""",
                """
                        {"maxMinutes":120,"period":"DAY"}""", 1));
        // Deuxième quota sur la même ressource : c'est ce cas qui attrape les
        // erreurs de clé d'agrégation, deux règles mélangeant leurs totaux.
        configs.add(config("load.max",
                """
                        {"eq":{"path":"/statut","value":"vacataire"}}""",
                """
                        {"maxMinutes":60,"period":"DAY"}""", 3));
        configs.add(config("load.min",
                """
                        {"eq":{"path":"/statut","value":"titulaire"}}""",
                """
                        {"minMinutes":60,"period":"CYCLE"}""", 1));
        configs.add(config("load.balance",
                """
                        {"contains":{"path":"/matieres","value":"maths"}}""", "{}", 1));
        configs.add(config("resource.consistency",
                """
                        {"eq":{"path":"/type","value":"enseignant"}}""", "{}", 1));
        configs.add(config("demand.spread", "{}",
                """
                        {"maxPerDay":1}""", 1));
        configs.add(config("gaps.avoid",
                """
                        {"eq":{"path":"/type","value":"groupe"}}""",
                """
                        {"toleranceMinutes":20}""", 4));
        configs.add(config("gaps.avoid",
                """
                        {"eq":{"path":"/type","value":"enseignant"}}""",
                """
                        {"toleranceMinutes":20}""", 1));
        configs.add(config("day.span_max",
                """
                        {"eq":{"path":"/type","value":"enseignant"}}""",
                """
                        {"maxSpanMinutes":180}""", 1));
        configs.add(config("sequence.consecutive_max",
                """
                        {"eq":{"path":"/type","value":"enseignant"}}""",
                """
                        {"maxConsecutiveMinutes":60,"toleranceMinutes":20}""", 1));
        configs.add(config("time.preferred_window",
                """
                        {"eq":{"path":"/matiere","value":"maths"}}""",
                """
                        {"from":"08:00","to":"10:00"}""", 2));
        return configs;
    }

    private static ConstraintConfigRow config(String type, String scope, String params, int weight) {
        return new ConstraintConfigRow(UUID.randomUUID(), type, json(scope), json(params), weight, true);
    }

    public static List<ConstraintWeightRow> weights() {
        return List.of(
                new ConstraintWeightRow("resource.conflict", Level.HARD, 1),
                new ConstraintWeightRow("resource.availability", Level.HARD, 1),
                new ConstraintWeightRow("resource.consistency", Level.HARD, 1),
                new ConstraintWeightRow("booking.unassigned", Level.HARD, 1),
                new ConstraintWeightRow("load.max", Level.HARD, 1),
                new ConstraintWeightRow("demand.spread", Level.SOFT, 200),
                new ConstraintWeightRow("gaps.avoid", Level.SOFT, 50),
                new ConstraintWeightRow("resource.discouraged_slot", Level.SOFT, 30),
                new ConstraintWeightRow("time.preferred_window", Level.SOFT, 20),
                new ConstraintWeightRow("load.min", Level.SOFT, 20),
                new ConstraintWeightRow("load.balance", Level.SOFT, 10),
                new ConstraintWeightRow("day.span_max", Level.SOFT, 10),
                new ConstraintWeightRow("sequence.consecutive_max", Level.SOFT, 10));
    }

    public static FakeProblemSource source() {
        return new FakeProblemSource(timeModel(), grid(), resources(), availability(),
                demands(), configs(), weights());
    }

    public static ProblemBuilder builder() {
        return builder(source());
    }

    public static ProblemBuilder builder(FakeProblemSource source) {
        return new ProblemBuilder(source, MAPPER, ConstraintRegistry.standard(),
                new ParamsCodec(MAPPER), PlacementRegistry.standard(), new HorizonFactory(),
                new AvailabilityCompiler(), new StartsCompiler(), new ProblemValidator());
    }
}
