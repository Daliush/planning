---
name: criterion-operator-creator
description: Ajoute un opérateur au moteur de critères du module planning. Utiliser quand un scope ou des critères d'éligibilité ne peuvent pas s'exprimer avec les opérateurs existants — « il me faut un critère qui compare deux attributs », « qui teste une plage », « qui vérifie qu'un tableau est vide ». Vérifier d'abord qu'aucune combinaison des opérateurs existants ne suffit.
---

# Ajouter un opérateur de critère

Le moteur de critères est le vocabulaire dans lequel les clients écrivent leurs
phrases. **Sa richesse détermine combien de temps un nouveau besoin reste de la
simple configuration** : un vocabulaire pauvre ramène en Java à chaque client.

Un seul moteur, deux usages : `requirement.criteria` (quelles ressources peuvent
occuper ce poste) et `constraint_config.scope` (à quelles ressources s'applique
cette règle). Un opérateur ajouté sert les deux.

---

## Avant tout : est-ce vraiment un nouvel opérateur ?

Douze opérateurs existent : `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`,
`contains`, `containsAll`, `and`, `or`, `not`. Plus le critère vide `{}`, qui vaut
« tout le monde ».

Beaucoup de besoins se composent :

| Besoin | Composition existante |
|---|---|
| « entre 20 et 30 » | `{"and":[{"gte":…},{"lte":…}]}` |
| « ni A ni B » | `{"not":{"in":{"path":…,"values":["A","B"]}}}` |
| « tout sauf les vacataires » | `{"not":{"eq":{"path":"/statut","value":"vacataire"}}}` |
| « au moins une matière parmi » | `{"or":[{"contains":…},{"contains":…}]}` |

**N'ajouter un opérateur que si la composition est impossible ou illisible.**

---

## Étape 1 — Fixer le nom et la forme JSON

Le nom devient un contrat : il apparaîtra dans des lignes de `requirement.criteria`
et de `constraint_config.scope` en base. Le renommer plus tard laisserait ces
lignes lever `InvalidConfigException` au chargement — bruyamment, au moins.

Conventions de forme :

```json
{"operateur": {"path": "/chemin", "value": <scalaire>}}
{"operateur": {"path": "/chemin", "values": [<scalaires>]}}
{"operateur": [<critères>]}                                  // combinateur
{"operateur": <critère>}                                     // unaire
```

Le chemin est un **JSON Pointer** (RFC 6901), la syntaxe de `JsonNode.at()`.

---

## Étape 2 — Déclarer le record

Dans `domain/criteria/Criterion.java`, à l'intérieur du `sealed interface` :

```java
record MonOperateur(String path, BigDecimal value) implements Criterion {
    @Override
    public boolean test(JsonNode attributes) {
        return JsonValues.…(attributes.at(path), value);
    }
}
```

Le `sealed interface` garantit que le compilateur signalera tout `switch` exhaustif
à compléter ailleurs.

**La comparaison passe par `JsonValues`**, jamais par une logique Jackson écrite sur
place. Ce helper porte la règle transverse : *un attribut absent ou d'un type
inattendu ne fait jamais planter le moteur, il ne matche simplement pas.*

C'est volontaire — une exception au milieu de la construction des viviers rendrait
tout le scénario inexploitable pour une seule ressource mal saisie, alors que le
`ProblemValidator` détecte le vivier vide qui en résulte et nomme le besoin
concerné.

---

## Étape 3 — Enregistrer dans le parseur

Dans `CriterionParser.OPERATORS` :

```java
Map.entry("monOperateur", body -> new Criterion.MonOperateur(path(body), number(body))),
```

Réutiliser les helpers de validation existants — `path()`, `value()`, `number()`,
`text()`, `valueList()`, `textList()`, `children()` — qui produisent déjà des
messages d'erreur exploitables. Ne pas parser à la main.

L'opérateur apparaîtra automatiquement dans `GET /api/catalog`, donc dans l'éditeur
du frontend : `CriterionParser.operators()` lit la même map.

---

## Étape 4 — Tester

Dans `CriterionTest`, quatre cas **obligatoires** :

```java
@Test void leCasNominalMatche() { … }
@Test void leCasNegatifNeMatchePas() { … }

/** Un attribut absent ne matche jamais — y compris pour un opérateur de négation. */
@Test void unAttributAbsentNeMatcheJamais() {
    assertThat(test("""
            {"monOperateur":{"path":"/inconnu","value":1}}""")).isFalse();
}

/** Un type inattendu ne lève pas d'exception. */
@Test void unTypeInattenduNeMatchePasEtNePlantePas() {
    assertThat(test("""
            {"monOperateur":{"path":"/matieres","value":1}}""")).isFalse();
}
```

Les deux derniers sont ceux qu'on oublie, et ce sont ceux qui comptent : un
opérateur qui lève une exception sur un attribut absent fait échouer une résolution
entière.

Ajouter aussi un cas de forme invalide, pour vérifier que le message d'erreur est
exploitable :

```java
assertThatThrownBy(() -> CriterionParser.parse(json("""
        {"monOperateur":{"path":"sans-slash","value":1}}""")))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessageContaining("JSON Pointer");
```

---

## Étape 5 — Documenter

Ajouter une ligne au tableau des opérateurs dans
`.claude/skills/constraint-plugin-creator/references/constraint-streams-patterns.md`
si l'opérateur change la façon d'écrire les scopes, et une entrée dans le
`README.md` du module si sa sémantique demande une explication.

---

## Les pièges

**`JsonNode.has(String)` teste une clé d'objet, pas un élément de tableau.** Sur
`["maths","physique"]`, `has("maths")` renvoie systématiquement `false`, sans lever
d'exception. C'est un bug parfaitement silencieux. Itérer explicitement, comme le
fait `JsonValues.contains`.

**Les nombres JSON ne se comparent pas par égalité textuelle.** `24` et `24.0` sont
le même nombre. `JsonValues.equal` compare via `decimalValue()`.

**Un opérateur de négation doit refuser l'attribut absent.** `neq` sur une clé
inconnue renvoie `false`, pas `true` : sinon toutes les ressources mal saisies
passeraient le filtre. Reprendre cette convention.

**Le moteur ne lit que `attributes`.** Ni `label`, ni `id`, ni aucun champ fixe de
`Resource`. Un opérateur qui aurait besoin d'un champ fixe est le signe que ce
champ devrait être projeté dans `attributes` par le builder, comme l'est `type`.

**Les critères sont évalués une seule fois, au chargement.** Ne pas s'inquiéter de
leur coût, mais ne pas non plus y mettre d'état : ils doivent rester des fonctions
pures.
