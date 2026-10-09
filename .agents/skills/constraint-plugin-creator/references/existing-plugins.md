# Catalogue des plugins

Avant d'en créer un, vérifier qu'aucun ne couvre déjà la **forme de raisonnement**
visée. S'il ne manque qu'un réglage, c'est une ligne dans `constraint_config` —
niveau 1, pas de déploiement.

Colonne « source » : d'où le plugin lit ses paramètres.

| `type()` | Défaut | Source | Horizons | Forme de raisonnement |
|---|---|---|---|---|
| `resource.conflict` | HARD | structurel | tous | paires chevauchantes sur une même ressource |
| `resource.availability` | HARD | `TimeProfile.allowed` | tous | appartenance à un `BitSet` |
| `resource.discouraged_slot` | SOFT | `TimeProfile.discouraged` | tous | appartenance à un `BitSet` |
| `resource.consistency` | HARD | config | tous | équipes de ressources identiques par occurrence et rôle |
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
| `rest.min` | HARD | config | **DATED** | repos entre dates de début de travail, fins réelles après minuit |
| `work.days_max` | HARD | config | **DATED** | séries de dates calendaires occupées |
| `load.contract` | SOFT | config + `quota_hebdo_minutes` | tous | écart absolu à une cible individuelle par semaine, y compris vide |
| `fairness.weekend` | SOFT | config | **DATED** | carré du nombre de week-ends distincts occupés |

¹ `period = MONTH` est refusé en horizon `CYCLIC`.

---

## Détail

### `resource.conflict` — structurel

Une ressource ne peut pas être à deux endroits à la fois. **Cette contrainte pénalise les conflits dans tous les emplois du temps** — professeurs, classes,
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

Chaque occurrence est projetée en ensemble d'identifiants de ressources ; la
pénalité est le nombre d'équipes distinctes moins un. Deux postes du même rôle
peuvent donc recevoir `{A, B}` puis `{B, A}` sans violation. `{A, C}` forme une
autre équipe. L'agrégation est linéaire, sans comparer toutes les paires.

**Attention** : déclaré dur, il bloque tout pas intermédiaire de la recherche
locale vers une réaffectation. Voir `infeasibility-diagnoser`.

### `booking.unassigned` — structurel

Nécessaire dès lors que `allowsUnassigned = true` : sans lui, laisser tous les
postes vides serait la meilleure solution.

**Ce plugin utilise `forEachIncludingUnassigned`.** `forEach` exclut les
entités à variable nulle et le rendrait totalement muet.

### `load.max` / `load.min` — config

`Params(int maxMinutes, Period period, boolean deductAbsences)` /
`Params(int minMinutes, Period period, boolean deductAbsences)`.
Le drapeau optionnel vaut `false` pour préserver les configurations existantes.
En WEEK avec ce drapeau, les jours d'absence compilés dans `TimeProfile` créditent
`minutes_absence_jour`, dans la limite de `quota_hebdo_minutes`. Le même crédit
abaisse minimum et maximum sans consommer la marge supplémentaire. Ce réglage
est refusé pour les autres périodes ; une indisponibilité récurrente ne crédite rien.
Clé : `AggregationScope(resource, period, periodIndex)`. Collecteur `sum`.
Chaque poste contribue aux minutes réellement travaillées dans chaque panier,
via `Bookings.minutesIn` : une nuit est répartie entre ses dates, semaines ou
mois, et les pauses de la grille ne sont pas comptées comme du travail.

Le cumul des scopes est une **intersection**, pas une hiérarchie : un enseignant
vacataire est soumis aux deux quotas simultanément. Pour qu'une règle spécifique
remplace la générale, l'exprimer dans le scope avec un `not`.

`load.min` déclare **deux pipelines** : le principal, et `load.min.never_used` qui
part de `(règle, ressource, PeriodBucket)` avec `ifNotExists` pour attraper chaque
période à charge nulle. Une ressource travaillant lundi est donc encore pénalisée
si le plancher journalier manque mardi. Les paniers sont des faits compilés par
l'horizon ; ils représentent les périodes contenant au moins un créneau de la
grille. Une indisponibilité ne supprime pas le plancher configuré : pour une cible
contractuelle tenant compte d'une semaine entièrement indisponible, utiliser
`load.contract`.

### `load.balance` — config

Somme des carrés. Le volume total est fixe, imposé par la demande ; à somme
constante, la somme des carrés est minimale quand toutes les valeurs sont égales.
Pénaliser `charge²` équilibre donc automatiquement, sans jamais calculer de
moyenne, et sans supposer une cible connue d'avance.

Le carré est calculé en `long`, puis divisé par `1000` avant conversion contrôlée
vers le score entier. La division seule ne protégeait pas le produit initial,
qui débordait dès 46 341 minutes. Une pénalité hors capacité du score est refusée
au lieu de changer silencieusement de signe.

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

Pénalité : minutes réellement travaillées tombant hors de la fenêtre, calculées
créneau par créneau pour exclure les pauses, y compris en traversant minuit. C'est l'exemple
canonique de la frontière vivier/contrainte — placée en `placement.window`, la
règle deviendrait une borne dure et muette.

### `time.weekly_regularity` — config, CYCLIC

Nécessaire dès que `cycle_days > 7`. La séance de référence est la première dans
l'ordre des créneaux : le tri est déterministe et identique dans les deux
traversées, sans quoi le rapport désignerait d'autres séances que celles comptées
par le solveur.

---

## Règles pour les équipes et horaires datés

### `rest.min`

`Params(int minMinutes)`. Agrégation `(règle, ressource)`, puis journées définies
par la **date de début** du poste. Les postes démarrant le même jour forment une
journée ; un poste 22 h–6 h garde sa fin réelle le lendemain. Le manque est le
seuil moins le repos entre la dernière fin et le premier début de la journée
suivante, multiplié par le poids de règle. Aucun repos avant ou après l'horizon
n'est inventé. Niveau HARD par défaut car la durée minimale configurée exprime
une borne d'organisation ; la configuration de poids peut la rendre SOFT.

### `work.days_max`

`Params(int maxDays)`. Les dates de l'intervalle occupé semi-ouvert sont réunies
par ressource. Une nuit peut toucher deux dates ; une fin à minuit exclut le
lendemain. Chaque série contribue au nombre de dates excédant le maximum,
indépendamment du nombre de postes par date. L'historique hors horizon est
inconnu. Niveau HARD par défaut pour le maximum configuré, modifiable par poids.

### `load.contract`

`Params(boolean deductAbsences)`, optionnel et `false` par défaut, attribut entier
positif ou nul `quota_hebdo_minutes`. L'écart absolu
entre charge et cible est pénalisé par `(règle, ressource, semaine)` ; la semaine
vide est détectée par un pipeline auxiliaire `load.contract.empty_week`. Une
ressource sans quota est ignorée, un quota mal typé est signalé. Sans déduction,
une semaine sans aucun créneau permis pour la ressource n'a pas de cible ; les absences partielles
et semaines tronquées **ne sont pas proratisées**, car le nombre de jours
contractuels n'est pas connu. La charge de nuit est répartie entre les semaines
réellement travaillées. Les quotas différents restent donc individuels, contrairement à
`load.balance`. Niveau SOFT par défaut : une cible inaccessible peut être
expliquée sans rendre la résolution infaisable. Avec `deductAbsences:true`, la cible
est réduite selon les jours d'absence datés et `minutes_absence_jour`, plafonnée au
contrat. Une simple indisponibilité ne supprime plus la cible. La façade métier
associe ce souhait aux bornes HARD `load.min` et `load.max`.

### `fairness.weekend`

`Params()`. Chaque samedi ou dimanche réellement occupé, y compris après une
prise de poste vendredi soir, désigne sa semaine calendaire. Samedi et dimanche
du même week-end comptent une fois. Le carré du nombre de week-ends par ressource
encourage leur répartition à volume de couverture constant. Toutes les
contributions positives apparaissent au rapport, même pour une répartition
égale, comme dans `load.balance`. Ce plugin n'impose pas d'alternance stricte et
ne suppose aucun historique avant l'horizon. Niveau SOFT.

## Formes futures — non implémentées

| `type()` | Sémantique | Horizon |
|---|---|---|
| `coverage.min_role` | au moins un manager dans un groupe de postes génériques | tous |
| `fairness.rotation` | équité des gardes sur la durée | DATED |
| `event.date` | événement à date fixe | DATED |
| `room.stability` | même salle pour une série de séances | tous |
| `travel.building` | pénaliser les changements de bâtiment | tous |
| `precedence` | l'évaluation après le module | DATED |
