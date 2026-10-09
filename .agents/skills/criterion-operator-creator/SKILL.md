---
name: criterion-operator-creator
description: Ajouter ou modifier un opérateur de critères Planning quand un scope ou un critère d'éligibilité ne peut pas être exprimé avec les opérateurs existants. Couvre la forme JSON, le parseur, la sémantique et les tests.
---

# Étendre les critères

Travailler depuis la racine Planning. Le moteur de `backend/src/main/java/com/planning/domain/criteria/` sert `requirement.criteria` et `constraint_config.scope`. Lire `Criterion`, `CriterionParser`, `JsonValues` et leurs tests avant de modifier ce contrat.

Vérifier si `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `contains`, `containsAll`, `and`, `or`, `not` ou `{}` suffisent. Le moteur évalue les attributs au chargement ; une règle dépendant de l'affectation ou de l'heure relève d'un [plugin](../constraint-plugin-creator/SKILL.md).

## Implémenter

- Choisir un nom stable et une forme JSON cohérente avec les opérateurs voisins. `path` est un JSON Pointer. Préserver les configurations persistées ; une rupture nécessite une migration.
- Ajouter le record au `sealed interface Criterion`, puis réutiliser `JsonValues` pour les comparaisons. Les nombres se comparent par valeur numérique, pas par texte ; un tableau demande une recherche d'élément, pas `JsonNode.has()`.
- Ajouter le parseur dans `CriterionParser.OPERATORS`, avec les helpers existants de validation. `operators()` alimente le catalogue : éviter une seconde liste.
- Les critères restent purs. Un attribut absent ou mal typé ne lève pas d'exception d'évaluation. Préserver la convention de `neq` qui refuse l'attribut absent ; les combinateurs, notamment `not`, suivent leur propre sémantique existante.

Les critères lisent `attributes`, pas `label` ou `id`. `/type` est projeté par le builder. Si un nouveau champ fixe est nécessaire, examiner cette projection et le catalogue plutôt que contourner le contrat localement.

## Vérifier

Ajouter des cas nominal, négatif, absent, mal typé et JSON invalide au test pertinent. Tester une valeur numérique équivalente ou un tableau si concernés. Vérifier qu'un scope et un critère de vivier utilisent la nouvelle sémantique sans modifier leurs usages existants.

Lancer la validation backend de [validate-planning-change](../validate-planning-change/SKILL.md). Si l'éditeur visuel a un traitement explicite des opérateurs, vérifier aussi `frontend/src/features/constraints/scopeModel.ts`, `ScopeEditor.tsx` et le build frontend. Mettre à jour les [formes de flux et critères](../constraint-plugin-creator/references/constraint-streams-patterns.md) et le [README](../../../README.md) uniquement si le comportement public change.
