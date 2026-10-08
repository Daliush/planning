# CLAUDE.md — module `planning`

Moteur de planification paramétrable, jalon 2.

Trois documents, trois rôles :

| Fichier | Contenu |
|---|---|
| [`docs/SDD-moteur-planification.md`](docs/SDD-moteur-planification.md) | **La spécification de référence.** Les décisions, invariants et pièges du produit. C'est elle que citent les `§` de ce fichier et des skills |
| `README.md` | Les décisions d'implémentation et leurs raisons, y compris les écarts assumés à la spec |
| `CLAUDE.md` | Comment travailler dans ce module |

En cas de doute sur une intention de conception, **la spec fait foi**. En cas
d'écart entre la spec et le code, le `README.md` dit lequel a été choisi et
pourquoi — un écart non documenté est un bug.

---

## Règle d'autonomie — la première à respecter

**Ce module ne dépend d'aucun autre, et rien d'autre ne dépend de lui.** Il est
destiné à partir dans son propre dépôt : il suffira de copier le dossier
`planning/`.

Interdits absolus :

- importer quoi que ce soit de `backend/`, `frontend/` ou `service-auth/` ;
- lire ou écrire une table hors du schéma PostgreSQL `planning` ;
- ajouter une clé étrangère vers une table de Progression ;
- factoriser du code « commun » avec le reste du dépôt.

Si un besoin ressemble à du partage, dupliquer. Le couplage coûtera plus cher que
la duplication.

---

## Ce que ce produit est

Ce n'est pas une application CRUD. C'est un **problème d'optimisation sous
contraintes**, NP-difficile, dont la résolution automatique constitue l'essentiel
de la valeur et de la difficulté.

Trois idées gouvernent tout le code :

1. **Le solveur ne comprend rien.** Il remplit des cases avec des valeurs qu'on lui
   tend, et note le résultat. Toute la sémantique vit dans le builder et les
   plugins. *Une donnée sans contrainte pour la lire ne produit strictement rien.*
2. **Ce qui ne change pas pendant la résolution se compile.** Disponibilités en
   `BitSet`, critères en viviers, demandes en entités. Seule exception :
   `constraint_config`, qui reste un fait vivant joignable dynamiquement.
3. **Impossible structurel → vivier ; règle d'organisation → contrainte.** Le test :
   *quelqu'un pourrait-il un jour vouloir l'enfreindre ?* Une exclusion par vivier
   est rapide, définitive et **muette**.

---

## Stack et versions verrouillées

| | |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| Timefold Solver | **1.16.0**, édition Community |
| PostgreSQL | 16, schéma `planning` |
| Frontend | React 18 · TypeScript · Vite · Tailwind |

**Le SDD cite Timefold 2.x. Cette branche n'est pas publiée sur Maven Central.**
Avant d'utiliser une signature Timefold que tu n'as pas déjà vue dans ce code,
vérifie-la contre le jar :

```bash
docker run --rm -v "$PWD:/w" -w /w eclipse-temurin:21-jdk \
  javap -cp ~/.m2/repository/ai/timefold/solver/timefold-solver-core/1.16.0/timefold-solver-core-1.16.0.jar \
  ai.timefold.solver.core.api.score.stream.ConstraintFactory
```

Écrire d'après la documentation en ligne mène à des méthodes qui n'existent pas
dans cette version.

---

## Commandes

Aucun JDK n'est nécessaire sur la machine ; tout passe par Docker.

```bash
# Tests — à lancer après toute modification du domaine ou d'un plugin
docker run --rm -v "$PWD/planning/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test

# Base (depuis la racine du dépôt)
docker compose up -d postgres

# Backend
cd planning && PLANNING_DB_PASSWORD="$(cat ../secrets/postgres_password.txt)" docker compose up -d --build

# Frontend
cd planning/frontend && npm install && npm run dev     # http://localhost:5174
```

L'API écoute sur `http://localhost:8081`. Le tenant de démonstration est
`college-demo`, son scénario `Rentrée 2026`.

---

## Architecture — la règle de dépendance

```
api            → application, domain            jamais repository, Hibernate, Timefold
application    → domain, repository             la couche CRUD/orchestration
domain         → rien du projet                 jamais api, repository, JPA, HTTP
repository     → domain                         jamais api
```

`domain` ne porte **aucune annotation Spring** : ses classes sont instanciables
dans un test sans contexte ni base. C'est ce qui rend le `ProblemBuilder` testable
sans PostgreSQL, et ce n'est pas négociable.

Le domaine ne connaît que deux ports, dans `domain/port/` :
`ProblemSource` (lecture) et `SolveResultSink` (écriture du résultat).

### Cartographie

| Chemin | Contenu |
|---|---|
| `domain/model/` | `Timeslot`, `Effect`, `TimeProfile`, `Resource`, `ScheduledActivity`, `ResourceBooking`, `Schedule`, `ConstraintConfig`, `Violation`, `BusinessProfile` |
| `domain/attribute/` | `AttributeCatalog` — le vocabulaire métier qui génère les formulaires |
| `domain/criteria/` | Le moteur de critères et son parseur |
| `domain/time/` | `TimeHorizon`, `TimeGrid`, `GapPolicy`, `CycleBuckets` |
| `domain/placement/` | Stratégies `free`, `fixed`, `window` |
| `domain/constraint/` | `ConstraintPlugin`, le registre, les têtes de pipeline partagées |
| `domain/constraint/plugin/` | **Les 14 plugins du catalogue V1** |
| `domain/builder/` | `ProblemBuilder`, `AvailabilityCompiler`, `StartsCompiler`, `ProblemValidator`, `WeightBook` |
| `domain/solver/` | `SolverService`, `SolveJobRunner`, `ExplanationService` |
| `repository/entity/` | `Entities.java` — toutes les entités JPA, imbriquées |

---

## INVARIANT — aucune entité JPA ne franchit la couche domaine

Deux raisons, toutes deux graves :

1. Une relation `LAZY` touchée pendant la résolution déclencherait une requête SQL
   au milieu de la boucle chaude.
2. Les entités JPA sont **mutables**, or Timefold partage les faits du problème par
   référence entre tous les clones de solution. Une mutation corromprait
   silencieusement toutes les photos déjà prises, dont la meilleure solution.

`JpaProblemSource` produit des `record` immuables. Cette conversion n'est pas une
formalité, c'est une barrière de sécurité.

---

## Le test qui garde tout le dispositif

`ScoreConsistencyTest.lExplicationRedonneExactementLeScoreDuSolveur` vérifie que
la somme des violations rapportées, pondérées, redonne **exactement** le score du
solveur.

En édition Community, la couche d'explicabilité est écrite à la main : chaque
plugin expose deux traversées de la même règle. Ce test est la seule chose qui
empêche les deux de diverger — et il l'a déjà prouvé en attrapant un plugin
entièrement mort (voir plus bas).

**Un plugin sans ce test n'est pas terminé.** S'il tombe, ne le rattrape pas en
ajustant l'assertion : une des deux traversées est fausse.

---

## Pièges vérifiés dans ce code

Chacun a été rencontré, pas anticipé. Tous échouent **silencieusement**.

### `forEach` exclut les entités à variable nulle

Y compris quand `allowsUnassigned = true`. Écrire
`forEach(ResourceBooking.class).filter(b -> b.getResource() == null)` ne matche
jamais rien : laisser tous les postes vides devient gratuit, et la recherche locale
s'y engouffre — le solveur rend un planning **entièrement vide avec un score
parfait**. Utiliser `forEachIncludingUnassigned` pour raisonner sur l'absence
d'affectation.

### Lire la variable d'une autre entité exige une variable ombre

`ResourceBooking.start` est une `@ShadowVariable` alimentée depuis
`ScheduledActivity.start`. Déréférencer `booking.getActivity().getStart()`
compilerait et donnerait le bon résultat en apparence, mais Timefold ignorerait que
déplacer la séance invalide les tuples des bookings : le score incrémental
deviendrait faux. `environmentMode=FULL_ASSERT` fait tomber ce genre de bug
immédiatement — le test l'active déjà.

### Collecteurs des flux Bi et Tri

`count()` n'existe que sur un flux Uni. Sur un flux Bi c'est `countBi()`, sur un
Tri `countTri()`. De même `toList((a, b) -> …)` et `sum((a, b) -> …)` ont leurs
surcharges dédiées.

### La règle doit faire partie de la clé d'agrégation

Sans elle, deux lignes de `constraint_config` passant par le même plugin
mélangeraient leurs totaux. C'est le cas que le test « deux quotas concurrents sur
la même ressource » attrape.

### Un plugin peut produire plusieurs contraintes Timefold

Quand une règle doit aussi se déclencher sur une **absence** — les Constraint
Streams n'ayant pas de jointure externe — le plugin redéfinit `buildAll()` et
`constraintNames()`. `WeightBook` applique alors le poids ① du plugin à tous ses
pipelines, et leurs violations sont rapportées sous le même identifiant.
`load.min` est l'exemple de référence.

### Jamais `getReferenceById` sur les entités de ce module

Elles exposent des champs publics ; un proxy Hibernate n'intercepte que les appels
de méthode. Lire `job.scenarioId` sur un proxy renvoie `null`. Utiliser `findById`.

### Un attribut hors catalogue n'a aucun champ de saisie

`AttributeCatalog` génère le formulaire d'attributs des ressources. Un plugin qui
lit une clé absente du catalogue lit une clé que personne ne peut renseigner
autrement qu'en JSON brut : l'attribut reste vide sur toutes les ressources, la
règle ne se déclenche jamais, et **rien ne le signale**. D'où la déclaration
`ConstraintPlugin.readsAttributes()` et le test `AttributeCatalogTest`.

Le lien inverse — « qui lit cette clé ? » — est **calculé** par `CatalogService`
en parcourant le registre. Ne jamais en tenir une seconde liste.

### Un scope qui ne matche rien est muet

`{"eq":{"path":"/name","value":"Jean Dupont"}}` ne lève aucune erreur et ne vise
personne : le moteur de critères ne lit que `resource.attributes`, où le libellé
n'existe pas. Les clés disponibles sont celles des attributs, plus `/type` projeté
par le builder.

### Une contrainte dure peut bloquer la recherche, pas seulement le résultat

Observé : `resource.consistency` en dur empêche tout pas intermédiaire vers un
enseignant sous-chargé, parce que changer un seul poste d'un cours crée aussitôt
une violation dure et que le dur domine lexicographiquement. Le solveur reste
enfermé dans un optimum médiocre. C'est le couplage des variables du §8.8 — voir
le skill `infeasibility-diagnoser`.

---

## Conventions de code

- **Commentaires et messages en français**, comme le reste du dépôt. Les messages
  de violation s'adressent à un utilisateur métier : *« Jean Dupont dépasse son
  quota du mardi : 5 h 00 au lieu de 4 h 00 maximum »*, jamais
  *« load.max violated, delta=60 »*.
- **Les durées s'expriment en minutes**, jamais en créneaux. La grille est un
  paramètre modifiable ; un seuil en créneaux serait corrompu par un changement de
  granularité.
- **Aucun index de créneau en base.** Heures réelles et durées en minutes
  uniquement.
- `equals`/`hashCode` des clés d'agrégation reposent sur des identifiants, jamais
  sur un `JsonNode` — une comparaison profonde en boucle chaude est inacceptable.
- **Aucune valeur métier en dur dans un plugin.** Le mot « maths » n'apparaît nulle
  part dans le code : il vit dans le `scope` d'une ligne de configuration.
- Commits préfixés `feat`, `fix`, `docs`, `chore`.

---

## Skills disponibles

| Skill | Quand |
|---|---|
| `constraint-plugin-creator` | Ajouter ou modifier une règle de planification, un quota, une préférence, une interdiction — **même si le mot « plugin » n'est pas prononcé** |
| `infeasibility-diagnoser` | « Pas de solution », « le planning est vide », « le score ne bouge pas », un `solve_job` en `FAILED`, ou un planning aberrant |
| `weight-calibrator` | « Le planning privilégie trop X » |
| `criterion-operator-creator` | « Il me faut un critère qui… » |
| `business-profile-bootstrapper` | Ouvrir une verticale métier |

Agent `plugin-reviewer` : audit d'un plugin existant contre les invariants, en
revue de code ou après une montée de version de Timefold.

---

## Ce qui n'est pas fait

- **Authentification** — l'API est ouverte. Le multi-tenant est en place ; la
  sécurité se branchera dans `WebConfiguration` sans toucher au domaine.
- **Données réelles** — les ressources sont mockées par la migration `V2`. Le
  connecteur vers Progression reste à écrire.
- **Mode `Dated`** — l'interface `TimeHorizon` existe avec sa seule branche
  `Cyclic`. Jalon 4.
- **Publication, exports iCal, pinning, analyse de relaxation** — jalon 3.
