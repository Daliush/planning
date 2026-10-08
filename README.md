# Module `planning` — moteur de planification paramétrable

Implémentation de [`docs/SDD-moteur-planification.md`](docs/SDD-moteur-planification.md),
jalon 2 (architecture générique complète). Les renvois en `§` de ce document et des
skills désignent les sections de cette spécification.

**Ce module est autonome.** Il ne partage aucun code, aucune dépendance et aucune
table avec le reste de Progression. Il vit dans son propre schéma PostgreSQL
`planning`, et peut être déplacé tel quel dans son propre dépôt : il suffit de
copier le dossier.

```
planning/
├── backend/          Java 21 · Spring Boot 3.3.5 · Timefold 1.16.0 · PostgreSQL
├── frontend/         React 18 · TypeScript · Vite · Tailwind
└── docker-compose.yml
```

---

## Démarrer

### 1. La base

Le module réutilise l'instance PostgreSQL de Progression, dans un schéma séparé.
Depuis la racine du dépôt :

```bash
docker compose up -d postgres
```

Flyway crée le schéma `planning`, ses quatorze tables et un jeu de données mocké
(un collège de démonstration) au premier démarrage du backend.

### 2. Le backend

Aucun JDK n'est nécessaire sur la machine : tout passe par Docker.

```bash
cd planning
PLANNING_DB_PASSWORD="$(cat ../secrets/postgres_password.txt)" docker compose up --build
```

L'API écoute sur `http://localhost:8081`.

Avec un JDK 21 et Maven installés localement, l'équivalent direct est :

```bash
cd planning/backend
PLANNING_DB_PASSWORD=... mvn spring-boot:run
```

### 3. Le frontend

```bash
cd planning/frontend
npm install
npm run dev
```

Interface sur `http://localhost:5174`. Le proxy Vite redirige `/api` vers le
backend, il n'y a rien à configurer.

### Les tests

```bash
cd planning/backend
mvn test
```

ou, sans JDK local :

```bash
docker run --rm -v "$PWD:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

---

## Ce que fait l'application

| Écran | Ce qu'on y règle |
|---|---|
| **Référentiel** | Les ressources et leurs disponibilités. Les qualifications et la capacité vivent dans `attributes`, lus par les critères d'éligibilité. |
| **Demandes** | Ce qu'il faut planifier, et les postes à pourvoir sur chaque séance. |
| **Règles** | Le poids ① de chaque contrainte, et les lignes de configuration avec leur scope, leurs paramètres et leur poids ②. |
| **Résolution** | Lancement, suivi du job, grille de planning filtrable par ressource, et rapport de violations nommées. |

Le catalogue des quatorze plugins, les opérateurs de critères et les stratégies de
placement sont exposés par `GET /api/catalog` : le frontend ne code aucun nom de
contrainte en dur.

---

## Documentation agentique

`CLAUDE.md` est le point d'entrée pour tout agent qui modifie ce module :
architecture, invariants, commandes et pièges vérifiés.

`.claude/skills/` contient les procédures des gestes répétitifs — ajouter un
plugin, diagnostiquer une infaisabilité, calibrer les poids, ajouter un opérateur
de critère, amorcer une verticale. `.claude/agents/plugin-reviewer.md` audite un
plugin existant contre les invariants.

Ces fichiers vivent sous `planning/` et partiront avec le module.

---

## Décisions d'implémentation

Le SDD fixe l'essentiel. Ce qui suit relève des choix laissés ouverts, ou d'écarts
assumés, chacun avec sa raison.

### Timefold 1.16.0, et non 2.x

Le SDD cite la branche 2.x et prévient qu'il faut vérifier les signatures contre
la version verrouillée dans le `pom.xml`. **La 2.x n'est pas publiée sur Maven
Central** : 1.16.0 est la dernière version disponible. Les deux points sensibles
ont été vérifiés directement dans le jar :

- `@PlanningVariable.allowsUnassigned` existe bien (`nullable` subsiste, déprécié) ;
- `ConstraintWeightOverrides.of(Map<String, Score>)` existe et est détecté par le
  type du champ sur `@PlanningSolution`, sans annotation.

L'édition reste **Community** (ADR-002), et la couche d'explicabilité est
construite en interne (ADR-003) — elle ne dépend donc d'aucune édition, ce qui
rend le choix de version indifférent sur ce plan.

### Une variable ombre sur `ResourceBooking`

Le SDD décrit `ResourceBooking.startIndex()` comme un simple déréférencement de
`activity.start()`. **Écrit ainsi, le score incrémental devient faux.** Timefold ne
sait pas qu'un booking dépend du créneau d'une *autre* entité : déplacer la séance
laisserait les tuples des bookings intacts.

`ResourceBooking.start` est donc une `@ShadowVariable` alimentée par
`BookingStartListener` depuis `ScheduledActivity.start`. La dépendance devient
visible par le réseau de propagation. Le test `leScoreIncrementalResisteAuRecalculComplet`
tourne en `FULL_ASSERT` et fait tomber immédiatement toute régression sur ce point.

### `forEachIncludingUnassigned` pour `booking.unassigned`

`forEach(Class)` **exclut les entités dont la variable de planification est
nulle**, y compris quand `allowsUnassigned = true`. Écrite avec `forEach`, la
contrainte qui pénalise les postes vides ne matche jamais rien : laisser tous les
postes vides devient gratuit, et la recherche locale s'y engouffre — le solveur
rend un planning entièrement vide avec un score parfait.

C'est exactement ce qui est arrivé au premier passage, et c'est le test de
cohérence du §16.5 qui l'a attrapé : le solveur annonçait `0hard/0soft` là où
l'explication comptait 18 violations. `forEachIncludingUnassigned` est obligatoire
ici, et uniquement ici.

### Jamais `getReferenceById` sur ces entités

Les entités exposent des champs publics. `getReferenceById` rend un proxy
Hibernate, qui n'intercepte que les appels de méthode : une lecture directe de
`job.scenarioId` renvoie le champ du proxy, resté nul. Le worker doit charger
l'entité pour de vrai, avec `findById`.

### Le worker démarre après le commit

`SolveService.submit` enregistre le job dans une transaction. Soumettre le worker
à l'exécuteur dans la foulée le ferait interroger une ligne pas encore visible.
Une synchronisation `afterCommit` garantit l'ordre.

Corollaire : les workers vivent en mémoire, donc aucun job ne survit à un
redémarrage. `OrphanJobRecovery` solde au démarrage les jobs restés `QUEUED` ou
`RUNNING`, sans quoi l'interface croirait une résolution éternellement en cours et
refuserait d'en lancer une autre.

### `CycleBuckets` — le module temporel des plugins

L'INVARIANT n° 10 exige qu'aucune notion de date ou de cycle ne soit manipulée
hors de `TimeHorizon`. Or les plugins ne peuvent pas porter d'instance de
`TimeHorizon` : le `ConstraintProvider` est instancié par Timefold, hors du
contexte Spring, avec un constructeur sans argument.

La logique temporelle vit donc dans `CycleBuckets`, un module de fonctions pures
prenant un `Timeslot`. `TimeHorizon.Cyclic` y délègue. La règle n'existe qu'à un
seul endroit, et l'ajout du mode daté consistera à écrire la classe jumelle plutôt
qu'à disséminer des `if`.

### Tous les plugins sont enregistrés, la compatibilité est validée au chargement

Le §11.2 filtre les plugins par mode d'horizon dans `defineConstraints()`. Ce
filtrage rendrait une ligne de configuration **muette** au lieu de la refuser — ce
que le §15 contrôle 7 interdit explicitement. Les quatorze plugins sont donc
toujours enregistrés ; `horizons()` sert de filtre d'affichage pour l'interface
d'administration, et `ProblemValidator` refuse bruyamment toute configuration
incompatible.

### Une ligne de configuration synthétique par plugin structurel

`resource.conflict`, `resource.availability`, `resource.discouraged_slot` et
`booking.unassigned` ne lisent aucune ligne de `constraint_config`. Le builder leur
en fabrique une malgré tout, de scope `Always` et de poids ② 1.

Elle n'a aucun effet sur le solveur — ces plugins ne joignent jamais
`ConstraintConfig` — mais elle rend la traversée d'explicabilité uniforme. Sans
elle, les violations structurelles compteraient dans le score sans apparaître dans
le rapport, et le test de cohérence du §16.5 échouerait.

### Grille : durée de créneau uniforme, intervalles libres

`TimeGrid` exige que tous les créneaux d'un modèle temporel aient la **même
durée**. Les intervalles entre eux restent libres : récréations, pause déjeuner,
journée raccourcie le mercredi — c'est-à-dire exactement les cas qui motivent la
table `slot_template` au §6.7.

Une grille à durées mélangées rendrait `durationSlots` dépendant de la position de
départ, donc non calculable une fois pour toutes. L'exception explicite vaut mieux
qu'un planning silencieusement faux.

### `enabled` est compilé, pas joint

`constraint_config.enabled` ne dépend d'aucune variable de planification : le
critère du §2.3 dit donc de le compiler. Le builder ne charge que les lignes
actives, ce qui supprime un `filter` en boucle chaude et, surtout, tout risque de
divergence entre les deux traversées.

### `load.min` — deux pipelines pour une seule règle

Une ressource sans aucune affectation n'apparaît dans aucun panier d'agrégation :
le pipeline principal, qui part des affectations, ne peut structurellement pas la
voir. Or c'est exactement le cas que la règle existe pour attraper — un enseignant
à zéro heure.

Les Constraint Streams n'offrant pas de jointure externe, `load.min` déclare un
second pipeline qui part des **ressources** et retient, via `ifNotExists`, celles
qu'aucune affectation ne mentionne.

Pour que les deux partagent le poids ① sans casser le contrat « un plugin, un
`constraint_weight` », l'interface expose `buildAll()` et `constraintNames()` :
un plugin peut produire plusieurs contraintes Timefold, `WeightBook` leur applique
le poids de leur propriétaire, et leurs violations sont rapportées sous le même
identifiant. Ce qui se dédouble est la traversée, jamais la règle — et le test de
cohérence du §16.5 continue de le vérifier.

### Le couplage des variables, observé

Le §8.8 décrit un risque et tranche : *ne rien faire, mesurer sur données réelles,
et ne sortir l'artillerie que si la stagnation est observée*. Elle l'est.

Sur le jeu mocké, `load.min` détecte et nomme correctement un vacataire à zéro
heure, mais le solveur ne lui donne pas de travail pour autant. La cause est
mécanique : `resource.consistency` étant **dure**, changer l'enseignant d'un seul
des quatre postes d'un cours crée immédiatement une violation dure, et le niveau
dur domine lexicographiquement. Aucun premier pas vers le vacataire n'est
acceptable, alors que l'arrivée est bien meilleure.

Vérifié en basculant `resource.consistency` en souple : le vacataire prend
aussitôt ses quatre séances, sur ses seuls jours de disponibilité, et le score
souple passe de −17 750 à −8 450 — la continuité restant de fait respectée. La
contrainte ne protégeait pas le résultat, elle bloquait la recherche.

Trois parades, par coût croissant : basculer `resource.consistency` en souple avec
un poids élevé ; modéliser le besoin en demande dédiée (§18.3) ; ou implémenter le
`cartesianProductMoveSelector` restreint du §8.8.

### Une couche `application` entre `api` et `repository`

Le SDD place les services CRUD dans `domain/service`. Les y mettre imposerait un
port par entité — quatorze interfaces pour de la lecture-écriture sans métier.

Le domaine reste donc strictement pur (modèle, critères, temps, placement,
contraintes, builder, solveur) et ne connaît que deux ports : `ProblemSource` et
`SolveResultSink`. Les services CRUD vivent dans `com.planning.application`, qui
dépend de `repository` et de `domain`. La règle de dépendance du §4.3 est
respectée — `api` ne voit jamais `repository`, `domain` ne voit rien du projet.

### Le vocabulaire métier vit dans le code, pas en base

`resource.attributes` est un jsonb libre, et le reste. Mais laisser un utilisateur
métier le saisir à la main était une faute d'interface : personne ne devrait écrire
`{"matieres":["maths"]}` pour dire qu'un enseignant fait des maths.

`AttributeCatalog` déclare les clés que le produit connaît — leur libellé, leur
forme, la verticale où elles s'appliquent — et le frontend en génère un formulaire.
L'éditeur JSON reste accessible, replié : c'est le seul moyen de poser une clé hors
catalogue, et il ne doit jamais disparaître.

**Pourquoi pas une table.** Le catalogue est du vocabulaire, et « le code fournit
un langage, la base contient les phrases » (§2.1). En base, ajouter un mot
demanderait une migration, et surtout rien n'empêcherait un plugin de lire une clé
que le catalogue ignore. Dans le code, la déclaration se relit en revue au même
endroit que le plugin, et `AttributeCatalogTest` fait tomber le build sur toute
lecture non déclarée.

Le lien « qui lit tel attribut » n'est **jamais saisi** : `readsAttributes()` est la
seule déclaration, `CatalogService` calcule l'inverse. Deux listes finiraient par
diverger, et un catalogue qui ment sur ce qui lit un attribut est pire qu'aucun
catalogue.

### `scenario.profile` ne filtre qu'une interface

Le profil d'un scénario — école, entreprise, restauration, ou aucun — choisit le
vocabulaire d'attributs proposé, et rien d'autre. Aucun plugin, aucun critère et
aucune contrainte ne le lit ; `NULL` signifie « tout proposer », pour qui veut poser
un attribut d'entreprise sur une école.

Le faire lire par le noyau aurait introduit une valeur métier en dur dans le moteur,
exactement ce que le §2.6 interdit — et rouvert la porte au `if (profil == SCOLAIRE)`
que toute l'architecture existe pour éviter.

### Le rapport est figé à la fin du job

`solve_job.report` porte le `SolveReport` sérialisé. Le recalculer à la demande
imposerait de reconstruire le problème et d'y réappliquer les affectations, et
donnerait un rapport dépendant de l'état *courant* du référentiel — pas de celui
qui a produit ce planning-là.

---

## Ce qui n'est pas fait

Conformément au périmètre convenu :

- **Authentification** — l'API est ouverte. Le multi-tenant est en place
  (`tenant_id`, `scenario_id`), la sécurité se branchera dans `WebConfiguration`
  sans toucher au domaine.
- **Déploiement** — le `docker-compose.yml` de ce dossier sert au développement
  local. L'intégration au déploiement des autres applications viendra plus tard.
- **Données réelles** — les ressources sont mockées (migration `V2`). Le
  connecteur vers les données de Progression reste à écrire ; il alimentera
  `resource`, `resource_availability` et `demand`.
- **Mode `Dated`** — l'interface `TimeHorizon` existe avec sa seule branche
  `Cyclic`, comme prévu par l'ADR-006. Jalon 4.
- **Analyse de relaxation** (§16.7), **publication et exports iCal**, **pinning et
  `ProblemChange`** — jalon 3.

---

## Le jeu de données mocké

Un collège de démonstration, créé par `V2__seed_mock_school.sql` :

- 5 jours, 8 créneaux d'une heure, deux récréations de 15 min et une pause
  déjeuner de 75 min — grille volontairement irrégulière ;
- 11 enseignants, 6 salles, 4 groupes ;
- Jean Dupont, vacataire, n'est disponible que le mardi et le mercredi
  (l'exemple de référence du SDD) ; la salle B12 ferme le vendredi après-midi ;
  les groupes n'ont pas cours le mercredi après-midi ;
- 24 demandes, 64 séances par cycle, 3 postes chacune — 192 affectations à décider ;
- 13 règles configurées et les 14 poids.

Le tenant de démonstration porte le code `college-demo`. Le seed est idempotent :
il ne fait rien si ce tenant existe déjà.
