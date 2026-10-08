package com.planning.domain.criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.planning.domain.exception.InvalidConfigException;
import org.junit.jupiter.api.Test;

import static com.planning.support.SchoolFixture.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriterionTest {

    private static final JsonNode ATTRIBUTES = json("""
            {"type":"enseignant","statut":"vacataire","capacite":24,
             "matieres":["maths","physique"],"adresse":{"batiment":"A"}}""");

    private boolean test(String criterion) {
        return CriterionParser.parse(json(criterion)).test(ATTRIBUTES);
    }

    @Test
    void eqCompareLesScalairesEtLesNombresSansSeSoucierDuFormat() {
        assertThat(test("""
                {"eq":{"path":"/type","value":"enseignant"}}""")).isTrue();
        assertThat(test("""
                {"eq":{"path":"/capacite","value":24.0}}""")).isTrue();
        assertThat(test("""
                {"eq":{"path":"/type","value":"salle"}}""")).isFalse();
    }

    @Test
    void lePointeurJsonNavigueEnProfondeur() {
        assertThat(test("""
                {"eq":{"path":"/adresse/batiment","value":"A"}}""")).isTrue();
        assertThat(test("""
                {"eq":{"path":"/matieres/0","value":"maths"}}""")).isTrue();
    }

    /**
     * PIÈGE §7.8 : {@code JsonNode.has(String)} teste la présence d'une clé
     * d'objet, pas d'un élément de tableau. Sur un tableau il renverrait
     * systématiquement {@code false}, sans lever d'exception.
     */
    @Test
    void containsItereLesTableauxLaOuHasEchoueraitSilencieusement() {
        assertThat(test("""
                {"contains":{"path":"/matieres","value":"physique"}}""")).isTrue();
        assertThat(test("""
                {"contains":{"path":"/matieres","value":"anglais"}}""")).isFalse();
        assertThat(test("""
                {"containsAll":{"path":"/matieres","values":["maths","physique"]}}""")).isTrue();
        assertThat(test("""
                {"containsAll":{"path":"/matieres","values":["maths","anglais"]}}""")).isFalse();
    }

    @Test
    void lesComparaisonsNumeriquesRespectentLesBornes() {
        assertThat(test("""
                {"gte":{"path":"/capacite","value":24}}""")).isTrue();
        assertThat(test("""
                {"gt":{"path":"/capacite","value":24}}""")).isFalse();
        assertThat(test("""
                {"lte":{"path":"/capacite","value":24}}""")).isTrue();
        assertThat(test("""
                {"lt":{"path":"/capacite","value":30}}""")).isTrue();
    }

    /**
     * Un attribut absent ne matche jamais, y compris sur une comparaison
     * numérique : c'est ce qui produit un vivier vide, que le validateur détecte et
     * nomme, plutôt qu'une exception au milieu de la construction.
     */
    @Test
    void unAttributAbsentNeMatcheJamais() {
        assertThat(test("""
                {"gte":{"path":"/inconnu","value":1}}""")).isFalse();
        assertThat(test("""
                {"lt":{"path":"/inconnu","value":1}}""")).isFalse();
        assertThat(test("""
                {"contains":{"path":"/inconnu","value":"x"}}""")).isFalse();
        // « différent de » n'est pas vrai pour un attribut inconnu : le laisser
        // matcher ferait passer toutes les ressources mal saisies.
        assertThat(test("""
                {"neq":{"path":"/inconnu","value":"x"}}""")).isFalse();
    }

    @Test
    void lesCombinateursSeComposent() {
        assertThat(test("""
                {"and":[{"eq":{"path":"/type","value":"enseignant"}},
                        {"not":{"eq":{"path":"/statut","value":"titulaire"}}}]}""")).isTrue();
        assertThat(test("""
                {"or":[{"eq":{"path":"/type","value":"salle"}},
                       {"eq":{"path":"/type","value":"enseignant"}}]}""")).isTrue();
        assertThat(test("""
                {"in":{"path":"/statut","values":["vacataire","stagiaire"]}}""")).isTrue();
    }

    @Test
    void unScopeVideSappliqueATousLeMonde() {
        assertThat(test("{}")).isTrue();
    }

    @Test
    void unOperateurInconnuEchoueAvecUnMessageQuiListeLesDisponibles() {
        assertThatThrownBy(() -> CriterionParser.parse(json("""
                {"between":{"path":"/capacite","value":24}}""")))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("between")
                .hasMessageContaining("contains");
    }

    @Test
    void unCritereAPlusieursOperateursEstRefuse() {
        assertThatThrownBy(() -> CriterionParser.parse(json("""
                {"eq":{"path":"/type","value":"x"},"neq":{"path":"/type","value":"y"}}""")))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("and");
    }

    @Test
    void unCheminQuiNestPasUnPointeurJsonEstRefuse() {
        assertThatThrownBy(() -> CriterionParser.parse(json("""
                {"eq":{"path":"type","value":"x"}}""")))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("JSON Pointer");
    }
}
