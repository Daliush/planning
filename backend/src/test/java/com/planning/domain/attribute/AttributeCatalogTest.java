package com.planning.domain.attribute;

import com.planning.domain.constraint.ConstraintPlugin;
import com.planning.domain.constraint.ConstraintRegistry;
import com.planning.domain.model.BusinessProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le garde du vocabulaire métier.
 *
 * <p>Un plugin qui lit une clé absente du catalogue lit une clé que l'interface
 * ne sait pas faire saisir. L'attribut restera vide sur toutes les ressources, la
 * règle ne se déclenchera jamais, et <strong>rien ne le signalera</strong> — ni
 * erreur au chargement, ni violation au rapport, ni score anormal. C'est
 * exactement le mode de défaillance silencieux que le §15 traque.
 */
class AttributeCatalogTest {

    @Test
    @DisplayName("tout attribut lu par un plugin est déclaré au catalogue")
    void toutAttributLuEstDeclare() {
        Set<String> connus = AttributeCatalog.keys();

        List<String> orphelins = ConstraintRegistry.standard().all().stream()
                .flatMap(plugin -> plugin.readsAttributes().stream()
                        .filter(key -> !connus.contains(key))
                        .map(key -> "%s lit « %s »".formatted(plugin.type(), key)))
                .toList();

        assertThat(orphelins)
                .describedAs("""
                        Ces plugins lisent un attribut que le catalogue ne déclare pas. \
                        L'interface ne proposera aucun champ pour le saisir, la règle \
                        ne se déclenchera jamais, et aucune erreur ne le dira. \
                        Ajouter la définition dans AttributeCatalog.""")
                .isEmpty();
    }

    @Test
    @DisplayName("chaque attribut est proposé dans au moins une verticale")
    void chaqueAttributAppartientAUneVerticale() {
        List<String> orphelins = AttributeCatalog.all().stream()
                .filter(definition -> definition.profiles().isEmpty())
                .map(AttributeDefinition::key)
                .toList();

        assertThat(orphelins)
                .describedAs("Un attribut sans profil n'apparaîtrait dans aucun formulaire.")
                .isEmpty();
    }

    @Test
    @DisplayName("les clés du catalogue sont uniques")
    void lesClesSontUniques() {
        List<String> doublons = AttributeCatalog.all().stream()
                .collect(Collectors.groupingBy(AttributeDefinition::key, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(java.util.Map.Entry::getKey)
                .toList();

        assertThat(doublons).isEmpty();
    }

    @Test
    @DisplayName("un attribut ENUM déclare ses options, les autres non")
    void lesOptionsAccompagnentLesEnums() {
        AttributeCatalog.all().forEach(definition -> {
            if (definition.kind() == AttributeKind.ENUM) {
                assertThat(definition.options())
                        .describedAs("« %s » est un ENUM sans options : la liste déroulante serait vide.",
                                definition.key())
                        .isNotEmpty();
            } else {
                assertThat(definition.options())
                        .describedAs("« %s » n'est pas un ENUM mais déclare des options, qui seraient ignorées.",
                                definition.key())
                        .isEmpty();
            }
        });
    }

    @Test
    @DisplayName("un scénario sans profil voit tout le vocabulaire")
    void sansProfilToutEstPropose() {
        assertThat(AttributeCatalog.forProfile(null))
                .hasSameSizeAs(AttributeCatalog.all());
    }

    @Test
    @DisplayName("le profil filtre le vocabulaire sans jamais le vider")
    void chaqueProfilAUnVocabulaire() {
        for (BusinessProfile profile : BusinessProfile.values()) {
            assertThat(AttributeCatalog.forProfile(profile))
                    .describedAs("Le profil %s ne propose aucun attribut.", profile)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("le code est proposé partout : c'est la clé d'accroche des scopes")
    void leCodeEstUniversel() {
        for (BusinessProfile profile : BusinessProfile.values()) {
            assertThat(AttributeCatalog.forProfile(profile))
                    .extracting(AttributeDefinition::key)
                    .describedAs("Sans « code », aucune règle ne peut viser un individu précis en %s.", profile)
                    .contains("code");
        }
    }

    /** La quasi-totalité des plugins passe par les scopes, qui sont des données. */
    @Test
    @DisplayName("aucun plugin ne lit d'attribut en dur aujourd'hui")
    void aucuneLectureDirecteInattendue() {
        List<String> lecteurs = ConstraintRegistry.standard().all().stream()
                .filter(plugin -> !plugin.readsAttributes().isEmpty())
                .map(ConstraintPlugin::type)
                .toList();

        assertThat(lecteurs)
                .describedAs("""
                        Un plugin s'est mis à lire un attribut directement. Ce n'est pas \
                        interdit, mais c'est un choix de conception : vérifier qu'il ne \
                        s'agit pas d'un scope déguisé, puis mettre ce test à jour.""")
                .isEmpty();
    }
}
