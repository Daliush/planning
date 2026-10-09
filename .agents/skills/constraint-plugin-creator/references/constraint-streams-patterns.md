# Constraint Streams — les formes utilisées dans ce code

API **Timefold 1.16.0**. Toutes les signatures ci-dessous ont été vérifiées contre
le jar. Le SDD cite une autre version : ne pas écrire
d'après la documentation en ligne.

```bash
docker run --rm -v "$HOME/.m2:/root/.m2" eclipse-temurin:21-jdk \
  javap -cp /root/.m2/repository/ai/timefold/solver/timefold-solver-core/1.16.0/timefold-solver-core-1.16.0.jar \
  ai.timefold.solver.core.api.score.stream.ConstraintCollectors
```

---

## Les têtes de pipeline

Elles sont déjà écrites dans `domain/constraint/Streams.java`. **Les utiliser plutôt
que de repartir de `factory`** : leurs jumelles pour la traversée d'explicabilité
vivent dans le même fichier, ce qui rend une divergence visible à la relecture.

```java
// Postes pourvus et placés — pour un plugin structurel
Streams.placedBookings(factory)                    // Uni<ResourceBooking>

// Forme B : les paramètres viennent de la configuration, la règle parle des ressources
Streams.scopedBookings(factory, TYPE)              // Bi<ConstraintConfig, ResourceBooking>

// La règle parle de la demande — « les maths plutôt le matin »
Streams.scopedActivities(factory, TYPE)            // Bi<ConstraintConfig, ScheduledActivity>
```

Le scope est déjà appliqué : le plugin n'a plus qu'à agréger.

---

## Le patron d'un plugin configurable

```java
Streams.scopedBookings(factory, TYPE)
    .groupBy((rule, booking) -> rule,                            // ← la règle DANS la clé
             (rule, booking) -> scopeOf(booking, params(rule)),  // ← méthode partagée
             ConstraintCollectors.sum((rule, b) -> b.durationMinutes()))
    .filter((rule, scope, total) -> violates(total, params(rule)))        // ← partagée
    .penalize(HardSoftScore.ONE_HARD,
              (rule, scope, total) -> penalty(total, params(rule), rule.weight()))  // ← partagée
    .asConstraint(TYPE);
```

`groupBy(clé₁, clé₂, collecteur)` sur un flux Bi rend un flux **Tri** — d'où les
trois paramètres des lambdas suivantes.

**Tout ce qui n'est ni dans la clé ni dans le collecteur est définitivement perdu.**
Les bookings individuels ne sont plus accessibles en aval : c'est pourquoi la règle
doit figurer dans la clé, sans quoi aucun moyen de récupérer le seuil.

---

## Collecteurs — une surcharge par arité

| Uni | Bi | Tri |
|---|---|---|
| `count()` | `countBi()` | `countTri()` |
| `countDistinct(f)` | `countDistinct(bi)` | `countDistinct(tri)` |
| `sum(toInt)` | `sum(toIntBi)` | `sum(toIntTri)` |
| `toList(f)` | `toList(bi)` | `toList(tri)` |

**`count()` sur un flux Bi ne compile pas.** C'est l'erreur la plus fréquente.

Le collecteur détermine la nature de la règle :

- `sum` → un volume (quota horaire) ;
- `countBi` → un nombre d'occurrences (« pas plus de 3 séances par jour ») ;
- `countDistinct` → une unicité (« le même enseignant partout ») ;
- `toList` → il faut examiner la séquence (trous, amplitude, enchaînements).

---

## Joiners

```java
Joiners.equal(ResourceBooking::getResource)                     // Bi, même classe
Joiners.equal((rule, r) -> r, ResourceBooking::getResource)     // Tri, depuis un Bi
Joiners.overlapping(ResourceBooking::startIndex,
                    ResourceBooking::endIndex)                  // intervalles
Joiners.filtering((rule, booking) -> …)                         // Bi
Joiners.filtering((rule, r, booking) -> …)                      // Tri
```

Les joiners `filtering` doivent venir **en dernier**. Les joiners indexés
(`equal`, `overlapping`) sont évalués **avant tout `filter`** du flux : ils voient
donc des entités potentiellement non initialisées pendant la phase de construction.

---

## `forEach` et l'absence

```java
factory.forEach(X.class)                    // exclut les entités à variable nulle
factory.forEachIncludingUnassigned(X.class) // les inclut
```

**Ceci vaut aussi pour `allowsUnassigned = true`.** Pour raisonner sur l'absence
d'affectation, `forEachIncludingUnassigned` est obligatoire — c'est le seul usage
légitime, dans `booking.unassigned`.

`join(Class)`, `ifExists`, `ifNotExists` et `forEachUniquePair` héritent tous de la
sémantique de `forEach` : les postes non pourvus en sont exclus.

---

## Détecter une absence — le second pipeline

Les Constraint Streams n'offrent pas de jointure externe. Une ressource sans
affectation n'apparaît dans aucun panier d'agrégation.

```java
private Constraint buildNeverUsed(ConstraintFactory factory) {
    return factory.forEach(ConstraintConfig.class)
            .filter(rule -> rule.type().equals(TYPE))
            .join(Resource.class,
                  Joiners.filtering((rule, resource) -> rule.scope().matches(resource)))
            .ifNotExists(ResourceBooking.class,
                  Joiners.equal((rule, resource) -> resource, ResourceBooking::getResource))
            .penalize(HardSoftScore.ONE_SOFT,
                      (rule, resource) -> emptyPenalty(params(rule), rule.weight()))
            .asConstraint(NEVER_USED);
}

@Override public List<Constraint> buildAll(ConstraintFactory factory) {
    return List.of(build(factory), buildNeverUsed(factory));
}

@Override public List<String> constraintNames() {
    return List.of(TYPE, NEVER_USED);
}
```

`WeightBook` applique le poids ① du plugin à **tous** ses noms de contrainte. Sans
`constraintNames()`, le pipeline auxiliaire garderait la valeur écrite en dur et
ignorerait la configuration.

Les violations des deux pipelines se rapportent sous le **même** `TYPE`, pour que
le rapport reste lisible et que le test de cohérence tienne.

C'est aussi ce qui rend `Schedule.resources` indispensable : sans cette liste sur la
solution, une charge nulle serait invisible.

---

## Le temps

**Aucun plugin ne manipule une date, un jour de semaine ou une notion de cycle
directement.** `CycleBuckets` décrit CYCLIC ; `TemporalBuckets` et `TimeHorizon` prennent aussi en charge DATED. Pour des quotas, agréger les minutes de `Bookings.minutesIn(booking, bucket)` dans les `PeriodBucket` compilés ; une activité peut contribuer à plusieurs dates ou semaines. Fonctions cycliques :

```java
CycleBuckets.bucketOf(slot, period)        // DAY → dayInCycle, WEEK → /7, CYCLE → 0
CycleBuckets.sameRecurrence(a, b)          // même jour de semaine et même heure
CycleBuckets.supports(period)              // MONTH refusé en cyclique
CycleBuckets.label(period, index)          // « du mardi », pour les messages
```

Ces fonctions sont pures et ne dépendent que du `Timeslot`, ce qui permet aux
plugins de les appeler sans porter d'instance de `TimeHorizon` — le
`ConstraintProvider` étant instancié par Timefold, hors du contexte Spring.

Pour la séquence sur une journée, `Bookings` :

```java
Bookings.gapMinutes(sameDay, tolerance)
Bookings.spanMinutes(sameDay)
Bookings.longestRunMinutes(sameDay, tolerance)
Bookings.overlapSlots(a, b)
Bookings.hoursMinutes(minutes)             // « 5 h 30 » pour les messages
```

**Toujours les heures réelles, jamais `ordinal + 1`.** Deux ordinaux successifs
peuvent être séparés par une récréation ; la version naïve produit des résultats
faux uniquement autour des pauses — un bug rare, plausible et pénible à reproduire.

---

## La variable ombre

`ResourceBooking.start` est une `@ShadowVariable` alimentée depuis
`ScheduledActivity.start` par `BookingStartListener`.

**Ne jamais lire `booking.getActivity().getStart()` dans un pipeline.** Ça compile
et donne le bon résultat en apparence, mais Timefold ignore que déplacer la séance
invalide le tuple du booking : le score incrémental devient faux, silencieusement.
Utiliser `booking.getStart()`, `booking.startIndex()`, `booking.dayInCycle()`.

`environmentMode=FULL_ASSERT` fait tomber ce genre de bug immédiatement.

---

## Pureté des lambdas

Aucun accès base, aucun `LocalDate.now()`, aucun aléatoire, aucun état mutable. Le
réseau de propagation ne rejoue pas le pipeline : il propage un delta. Une lambda
impure corrompt le score incrémental et rend le diagnostic très difficile.

---

## Filtrer tôt

L'ordre des opérations compte : réduire le flux en amont allège tout ce qui suit.
Les têtes de `Streams` appliquent déjà le scope avant toute agrégation.
