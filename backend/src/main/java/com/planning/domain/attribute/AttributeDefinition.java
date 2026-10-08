package com.planning.domain.attribute;

import com.planning.domain.model.BusinessProfile;

import java.util.List;
import java.util.Set;

/**
 * Un attribut métier connu du produit — une clé de {@code resource.attributes}
 * que les scopes et les critères d'éligibilité savent interroger.
 *
 * <p>C'est du <strong>vocabulaire</strong>, donc du code : « le code fournit un
 * langage, la base contient les phrases » (§2.1). {@code matieres} est un mot du
 * langage ; {@code ["maths"]} sur Jean Dupont est une phrase, et elle vit en base.
 *
 * <p>Cette déclaration ne restreint rien. {@code resource.attributes} reste un
 * jsonb libre : un tenant peut y poser des clés que le catalogue ignore, et le
 * moteur de critères les lira sans broncher. Le catalogue sert à <em>proposer</em>
 * un formulaire plutôt qu'un éditeur JSON nu.
 *
 * @param key           la clé dans {@code attributes}, et donc le {@code path} des scopes
 * @param title         le libellé montré à un utilisateur métier
 * @param description   ce que l'attribut veut dire, en une phrase
 * @param kind          le widget à proposer et le type attendu
 * @param options       les valeurs admises — seulement pour {@link AttributeKind#ENUM}
 * @param minimum       borne basse des valeurs numériques, {@code null} si sans objet
 * @param profiles      les verticales où l'attribut est proposé
 * @param resourceTypes les codes de type concernés ; vide = tous les types du profil
 */
public record AttributeDefinition(
        String key,
        String title,
        String description,
        AttributeKind kind,
        List<String> options,
        Integer minimum,
        Set<BusinessProfile> profiles,
        Set<String> resourceTypes) {

    public AttributeDefinition {
        options = List.copyOf(options);
        profiles = Set.copyOf(profiles);
        resourceTypes = Set.copyOf(resourceTypes);
    }

    /** Un attribut sans contrainte de type de ressource, proposé partout dans ses profils. */
    public static AttributeDefinition universal(String key, String title, String description,
                                                AttributeKind kind, Set<BusinessProfile> profiles) {
        return new AttributeDefinition(key, title, description, kind,
                List.of(), null, profiles, Set.of());
    }

    public boolean appliesTo(BusinessProfile profile, String resourceTypeCode) {
        // Un scénario sans profil ne filtre rien : c'est le mode expert, qui
        // assume de voir le vocabulaire des autres verticales.
        boolean profileMatches = profile == null || profiles.contains(profile);
        boolean typeMatches = resourceTypes.isEmpty() || resourceTypes.contains(resourceTypeCode);
        return profileMatches && typeMatches;
    }
}
