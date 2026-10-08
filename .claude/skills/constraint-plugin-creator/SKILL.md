---
name: constraint-plugin-creator
description: Crée ou modifie un plugin de contrainte du moteur de planification, de la formulation métier jusqu'aux tests. Utiliser systématiquement dès qu'il est question d'ajouter une règle de planification, une contrainte, une pénalité, un quota, une préférence horaire, une interdiction ou un équilibrage — même si l'utilisateur ne prononce jamais le mot « plugin ». À utiliser aussi pour modifier un plugin existant, car les mêmes invariants s'appliquent.
---

# Créer un plugin de contrainte

Le niveau 2 du modèle d'extension : ce qui exige un déploiement. Avant d'y aller,
vérifier qu'on en a vraiment besoin.

---

## Avant tout : est-ce vraiment un plugin ?

Trois questions, dans cet ordre. **Ne pas écrire de code avant d'y avoir répondu.**

### 1. Est-ce une demande plutôt qu'une contrainte ?

Une **contrainte** juge une affectation qui existe. Une **demande** crée une case à
remplir. Le solveur ne crée jamais d'entités : l'ensemble est figé au chargement.

Si la règle dit « il faut N de quelque chose », c'est du **niveau 0** — des lignes
dans `demand` et `requirement`. S'arrêter ici et rediriger.

> *« Il faut 6 personnes entre 7 h et 15 h »* → deux `INSERT`, pas un plugin.

### 2. Est-ce structurellement impossible plutôt qu'une règle d'organisation ?

Test : **quelqu'un pourrait-il un jour vouloir l'enfreindre ?**

- Non → critère d'éligibilité, dans `requirement.criteria`. Pas de plugin.
- Oui → c'est bien une contrainte, continuer.

> *« Une salle de 20 places ne peut pas accueillir 30 élèves »* → vivier.
> *« Les maths plutôt le matin »* → contrainte souple.

**Attention au piège inverse.** Ne jamais mettre dans le vivier une règle qui
*ressemble* à une préférence : l'utilisateur verrait « aucune solution » sans
jamais savoir laquelle bloque, alors qu'une contrainte souple violée apparaît noir
sur blanc dans le rapport.

**Et à l'impossibilité technique.** Une règle qui dépend du *moment* ne peut pas
entrer dans `criteria` : au moment où le vivier est construit, on ignore quand
l'activité tombera — le créneau est décidé par une autre entité de planification.

### 3. Un plugin existant couvre-t-il déjà cette forme de raisonnement ?

Lire `references/existing-plugins.md`. S'il ne manque qu'un réglage, c'est du
**niveau 1** — une ligne dans `constraint_config`, sans recompiler.

Ne créer un plugin que si la *forme* du raisonnement est nouvelle : une agrégation
différente, une jointure différente, une notion de séquence.

---

## Étape 1 — Fixer l'identifiant

L'identifiant est un **contrat**. Il apparaîtra dans `constraint_config.type`, dans
`constraint_weight.constraint_id` et dans les rapports. Le renommer laisserait
toutes les lignes existantes pointer dans le vide, **silencieusement**. On ne le
renomme pas, on le déprécie.

Convention : `domaine.regle` en minuscules — `load.max`, `gaps.avoid`,
`time.preferred_window`.

**Demander confirmation à l'utilisateur avant de continuer.**

---

## Étape 2 — Déclarer les paramètres

Un `record` imbriqué dans le plugin, et son JSON Schema. Le schéma valide la saisie
côté API : sans lui, une configuration invalide ferait échouer une résolution des
semaines plus tard, sans message exploitable.

```java
public record Params(int maxMinutes, Period period) {}

@Override
public JsonNode paramSchema() {
    return Schemas.object(Map.of(
            "maxMinutes", Schemas.minutes("Plafond en minutes sur la période", 1),
            "period", Schemas.period("Découpage du quota")),
            List.of("maxMinutes", "period"));
}
```

**Les durées s'expriment en minutes, jamais en créneaux.** La grille est un
paramètre modifiable ; un seuil en créneaux serait corrompu par un changement de
granularité.

Un plugin sans réglage déclare `public record Params() {}` et `Schemas.empty()`. Un
plugin structurel déclare `NoParams.class` et redéfinit `configDriven()` à `false`.

---

## Étape 3 — Déterminer la source des paramètres

| Le paramètre varie par… | Il vit dans… |
|---|---|
| ressource individuelle | un attribut de `Resource` ou son `TimeProfile` |
| catégorie de ressources | `constraint_config`, avec un `scope` |

Test : *est-ce que ça décrit une ressource, ou une politique ?*

Beaucoup de plugins lisent les deux. **Ne pas en faire deux catégories** : une seule
interface, un seul registre.

### Si le plugin lit un attribut métier, le déclarer

Passer par le `scope` ne demande rien : le scope est une donnée, et le vocabulaire
qu'il interroge est déjà au catalogue. Mais un plugin qui **déréférence lui-même**
une clé de `attributes` — typiquement un quota porté par la ressource plutôt que
par la règle — doit la déclarer :

```java
@Override
public Set<String> readsAttributes() {
    return Set.of("quota_hebdo_minutes");
}
```

Et la clé doit exister dans `domain/attribute/AttributeCatalog.java` :

```java
new AttributeDefinition("quota_hebdo_minutes", "Quota hebdomadaire",
        "La charge de travail visée sur une semaine.",
        DURATION_MINUTES, List.of(), 0, Set.of(SCHOOL, COMPANY, RESTAURANT), Set.of()),
```

**Pourquoi c'est obligatoire.** Le catalogue est ce qui génère le formulaire de
saisie des ressources. Une clé qu'il ignore n'a **aucun champ** dans l'interface :
l'attribut restera vide sur les 500 ressources, la règle ne se déclenchera jamais,
et rien ne le signalera — ni erreur au chargement, ni violation au rapport, ni
score anormal. `AttributeCatalogTest` fait tomber le build dans ce cas.

**Ne jamais tenir de liste inverse.** « Qui lit `quota_hebdo_minutes` ? » est
calculé par `CatalogService` en parcourant le registre. `readsAttributes()` est la
seule déclaration ; une seconde finirait par diverger.

Le catalogue est du **vocabulaire**, donc du code (§2.1) — pas une table. Ajouter
un mot ne demande aucune migration, et se relit en revue au même endroit que le
plugin qui l'exploite.

---

## Étape 4 — Factoriser la règle AVANT d'écrire le moindre pipeline

C'est l'étape que tout le monde saute, et c'est la source de dérive numéro un.

Extraire en méthodes statiques privées **tout ce qui constitue la règle** :

```java
private static Params params(ConstraintConfig rule) { return rule.paramsAs(Params.class); }

private static AggregationScope scopeOf(ResourceBooking b, Params p) { … }   // la clé
private static boolean violates(int total, Params p) { … }                   // le seuil
private static int penalty(int total, Params p, int ruleWeight) { … }        // la formule
```

Les deux traversées appelleront **ces mêmes méthodes**. Ce qui restera en double ne
sera que de la plomberie de parcours — un `groupBy` d'un côté, une boucle avec
`HashMap` de l'autre.

**Ne jamais recopier le seuil, la formule ou la clé.** Une correction appliquée
d'un seul côté rend le rapport utilisateur faux sans qu'aucun signal ne l'indique.

### Les trois poids

```
pénalité = ① poids de la contrainte × ② poids de la règle × ③ ampleur
```

- ① vient de `constraint_weight`, appliqué par Timefold. **Ne pas le manipuler dans
  le plugin.**
- ② est `rule.weight()`, à multiplier dans ta lambda.
- ③ est l'ampleur, **calculée**. Préférer une pénalité proportionnelle à une
  pénalité fixe : sans gradient, le solveur reste indifférent entre un petit et un
  gros dépassement, et n'a aucune raison de réduire progressivement une violation
  qu'il ne peut pas supprimer.

---

## Étape 5 — Traversée A : le pipeline incrémental

Partir de `assets/PluginTemplate.java.tpl`. Consulter
`references/constraint-streams-patterns.md` pour choisir la forme.

Quatre décisions :

1. **Point de départ.** `Streams.scopedBookings(factory, TYPE)` si les paramètres
   viennent de la configuration et que la règle parle des ressources ;
   `Streams.scopedActivities(factory, TYPE)` si elle parle de la demande ;
   `Streams.placedBookings(factory)` pour un plugin structurel.
2. **Collecteur.** Il détermine la nature de la règle : `sum` pour un volume,
   `countBi` pour un nombre d'occurrences, `countDistinct` pour une unicité,
   `toList` quand il faut examiner la séquence.
3. **Clé d'agrégation.** Sa composition *définit la sémantique*. Retirer la
   ressource transforme un quota individuel en quota d'équipe ; retirer la période
   transforme un quota journalier en quota sur tout l'horizon. **Y inclure la
   règle**, sans quoi plusieurs lignes de configuration mélangeraient leurs totaux.
4. **Poids ③.** Une lambda proportionnelle à l'ampleur.

Vérifier les signatures Timefold contre le jar **avant** d'écrire — la version
verrouillée est 1.16.0, pas la 2.x du SDD :

```bash
docker run --rm -v "$HOME/.m2:/root/.m2" eclipse-temurin:21-jdk \
  javap -cp /root/.m2/repository/ai/timefold/solver/timefold-solver-core/1.16.0/timefold-solver-core-1.16.0.jar \
  ai.timefold.solver.core.api.score.stream.ConstraintCollectors | grep -i toList
```

### Quand la règle doit se déclencher sur une absence

Les Constraint Streams n'offrent pas de jointure externe. Une ressource sans aucune
affectation n'apparaît dans aucun panier d'agrégation : un pipeline qui part des
affectations ne peut structurellement pas la voir.

Dans ce cas, redéfinir `buildAll()` et `constraintNames()` pour ajouter un second
pipeline partant des ressources, avec `ifNotExists`. `WeightBook` appliquera le
poids ① du plugin à tous ses pipelines, et leurs violations seront rapportées sous
le même identifiant. Voir `MinLoadConstraint` — c'est l'exemple de référence.

---

## Étape 6 — Traversée B : le rapport métier

C'est la **seule source d'explicabilité du produit**. L'édition Community ne
garantit aucune API d'analyse : sans cette méthode, un planning invalide se présente
comme un écran vide.

```java
@Override
public List<Violation> validate(Schedule solution, ConstraintConfig rule) { … }
```

Elle tourne **une fois, sur une solution figée**, en O(nombre de bookings). Elle ne
descend jamais dans la boucle chaude — son coût est négligeable, ne pas chercher à
l'optimiser.

### Ce qu'elle fait que la traversée A ne peut pas faire

Le pipeline Constraint Streams est *conçu pour ne pas retenir qui a matché* : c'est
exactement ce qui le rend rapide. La traversée B construit une phrase et liste les
objets impliqués.

### Le modèle de violation

```java
new Violation(
    TYPE,                    // toujours l'identifiant du plugin, même pour un pipeline auxiliaire
    rule.level(),            // résolu au chargement depuis constraint_weight
    penalty(…),              // poids ② × ③, AVANT le poids ①
    "phrase en vocabulaire métier",
    List.of(resource.id()),  // qui est impliqué        → surlignage côté interface
    List.of(demandId),       // quelles séances         → surlignage côté interface
    Map.of("totalMinutes", total, "maxMinutes", max))   // valeurs brutes pour le frontend
```

**`resourceIds` et `activityIds` ne sont pas décoratifs.** Ils permettent à
l'interface de surligner Jean et ses trois séances du mardi au lieu de se contenter
d'une phrase. Sans eux, le rapport devient illisible au-delà de quelques violations.

**`context` porte les valeurs brutes** pour que le frontend puisse formater, trier
et filtrer sans réanalyser le message.

### Le message

Il s'adresse à un proviseur, pas à un développeur.

> ✓ *« Jean Dupont dépasse son quota du mardi : 5 h 00 au lieu de 4 h 00 maximum. »*
> ✗ *« load.max violated, delta=60 »*

Utiliser `Bookings.hoursMinutes(minutes)` pour les durées et
`CycleBuckets.label(period, index)` pour les périodes. Nommer la ressource par son
`label`, la demande par son `getDemandLabel()`.

### Les gardes doivent être les mêmes des deux côtés

C'est le point de divergence le plus fréquent. Utiliser
`Streams.placedBookings(solution.getBookings(), rule)` et
`Streams.scopedActivities(solution.getActivities(), rule)` : ces helpers sont écrits
juste à côté de leurs jumeaux de pipeline, précisément pour qu'une divergence se
voie à la relecture.

---

## Étape 7 — Enregistrer et tester

1. **Ajouter la classe** à `ConstraintRegistry.STANDARD`.
2. **Ajouter une ligne de poids** au seed `V2__seed_mock_school.sql` si le plugin
   doit être actif par défaut sur la démo.
3. **Écrire le test du plugin**, depuis `assets/PluginTest.java.tpl`. Trois cas
   minimum :
   - règle respectée → aucune violation ;
   - règle violée → pénalité exacte, message contenant le libellé métier ;
   - **deux lignes de configuration concurrentes sur la même ressource** — c'est ce
     dernier qui attrape les erreurs de clé d'agrégation.
4. **Le test de cohérence tourne déjà** dans `ScoreConsistencyTest` et couvre
   automatiquement tout plugin du registre configuré dans `SchoolFixture`. Ajouter
   une ligne de configuration au fixture pour que le nouveau plugin y soit inclus.

```bash
docker run --rm -v "$PWD/planning/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

**Si `lExplicationRedonneExactementLeScoreDuSolveur` tombe, une des deux traversées
est fausse.** Ne jamais ajuster l'assertion.

`leScoreIncrementalResisteAuRecalculComplet` tourne en `FULL_ASSERT` : il recalcule
le score depuis zéro à chaque mouvement et le compare à l'incrémental. Il fait
tomber toute lambda impure et toute dépendance non déclarée entre entités.

---

## Étape 8 — Documenter

Ajouter une entrée à `references/existing-plugins.md` : identifiant, sémantique,
source des paramètres, niveau par défaut, horizons supportés, et le cas limite
éventuel.

---

## Liste de contrôle avant de considérer le plugin terminé

- [ ] Aucune valeur métier en dur — pas de « maths », pas de « enseignant » dans le
      code. Tout passe par `scope` et `params`.
- [ ] Tout attribut lu directement est déclaré par `readsAttributes()` **et**
      présent dans `AttributeCatalog`. Sinon l'interface n'offre aucun champ pour
      le saisir, et la règle reste muette.
- [ ] Aucune date, aucun jour de semaine, aucune notion de cycle manipulés
      directement — tout passe par `CycleBuckets`.
- [ ] Les lambdas sont pures : pas d'accès base, pas de `LocalDate.now()`, pas
      d'aléatoire, pas d'état mutable.
- [ ] `horizons()` déclaré si la règle n'a de sens que sur un mode.
- [ ] `validate(config, horizon)` refuse les combinaisons impossibles —
      typiquement `period = MONTH` en cyclique.
- [ ] Aucun filtre sur le type de ressource : le vivier le garantit déjà, et ce
      serait réévalué des millions de fois pour rien.
- [ ] La contiguïté temporelle, si elle est en jeu, teste les heures réelles via
      `Bookings`, jamais `ordinal + 1`.
- [ ] `equals`/`hashCode` de la clé d'agrégation reposent sur des identifiants, pas
      sur un `JsonNode`.
- [ ] Le seuil, la formule et la clé sont dans des méthodes partagées, appelées par
      les deux traversées.
- [ ] `validate(Schedule, ConstraintConfig)` implémenté, avec des messages métier et
      les identifiants impliqués.
- [ ] Les tests passent, dont le test de cohérence.

---

## Les pièges

Tous échouent silencieusement. Aucun n'est théorique : ils ont tous été rencontrés.

### `forEach` exclut les entités à variable nulle

Y compris avec `allowsUnassigned = true`. Pour raisonner sur l'absence
d'affectation, il faut `forEachIncludingUnassigned`. Écrit avec `forEach`, le
plugin `booking.unassigned` ne matchait rien, laisser tous les postes vides
devenait gratuit, et le solveur rendait **un planning entièrement vide avec un
score parfait**.

### Lire la variable d'une autre entité corrompt le score incrémental

`booking.getActivity().getStart()` compile et semble juste, mais Timefold ignore
que déplacer la séance invalide le tuple du booking. Passer par la variable ombre
`booking.getStart()`, déjà en place.

### `count()` n'existe pas sur un flux Bi

C'est `countBi()`. Sur un Tri, `countTri()`. Même logique pour `toList` et `sum`,
qui ont une surcharge par arité.

### La clé d'agrégation sans la règle

Trois quotas différents fusionneraient leurs totaux. Le `filter` sur le type ne
réduit jamais à une seule ligne — c'est tout l'intérêt du système.

### Une pénalité fixe prive le solveur de gradient

`penalize(ONE_SOFT)` sans lambda rend le solveur indifférent entre dépasser d'une
minute et dépasser de trois heures.

### `forEachUniquePair` sur les activités

Devient quadratique en horizon daté. Préférer `groupBy` + collecteur. C'est ce qui
a fait choisir `countDistinct` pour `resource.consistency` et `groupBy` pour
`demand.spread`.

### Une contrainte dure peut bloquer la recherche

Si une règle dure rend invalide tout pas intermédiaire vers une meilleure solution,
le solveur reste enfermé. Observé avec `resource.consistency`. Ce n'est pas un bug
du plugin, mais ça doit être connu avant de déclarer une règle dure par défaut.
