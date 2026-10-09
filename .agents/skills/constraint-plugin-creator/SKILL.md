---
name: constraint-plugin-creator
description: Créer ou modifier une règle du moteur Planning, ses paramètres, son score et son explication. Utiliser pour un nouveau quota, une préférence, une interdiction, un équilibrage ou une évolution de plugin après avoir vérifié les possibilités de configuration existantes.
---

# Créer ou modifier une contrainte

Travailler depuis la racine Planning. Lire les invariants de [l'ingénierie](../../../docs/ENGINEERING.md) et, pour un parcours métier daté, les [usages et limites](../../../docs/USE-CASES-metiers.md). Le domaine Java vit dans `backend/src/main/java/com/planning/domain/`.

## Choisir la bonne extension

- Un besoin « N personnes à couvrir » crée des `demand` et `requirement`, généralement via `application/workforce` et `/api/workforce`.
- Une impossibilité structurelle indépendante de l'horaire se décrit par `requirement.criteria`. Une règle d'organisation que l'on pourrait enfreindre doit rester explicable dans un plugin.
- Lire le [catalogue](references/existing-plugins.md) et vérifier `ConstraintRegistry` avant d'ajouter une nouvelle forme de raisonnement. Un réglage existant suffit souvent.

Si du code est nécessaire, poursuivre dans le périmètre demandé ; ces distinctions ne sont pas des raisons de stopper un travail déjà autorisé.

## Contrats et paramètres

Conserver les identifiants existants. Un nouveau `type()` suit `domaine.regle` ; il devient un contrat des configurations, poids et rapports. Un renommage nécessite une migration explicite.

Déclarer `Params`, `paramsType()` et `paramSchema()`, puis les validations métier au chargement. Utiliser les minutes pour les durées. Les attributs lus directement doivent figurer dans `readsAttributes()` et `AttributeCatalog` ; le lien inverse est calculé par `CatalogService`. Déclarer `horizons()` pour une règle limitée à CYCLIC ou DATED, et refuser les combinaisons incompatibles dans `validate(config, horizon)`.

## Score et explication

Partir des [formes de flux](references/constraint-streams-patterns.md) et du [template configurable](assets/PluginTemplate.java.tpl), en les adaptant au raisonnement. Partager les gardes, la clé, le seuil et la formule entre `build()` et `validate(Schedule, ConstraintConfig)`.

La pénalité vaut poids de contrainte × poids de règle × ampleur. Le plugin calcule les deux derniers ; `WeightBook` applique le premier. Une clé d'agrégation comprend la règle pour garder deux configurations indépendantes. Pour une absence ou une période à charge nulle, prévoir les faits `Resource`/`PeriodBucket` et un pipeline auxiliaire ; déclarer tous les noms dans `constraintNames()`.

Réutiliser `TimeHorizon`, `TemporalBuckets`, `CycleBuckets` et `Bookings` selon le mode. Un quota utilise les minutes réellement travaillées par `PeriodBucket` : une nuit peut traverser une date, une semaine ou un mois. Les messages utilisent `solution.getHorizon().labelOf(...)`. Éviter de regrouper toute la charge sur la seule date de début.

Les lambdas restent pures. Lire la variable ombre `booking.getStart()` dans les flux. Pour raisonner sur les entités non affectées, utiliser `forEachIncludingUnassigned`. Vérifier toute signature Timefold nouvelle contre la version de `backend/pom.xml` ou son jar, plutôt qu'une documentation d'une autre version.

Une violation fournit un message métier français, les identifiants impliqués et les valeurs brutes dans `context`. Conserver la même sémantique d'identifiant que les plugins voisins.

## Enregistrement et validation

Enregistrer dans `ConstraintRegistry.STANDARD`. Adapter le [template de test](assets/PluginTest.java.tpl) : cas respecté, violation exacte, scopes exclus, deux configurations concurrentes et frontières temporelles pertinentes. Ajouter la configuration au fixture utilisé par `ScoreConsistencyTest` ou `WorkforceScoreConsistencyTest` selon le mode pour comparer score et rapport ; `FULL_ASSERT` vérifie le score incrémental. Ne pas assouplir l'assertion pour masquer une divergence.

Utiliser [validate-planning-change](../validate-planning-change/SKILL.md), avec validation backend pour un plugin et contrats métier si le parcours workforce change. Toute évolution de données de démonstration passe par une nouvelle migration, via [manage-planning-migrations](../manage-planning-migrations/SKILL.md). Mettre à jour le catalogue et les documents de comportement concernés.
