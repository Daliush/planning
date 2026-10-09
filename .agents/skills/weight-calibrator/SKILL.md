---
name: weight-calibrator
description: Ajuster les poids de contraintes Planning par résolutions comparables et mesures métier. Utiliser quand un planning privilégie trop un objectif, répartit mal la charge ou demande un arbitrage entre préférences existantes.
---

# Calibrer les poids

Travailler sur le scénario autorisé, de préférence une copie locale pour les expériences. Lire ses configurations et son rapport avant toute modification. Pour un parcours daté, reprendre les règles de contrats et couverture des [usages métier](../../../docs/USE-CASES-metiers.md).

## Identifier le levier

La pénalité vaut poids de contrainte (`constraint_weight`) × poids de règle (`constraint_config.weight`) × ampleur calculée par le plugin. Le premier règle l'importance d'un type, le second distingue ses scopes, le troisième décrit la violation. Un gradient incorrect demande une correction de plugin, pas un changement de poids.

Un poids nul désactive le type. `HardSoftScore` compare d'abord le dur puis le souple entre solutions ayant le même score dur ; même une solution avec violations dures conserve un score souple. Le niveau est résolu pour un type de plugin dans le scénario : deux configurations du même type ne créent pas à elles seules deux niveaux distincts.

Multiplier uniformément tous les poids d'un même niveau par une constante positive conserve l'ordre théorique des solutions, sous réserve des arrondis et débordements. Cela ne garantit ni la même trajectoire ni le même planning obtenu sous budget limité. Modifier seulement quelques poids change l'objectif.

## Comparer

1. Conserver les données, poids, job, budget et paramètres de résolution de référence. Lire les pénalités **et les mesures métier brutes** : deux scores souples sous des poids différents ne se comparent pas directement.
2. Vérifier qu'une règle sans effet a un poids non nul, un scope qui vise quelqu'un et un vivier offrant un choix. Pour un blocage, utiliser [infeasibility-diagnoser](../infeasibility-diagnoser/SKILL.md).
3. Changer un poids à la fois pour isoler son effet, ou un groupe justifié si l'objectif l'exige. Utiliser les routes existantes des poids/configurations avec des payloads typés ; conserver les valeurs précédentes.
4. Relancer avec le même budget, la même donnée et, si disponible, la même graine. Répéter seulement si la variabilité empêche de distinguer l'effet. Examiner couverture, contrats, repos, charge et préférences concernées.
5. Si le dur se dégrade, examiner la recherche, les scopes et les interactions avant d'attribuer la cause au seul budget. Une règle dure peut aussi rendre les mouvements intermédiaires défavorables ; ne pas assouplir une obligation métier sans décision autorisée.

Les ordres de grandeur sont des départs d'expérience, jamais une garantie. De grands ratios peuvent masquer des objectifs secondaires ; ils ne prouvent pas qu'un autre type de score est nécessaire. Un score à plusieurs niveaux est une évolution distincte si le métier demande une priorité lexicographique supplémentaire.

Présenter les poids retenus, les mesures avant/après et le coût de l'arbitrage. Signaler les essais non concluants sans annoncer un optimum garanti. Une modification du code passe par [validate-planning-change](../validate-planning-change/SKILL.md).
