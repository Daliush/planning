package com.planning.domain.attribute;

import com.planning.domain.model.BusinessProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.planning.domain.attribute.AttributeKind.DURATION_MINUTES;
import static com.planning.domain.attribute.AttributeKind.ENUM;
import static com.planning.domain.attribute.AttributeKind.INTEGER;
import static com.planning.domain.attribute.AttributeKind.TEXT;
import static com.planning.domain.attribute.AttributeKind.TEXT_LIST;
import static com.planning.domain.model.BusinessProfile.COMPANY;
import static com.planning.domain.model.BusinessProfile.RESTAURANT;
import static com.planning.domain.model.BusinessProfile.SCHOOL;

/**
 * Le vocabulaire métier du produit — toutes les clés de {@code attributes} que
 * l'interface sait proposer sous forme de champ plutôt que de JSON brut.
 *
 * <p><strong>Pourquoi dans le code et pas en base.</strong> Ajouter un attribut,
 * c'est enrichir le langage que parlent les scopes ; le mettre en base
 * demanderait une migration pour chaque mot nouveau, et surtout laisserait
 * diverger ce qu'un plugin lit de ce que le catalogue déclare. Ici, la
 * déclaration est relue en revue de code, au même endroit que le plugin qui
 * l'exploite, et {@code AttributeCatalogTest} refuse toute lecture non déclarée.
 *
 * <p><strong>Ce catalogue ne restreint rien.</strong> {@code resource.attributes}
 * reste un jsonb libre : l'éditeur avancé permet d'y poser n'importe quelle clé,
 * et le moteur de critères la lira. Le catalogue rend le cas courant saisissable
 * sans connaître JSON, il ne ferme pas le cas rare.
 */
public final class AttributeCatalog {

    private AttributeCatalog() {
    }

    /**
     * Le code est la clé d'accroche des scopes : c'est par lui qu'une règle vise
     * un individu précis. Le libellé affiché n'est pas lisible par le moteur de
     * critères, et un scope adossé à un libellé se déferait au premier renommage.
     */
    private static final AttributeDefinition CODE = AttributeDefinition.universal(
            "code", "Code",
            "Identifiant court et stable, utilisé par les règles pour viser cette ressource.",
            TEXT, Set.of(SCHOOL, COMPANY, RESTAURANT));

    private static final List<AttributeDefinition> ALL = List.of(
            CODE,

            // ─── Scolaire ───────────────────────────────────────────────────
            new AttributeDefinition("matieres", "Matières",
                    "Ce que l'enseignant peut assurer. Un critère d'éligibilité y cherche la matière de la demande.",
                    TEXT_LIST, List.of(), null, Set.of(SCHOOL), Set.of("enseignant")),
            new AttributeDefinition("niveaux", "Niveaux",
                    "Les niveaux de classe que l'enseignant prend en charge.",
                    TEXT_LIST, List.of(), null, Set.of(SCHOOL), Set.of("enseignant")),
            new AttributeDefinition("statut", "Statut",
                    "Titulaire ou vacataire. Sert de scope aux règles qui ne visent qu'une catégorie.",
                    ENUM, List.of("titulaire", "vacataire"), null, Set.of(SCHOOL), Set.of("enseignant")),
            new AttributeDefinition("effectif", "Effectif",
                    "Le nombre d'élèves du groupe, comparé à la capacité des salles.",
                    INTEGER, List.of(), 1, Set.of(SCHOOL), Set.of("groupe")),
            new AttributeDefinition("niveau", "Niveau",
                    "Le niveau de la classe — « 6 », « 5 ».",
                    TEXT, List.of(), null, Set.of(SCHOOL), Set.of("groupe")),
            new AttributeDefinition("batiment", "Bâtiment",
                    "Le bâtiment de la salle, pour les règles qui pénalisent les traversées.",
                    TEXT, List.of(), null, Set.of(SCHOOL), Set.of("salle")),

            // ─── Entreprise et restauration ─────────────────────────────────
            new AttributeDefinition("competences", "Compétences",
                    "Ce que la personne sait faire. Un critère d'éligibilité y cherche la compétence exigée par le poste.",
                    TEXT_LIST, List.of(), null, Set.of(COMPANY, RESTAURANT), Set.of()),
            new AttributeDefinition("habilitations", "Habilitations",
                    "Les autorisations réglementaires détenues — conduite, hygiène, sécurité.",
                    TEXT_LIST, List.of(), null, Set.of(COMPANY, RESTAURANT), Set.of()),
            new AttributeDefinition("contrat", "Type de contrat",
                    "La nature du contrat. Sert de scope aux règles qui ne visent qu'une catégorie.",
                    ENUM, List.of("cdi", "cdd", "interim", "apprenti", "prestataire"), null,
                    Set.of(COMPANY, RESTAURANT), Set.of()),
            new AttributeDefinition("service", "Service",
                    "Le service ou l'équipe de rattachement.",
                    TEXT, List.of(), null, Set.of(COMPANY, RESTAURANT), Set.of()),
            new AttributeDefinition("site", "Site",
                    "Le site géographique, pour les règles qui pénalisent les déplacements.",
                    TEXT, List.of(), null, Set.of(COMPANY, RESTAURANT), Set.of()),

            // ─── Communs à plusieurs verticales ─────────────────────────────
            new AttributeDefinition("quota_hebdo_minutes", "Quota hebdomadaire",
                    "La charge de travail visée sur une semaine. Lue comme seuil par les règles de charge.",
                    DURATION_MINUTES, List.of(), 0, Set.of(SCHOOL, COMPANY, RESTAURANT), Set.of()),
            new AttributeDefinition("capacite", "Capacité",
                    "Le nombre de personnes que le lieu peut accueillir.",
                    INTEGER, List.of(), 1, Set.of(SCHOOL, COMPANY, RESTAURANT), Set.of("salle", "poste", "local")),
            new AttributeDefinition("usages", "Usages",
                    "Ce à quoi le lieu ou le matériel se prête — « cours », « tp », « visio ».",
                    TEXT_LIST, List.of(), null, Set.of(SCHOOL, COMPANY, RESTAURANT),
                    Set.of("salle", "poste", "local", "equipement")));

    private static final Map<String, AttributeDefinition> BY_KEY =
            ALL.stream().collect(Collectors.toUnmodifiableMap(
                    AttributeDefinition::key, Function.identity()));

    public static List<AttributeDefinition> all() {
        return ALL;
    }

    public static Optional<AttributeDefinition> find(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static Set<String> keys() {
        return BY_KEY.keySet();
    }

    /** Le vocabulaire proposé sur cette verticale — tout entier si {@code profile} est nul. */
    public static List<AttributeDefinition> forProfile(BusinessProfile profile) {
        return ALL.stream()
                .filter(definition -> profile == null || definition.profiles().contains(profile))
                .toList();
    }
}
