# Planning

Monorepo autonome de planification : backend Java/Timefold et interface React,
avec parcours restaurant, entreprise et console scolaire/experte conservée.
La base PostgreSQL, les services, les procédures d'agent et la validation sont
propres à ce dépôt. Aucun fichier ni service d'un autre projet n'est nécessaire.

```text
backend/          Java 21 · Spring Boot 3.3.5 · Timefold 1.16.0
frontend/         React 18 · TypeScript · Vite · Tailwind
scripts/          Démarrage et validation PowerShell
.agents/skills/   Skills Codex versionnés
.codex/agents/    Rôles de revue Codex
docs/             Architecture, règles métier et preuves
docker-compose.yml PostgreSQL 16 · API · interface
```

## Démarrer

Installer Docker avec Compose. Depuis cette racine, sous PowerShell :

```powershell
./scripts/dev.ps1 -Action start
./scripts/dev.ps1 -Action health
```

Le script prépare `.env` depuis `.env.example` s'il manque. Cette configuration
locale reste ignorée par Git. Interface sur `http://localhost:5174`, API sur
`http://localhost:8081`, PostgreSQL local sur le port `5435`. La base et son volume
sont dédiés à Planning. Le frontend Docker redirige `/api` vers le backend.

```powershell
./scripts/dev.ps1 -Action status
./scripts/dev.ps1 -Action stop
```

L'arrêt conserve les données. Les paramètres de ports et de nom de projet
permettent une seconde instance isolée ; consulter `Get-Help ./scripts/dev.ps1`.
Ne pas réutiliser un volume existant pour une validation destructive.

Avec Node.js 22 et npm installés, utiliser le serveur de développement
React pour les changements d'interface :

```powershell
./scripts/dev.ps1 -Action start
docker compose stop planning-web
npm --prefix ./frontend ci
npm --prefix ./frontend run dev -- --host 127.0.0.1
```

Ou utiliser Vite dans Docker, avec rechargement automatique des sources frontend
et sans installation Node sur l'hôte :

```powershell
./scripts/dev.ps1 -Action start -Mode dev -SeedDemo
./scripts/dev.ps1 -Action health -Mode dev
./scripts/dev.ps1 -Action stop -Mode dev
```

`-SeedDemo` charge trois [démonstrations réalistes](docs/DEMO-realiste.md) pour le
prochain lundi : restaurant et entreprise réalisables, puis restaurant avec deux
tensions identifiées. Les contrats sont atteignables, les absences déduites et les
suppléments plafonnés. Ajouter `-DemoWeekStart 2026-10-12` pour choisir une semaine.
Le [SQL](database/seeds/workforce-realistic.sql) préserve les données déjà présentes.
Sur une stack en cours, `./scripts/seed-demo.ps1` charge uniquement les données ;
`./scripts/validate-demo.ps1` calcule et vérifie les trois exemples.

Le mode `dev` conserve la même API et la même base ; seule l'interface passe sous
Vite. Reconstruire l'image après une modification backend. `-Mode built`, le mode
par défaut, revient à l'interface compilée servie par nginx.

Vite utilise l'API locale sur `8081` ; `PLANNING_API_URL` peut choisir une autre
adresse. L'exécution directe du backend utilise les variables `PLANNING_DB_HOST`,
`PLANNING_DB_PORT`, `PLANNING_DB_NAME`, `PLANNING_DB_USER` et `PLANNING_DB_PASSWORD`.
Les valeurs nécessaires sont dans `.env` ; Maven ne charge pas ce fichier
automatiquement. Le backend Docker reste disponible sans JDK ni Maven local.

Flyway applique les migrations `V1` à `V8` dans le schéma `planning`. Les migrations
appliquées ne sont pas réécrites. `V2` conserve le collège de démonstration ; les
exemples restaurant et entreprise se créent à la demande. `V7` et `V8` adaptent
uniquement les configurations métier reconnues et préservent les réglages experts.
Les politiques de contrats et absences sont décrites dans
[les usages métier](docs/USE-CASES-metiers.md).

## Valider une modification

```powershell
./scripts/validate.ps1
```

La validation requiert Docker et Node.js 22/npm ; Java 21 est fourni par Docker. Les tests Java peuvent aussi être lancés avec Maven local. Pour une évolution
métier, compléter par les deux parcours HTTP sur une base locale ou de test :

```powershell
./scripts/validate-workforce.ps1 -BaseUrl http://localhost:8081
./scripts/validate-contracts.ps1 -BaseUrl http://localhost:8081
```

Ces scripts créent des espaces de données fictives. Pour une UI, effectuer aussi
une vérification dans le navigateur sur ordinateur et mobile. La CI du dépôt
exécute les tests backend et le build frontend sur un checkout neuf.

## Travailler avec Codex

Ouvrir **ce dépôt** comme projet pour charger [AGENTS.md](AGENTS.md), les skills
de `.agents/skills` et les rôles `.codex/agents`. Les procédures essentielles
ne dépendent d'aucun skill personnel. [CLAUDE.md](CLAUDE.md) fournit l'entrée
compatible Claude vers les mêmes instructions.

Pour vérifier la découverte réelle des skills avec Codex installé :

```powershell
node ./scripts/check-codex.mjs
```

Ce contrôle interroge le serveur local de Codex, sans lancer de modèle ni créer de
conversation. Les trois rôles sont au [format natif Codex](https://learn.chatgpt.com/docs/agent-configuration/subagents), avec modèle et permissions hérités de la session. Si une version de l'outil ne charge pas les rôles nativement, AGENTS.md décrit le recours à leurs instructions.

Les renvois en § de la documentation technique désignent le
[SDD](docs/SDD-moteur-planification.md). Les écarts assumés sont décrits ci-dessous.

---

## Ce que fait l'application

L'accueil propose **Restaurant**, **Entreprise** et **Scolaire / Expert**.
Les espaces restaurant et entreprise se créent vides ou avec une démonstration
identifiée. Les noms, compétences, besoins et absences saisis sont enregistrés
dans PostgreSQL. Le parcours scolaire reste cyclique et conserve ses écrans,
son collège de démonstration et ses liens historiques.

| Écran | Ce qu'on y règle |
|---|---|
| **Restaurant** (`/restaurant`) | Services du midi ou du soir, rôles de cuisine et de salle, couverture et affectations. |
| **Entreprise** (`/entreprise`) | Plages de travail, besoins d'équipe, horaires par employé et charge prévue. |
| **Équipe / Absences / Organisation** | Compétences, contrat hebdomadaire, indisponibilités récurrentes, congés ou maladie datés, repos et charge maximale. |
| **Référentiel** | Les ressources et leurs disponibilités. Les qualifications et la capacité vivent dans `attributes`, lus par les critères d'éligibilité. |
| **Demandes** | Ce qu'il faut planifier, et les postes à pourvoir sur chaque séance. |
| **Règles** | Le poids ① de chaque contrainte, et les lignes de configuration avec leur scope, leurs paramètres et leur poids ②. |
| **Résolution** | Lancement, suivi du job, grille de planning filtrable par ressource, et rapport de violations nommées. |

La console générique reste accessible sous `/expert`. Le catalogue des dix-huit
plugins, les opérateurs de critères et les stratégies de placement sont exposés
par `GET /api/catalog`. Les formulaires métier traduisent les réglages usuels
vers cette même API et ce même moteur.

Les usages et leur traduction technique sont détaillés dans
[`docs/USE-CASES-metiers.md`](docs/USE-CASES-metiers.md).

### API métier

Les routes suivantes sont préfixées par `/api/workforce`. `{id}` est l'identifiant
du scénario retourné lors de la création de l'espace.

| Méthode et route | Usage |
|---|---|
| `GET /workspaces?profile=RESTAURANT` ou `COMPANY` | Retrouver les espaces enregistrés. |
| `POST /workspaces` | Créer un établissement et son planning daté ; `demo:true` ajoute des exemples identifiés. |
| `GET /scenarios/{id}/overview` | Équipe, période, services, absences, règles, dernier calcul et indicateurs de couverture. |
| `POST /scenarios/{id}/employees` | Créer un employé, son contrat et ses disponibilités dans une transaction. |
| `PUT` / `DELETE /scenarios/{id}/employees/{employeeId}` | Modifier ou supprimer cet employé. |
| `POST /scenarios/{id}/shifts` | Créer un service et tous ses postes dans une transaction. |
| `PUT` / `DELETE /scenarios/{id}/shifts/{shiftId}` | Modifier ou supprimer ce service. |
| `GET` / `POST /scenarios/{id}/absences` | Consulter ou enregistrer les absences datées. |
| `DELETE /scenarios/{id}/absences/{absenceId}` | Retirer une absence. |
| `PUT /scenarios/{id}/policy` | Régler le repos, la charge quotidienne et l'équilibre des volumes. |

Le calcul conserve les routes communes : `POST /api/scenarios/{id}/solve-jobs`,
puis `GET /api/solve-jobs/{jobId}` et `/report`, `/assignments` ou `/schedule`.
Une mutation rend le planning courant obsolète ; la vue métier masque les anciennes
affectations et demande un nouveau calcul. Les mutations et les départs de calcul
verrouillent le scénario ; un calcul déjà actif interdit un second départ.

### Règles ajoutées

| Plugin | Fonction | Niveau métier par défaut |
|---|---|---|
| `rest.min` | Repos entre journées regroupées par date de début ; fin réelle d'un poste de nuit. | HARD, repos configuré à 660 min. |
| `work.days_max` | Maximum de jours calendaires travaillés consécutifs, nuits comprises. | HARD, 6 jours. |
| `load.contract` | Écart à la cible hebdomadaire individuelle `quota_hebdo_minutes`. | SOFT. |
| `fairness.weekend` | Répartition des week-ends travaillés sur la période. | SOFT. |

Les nouveaux espaces activent aussi les règles structurelles, `load.max` par jour,
les bornes hebdomadaires HARD `load.min` et `load.max`, et `load.balance`. Le minimum
est le contrat diminué du crédit d'absence ; le maximum ajoute les heures
supplémentaires autorisées par l'employé. Les autres poids sont posés
explicitement à zéro. Les durées de nuit sont ventilées dans les jours et les
semaines réellement occupés. Les limites précises figurent dans la fiche d'usages.

Dans ces parcours, un poste vacant et le déficit contractuel ont un poids de 1
au niveau HARD ; disponibilité, conflit, repos, jours consécutifs et plafonds ont
un poids de 10000. Le moteur
préfère donc signaler un besoin non couvert plutôt que forcer une affectation
contraire à ces règles. La préférence pour la cible contractuelle et l'équité
restent SOFT, en complément des bornes strictes. Un déficit de contrat rend le
résultat infaisable et doit être signalé.

---

## Décisions d'implémentation

Le SDD fixe l'essentiel. Ce qui suit relève des choix laissés ouverts, ou d'écarts
assumés, chacun avec sa raison.

### Timefold verrouillé à 1.16.0

Le SDD cite la branche 2.x et prévient qu'il faut vérifier les signatures contre
la version verrouillée dans le `pom.xml`. L'implémentation et ses tests utilisent
1.16.0 ; une montée de version demanderait une validation dédiée. Les deux points sensibles
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

La logique temporelle vit dans deux modules purs : `CycleBuckets` pour le cycle
et `TemporalBuckets` pour les dates. `TimeHorizon.Cyclic` et `TimeHorizon.Dated`
y délèguent. Les templates d'un horizon daté décrivent les jours de semaine
(0 = lundi) ; le builder les projette sur les dates de la période. Les exceptions
d'absence sont appliquées après les disponibilités récurrentes et ne peuvent pas
être rouvertes par une priorité de disponibilité.

### Tous les plugins sont enregistrés, la compatibilité est validée au chargement

Le §11.2 filtre les plugins par mode d'horizon dans `defineConstraints()`. Ce
filtrage rendrait une ligne de configuration **muette** au lieu de la refuser — ce
que le §15 contrôle 7 interdit explicitement. Les dix-huit plugins sont donc
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

Une ressource sans aucune affectation sur une période n'apparaît dans aucun panier d'agrégation :
le pipeline principal, qui part des affectations, ne peut structurellement pas la
voir. Or c'est exactement le cas que la règle existe pour attraper — un enseignant
à zéro heure.

Les Constraint Streams n'offrant pas de jointure externe, `load.min` déclare un
second pipeline qui part des **couples ressource/période** et retient, via
`ifNotExists`, ceux qu'aucune affectation ne mentionne.

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
- **Déploiement** — le Compose autonome sert au développement local. Un déploiement
  public et ses contrôles de sécurité restent à concevoir dans ce dépôt.
- **Connecteurs de données** — la saisie métier est persistante, mais aucun
  connecteur vers un SIRH ou une caisse de restaurant n'est livré.
  Le collège `V2` et les espaces marqués « Démonstration » restent des exemples.
- **Paie et règles sectorielles** — les réglages d'organisation ne calculent pas
  la paie et ne vérifient pas automatiquement une convention collective.
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
