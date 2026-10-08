package com.planning.domain.constraint;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.model.ConstraintConfig;
import com.planning.domain.model.HorizonKind;
import com.planning.domain.model.Level;
import com.planning.domain.model.Schedule;
import com.planning.domain.model.Violation;
import com.planning.domain.time.TimeHorizon;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Une forme de raisonnement. La spécificité vient de la donnée, jamais de la
 * contrainte (§2.6) : il n'existe pas de plugin « Jean n'est pas là le lundi »,
 * il existe {@code resource.availability} qui s'applique aux 500 ressources et un
 * {@code BitSet} propre à Jean qui la fait se déclencher pour lui.
 *
 * <p><strong>Une seule interface, un seul registre.</strong> Ce qui varie d'un
 * plugin à l'autre est uniquement <em>où il lit ses paramètres</em> — attributs de
 * ressource, lignes de {@code constraint_config}, ou les deux. C'est un détail
 * d'implémentation interne à chaque classe, pas une taxonomie.
 *
 * <p>ADR-003 — chaque plugin expose <strong>deux</strong> traversées de la même
 * règle : {@link #build} pour le solveur, incrémentale, et {@link #validate} pour
 * l'API, naïve et explicative. La logique métier — seuil, clé d'agrégation,
 * formule de pénalité — vit dans des méthodes statiques partagées et n'est
 * <strong>jamais</strong> recopiée : c'est la source de dérive numéro un, et elle
 * rendrait le rapport utilisateur faux sans qu'aucun signal ne l'indique.
 */
public interface ConstraintPlugin {

    /**
     * Identifiant stable. C'est un CONTRAT, pas un libellé : renommer
     * {@code load.max} laisserait toutes les lignes de {@code constraint_config}
     * et de {@code constraint_weight} pointer dans le vide, silencieusement.
     */
    String type();

    /** Libellé affiché dans l'interface d'administration. */
    String label();

    /** Ce que la règle exprime, en vocabulaire métier. */
    String description();

    /**
     * Niveau par défaut, utilisé si {@code constraint_weight} ne dit rien. Ce
     * n'est pas un poids : c'est une unité et une valeur de repli, exactement
     * comme un défaut dans un fichier de configuration (§13.2).
     */
    Level defaultLevel();

    /** Le record de paramètres, pour la désérialisation typée. */
    Class<?> paramsType();

    /** JSON Schema exposé à l'API pour valider la saisie. */
    JsonNode paramSchema();

    /**
     * Horizons supportés. Filtre grossier pour n'afficher que ce qui est
     * proposable dans l'interface d'administration ; le contrôle fin se fait au
     * chargement via {@link #validate(ConstraintConfig, TimeHorizon)}.
     */
    default Set<HorizonKind> horizons() {
        return EnumSet.allOf(HorizonKind.class);
    }

    /**
     * Un plugin structurel ne se configure pas : il s'applique toujours et à tout
     * le monde. Le builder lui fabrique malgré tout une ligne de configuration
     * synthétique, pour que la traversée d'explicabilité soit uniforme.
     */
    default boolean configDriven() {
        return true;
    }

    /**
     * Contre quoi le {@code scope} est évalué. La plupart des règles parlent des
     * ressources ; celles qui partent de {@code Streams.scopedActivities} parlent
     * de la demande et doivent le déclarer, sans quoi l'interface proposerait les
     * mauvais attributs et le scope ne matcherait rien silencieusement.
     */
    default ScopeTarget scopeTarget() {
        return ScopeTarget.RESOURCE;
    }

    /**
     * Les clés de {@code attributes} que ce plugin lit <strong>directement</strong>,
     * hors scope et hors critères d'éligibilité.
     *
     * <p>La quasi-totalité des plugins n'en lit aucune : ils reçoivent leurs
     * seuils par {@code params} et leur cible par {@code scope}, tous deux des
     * données. N'inscrire ici que ce que le code du plugin déréférence lui-même —
     * typiquement un quota porté par la ressource plutôt que par la règle.
     *
     * <p>Cette déclaration est <strong>la seule</strong> : le lien inverse
     * — « qui lit {@code quota_hebdo_minutes} ? » — est recalculé par
     * {@code CatalogService} en parcourant le registre, jamais saisi à la main.
     * Deux déclarations divergeraient, et le catalogue mentirait en silence.
     *
     * <p>{@code AttributeCatalogTest} refuse toute clé absente du
     * {@link com.planning.domain.attribute.AttributeCatalog} : un plugin qui lit
     * un attribut que l'interface ne sait pas saisir ne se déclencherait jamais,
     * sans qu'aucune erreur ne le signale.
     */
    default Set<String> readsAttributes() {
        return Set.of();
    }

    /** Validation fine d'une ligne de configuration, au chargement. */
    default void validate(ConstraintConfig config, TimeHorizon horizon) {
        // par défaut, rien à contrôler
    }

    /** Traversée A — Constraint Streams, calcul incrémental, pour le solveur. */
    Constraint build(ConstraintFactory factory);

    /**
     * Les contraintes Timefold réellement produites par ce plugin.
     *
     * <p>La quasi-totalité des plugins en produit une seule, et n'a rien à
     * redéfinir. L'exception concerne les règles qui doivent aussi se déclencher
     * sur une <strong>absence</strong> : les Constraint Streams n'offrant pas de
     * jointure externe, détecter « cette ressource n'a aucune affectation » exige
     * un second pipeline, qui part des ressources plutôt que des affectations.
     *
     * <p>Ces pipelines auxiliaires restent le <em>même plugin</em> : ils partagent
     * son poids ①, et leurs violations sont rapportées sous son identifiant. La
     * seule chose qui se dédouble est la traversée, jamais la règle.
     */
    default List<Constraint> buildAll(ConstraintFactory factory) {
        return List.of(build(factory));
    }

    /**
     * Les noms des contraintes produites par {@link #buildAll}, dont
     * {@link #type()}. Le livre de poids s'en sert pour appliquer le même poids ①
     * à tous les pipelines d'un même plugin.
     */
    default List<String> constraintNames() {
        return List.of(type());
    }

    /**
     * Traversée B — Java simple sur une solution figée, pour l'API.
     *
     * <p>C'est la <strong>seule source d'explicabilité du produit</strong> :
     * l'édition Community ne garantit aucune API d'analyse. Le coût est
     * négligeable — une passe en O(nombre de bookings) sur la solution finale,
     * jamais dans la boucle chaude.
     */
    List<Violation> validate(Schedule solution, ConstraintConfig config);
}
