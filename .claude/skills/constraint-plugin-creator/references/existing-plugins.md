# Catalogue des plugins V1

Avant d'en créer un, vérifier qu'aucun ne couvre déjà la **forme de raisonnement**
visée. S'il ne manque qu'un réglage, c'est une ligne dans `constraint_config` —
niveau 1, pas de déploiement.

Colonne « source » : d'où le plugin lit ses paramètres.

| `type()` | Défaut | Source | Horizons | Forme de raisonnement |
|---|---|---|---|---|
| `resource.conflict` | HARD | structurel | tous | paires chevauchantes sur une même ressource |
| `resource.availability` | HARD | `TimeProfile.allowed` | tous | appartenance à un `BitSet` |
| `resource.discouraged_slot` | SOFT | `TimeProfile.discouraged` | tous | appartenance à un `BitSet` |
| `resource.consistency` | HARD | config | tous | unicité — `countDistinct` |
| `booking.unassigned` | HARD | structurel | tous | absence d'affectation |
| `load.max` | HARD | config | tous¹ | somme par panier, plafond |
| `load.min` | SOFT | config | tous¹ | somme par panier, plancher **+ absence** |
| `load.balance` | SOFT | config | tous | somme des carrés |
| `demand.spread` | SOFT | config | **CYCLIC** | comptage par panier |
| `gaps.avoid` | SOFT | config | tous | séquence sur la journée |
| `day.span_max` | SOFT | config | tous | amplitude sur la journée |
| `time.preferred_window` | SOFT | config | tous | position horaire d'une séance |
| `time.weekly_regularity` | HARD | config | **CYCLIC** | récurrence entre séances d'une demande |
| `sequence.consecutive_max` | SOFT | config | tous | plus longue série contiguë |

¹ `period = MONTH` est refusé en horizon `CYCLIC`.

---

## Détail

### `resource.conflict` — structurel

Une ressource ne peut pas être à deux endroits à la fois. **Cette unique contrainte
garantit la cohérence de tous les emplois du temps** — professeurs, classes,
salles — simultanément, sans rien connaître d'aucun d'eux.

`forEachUniquePair` + `Joiners.equal(getResource)` + `Joiners.overlapping`.
Pénalité : créneaux de chevauchement.

Les accesseurs `startIndex()` / `endIndex()` rendent `-1` sur un poste non placé :
l'intervalle vide ne chevauche rien, ce qui rend la jointure sûre sans garde en
boucle chaude.

### `resource.availability` et `resource.discouraged_slot` — structurels, jumeaux

Deux plugins, pas un avec un `if`. Le découpage donne deux poids réglables
séparément, deux lignes distinctes dans le rapport, et la possibilité d'en
désactiver un sans toucher l'autre.

**C'est le mécanisme de bascule dur ↔ souple par ressource** : passer une ligne de
`resource_availability` de `UNAVAILABLE` à `DISCOURAGED` la fait migrer d'un
`BitSet` vers l'autre, donc d'une contrainte vers l'autre. Aucun code modifié.

Pénalités : `forbiddenSlots` et `discouragedSlots`, proportionnelles au
débordement — sans quoi le solveur n'aurait aucune raison de préférer le
débordement minimal quand aucune place propre ne se libère.

### `resource.consistency` — config

« Le même enseignant sur toutes les séances d'un cours. » **Sans lui, les premiers
plannings semblent aberrants** alors que le moteur a parfaitement fait son travail :
chaque `ResourceBooking` est indépendant.

Clé : `(demandId, role)`. Raisonne sur le **rôle**, pas sur le type — un examen a
besoin de deux surveillants, et un surveillant peut être enseignant ou
administratif.

`countDistinct`, pas `forEachUniquePair` : sur 36 occurrences en horizon daté, la
version par paires devient quadratique.

**Attention** : déclaré dur, il bloque tout pas intermédiaire de la recherche
locale vers une réaffectation. Voir `infeasibility-diagnoser`.

### `booking.unassigned` — structurel

Nécessaire dès lors que `allowsUnassigned = true` : sans lui, laisser tous les
postes vides serait la meilleure solution.

**Seul plugin utilisant `forEachIncludingUnassigned`.** `forEach` exclut les
entités à variable nulle et le rendrait totalement muet.

### `load.max` / `load.min` — config

`Params(int maxMinutes, Period period)` / `Params(int minMinutes, Period period)`.
Clé : `AggregationScope(resource, period, periodIndex)`. Collecteur `sum`.

Le cumul des scopes est une **intersection**, pas une hiérarchie : un enseignant
vacataire est soumis aux deux quotas simultanément. Pour qu'une règle spécifique
remplace la générale, l'exprimer dans le scope avec un `not`.

`load.min` déclare **deux pipelines** : le principal, et `load.min.never_used` qui
part des ressources avec `ifNotExists` pour attraper celles à charge nulle — que le
premier ne peut structurellement pas voir. C'est le patron à copier quand une règle
doit se déclencher sur une absence.

### `load.balance` — config

Somme des carrés. Le volume total est fixe, imposé par la demande ; à somme
constante, la somme des carrés est minimale quand toutes les valeurs sont égales.
Pénaliser `charge²` équilibre donc automatiquement, sans jamais calculer de
moyenne, et sans supposer une cible connue d'avance.

Diviseur `/1000` pour éviter le débordement d'entier.

### `demand.spread` — config, CYCLIC

`Params(int maxPerDay)`. Clé `(demandId, dayInCycle)`, collecteur `countBi`.
**Sans lui, rien n'empêche les 4 séances d'un cours de tomber le même jour.**

Cyclique uniquement : en horizon daté, `dayInCycle` deviendrait le jour de l'année
et la contrainte deviendrait *muette* — elle ne planterait pas, elle cesserait de
détecter quoi que ce soit.

### `gaps.avoid`, `day.span_max`, `sequence.consecutive_max` — config

Trois lectures différentes de la même journée, toutes via `toList` sur la clé
`ResourceDayKey(resource, dayInCycle)` puis un calcul dans `Bookings` :
`gapMinutes`, `spanMinutes`, `longestRunMinutes`.

Tous raisonnent sur les **heures réelles**. Deux ordinaux successifs peuvent être
séparés par une récréation ; `toleranceMinutes` définit ce qui rompt réellement une
série.

Un seul plugin `gaps.avoid` sert les trous des élèves **et** ceux des professeurs :
la différence d'importance passe par le poids ②, avec deux lignes de configuration.

### `time.preferred_window` — config, sur les activités

`Params(String from, String to)`. Le scope s'évalue contre `demand.attributes`, pas
contre la ressource : « les maths plutôt le matin » parle de la matière.

Pénalité : minutes de la séance tombant hors de la fenêtre. C'est l'exemple
canonique de la frontière vivier/contrainte — placée en `placement.window`, la
règle deviendrait une borne dure et muette.

### `time.weekly_regularity` — config, CYCLIC

Nécessaire dès que `cycle_days > 7`. La séance de référence est la première dans
l'ordre des créneaux : le tri est déterministe et identique dans les deux
traversées, sans quoi le rapport désignerait d'autres séances que celles comptées
par le solveur.

---

## Catalogue V2 — non implémenté

| `type()` | Sémantique | Horizon |
|---|---|---|
| `coverage.min_role` | au moins un manager dans un groupe de postes génériques | tous |
| `rest.min` | repos minimum entre deux journées | DATED |
| `fairness.rotation` | équité des gardes sur la durée | DATED |
| `event.date` | événement à date fixe | DATED |
| `room.stability` | même salle pour une série de séances | tous |
| `travel.building` | pénaliser les changements de bâtiment | tous |
| `precedence` | l'évaluation après le module | DATED |
