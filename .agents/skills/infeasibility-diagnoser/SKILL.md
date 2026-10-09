---
name: infeasibility-diagnoser
description: Diagnostiquer un solve_job Planning en échec, un planning vide ou partiel, des violations dures, un score stagnant ou une règle sans effet. Distinguer erreurs de données, configuration, recherche et code avant de corriger.
---

# Diagnostiquer la résolution

Travailler depuis la racine Planning. Lire les [invariants](../../../docs/ENGINEERING.md) et les [usages métier](../../../docs/USE-CASES-metiers.md) correspondant au scénario. Suivre les pistes qui concordent avec les faits ; ne pas transformer une règle d'organisation en filtre muet de vivier pour faire disparaître son explication.

## Collecter les faits

Récupérer `GET /api/solve-jobs/{jobId}`, puis `/report`, les données du scénario, son mode temporel et ses poids. Vérifier le statut, `errorCode`, le budget, les affectations, les postes vacants et les violations. Utiliser [run-planning-locally](../run-planning-locally/SKILL.md) pour les commandes runtime et les logs du service API ; chercher les tailles de vivier et les départs rares signalés par le builder.

Les erreurs `INFEASIBLE_EMPTY_POOL`, `INFEASIBLE_NO_START`, `INCOMPATIBLE_CONFIG`, `INCOMPATIBLE_TIME_MODEL` et `UNKNOWN_CONSTRAINT` orientent respectivement vers viviers, départs, compatibilité, grille et registre. Vérifier les codes réellement retournés par l'API et lire le message métier. `INTERRUPTED` demande de vérifier le redémarrage avant de relancer.

## Viviers et départs

- Vérifier les ressources du bon type, les attributs, leurs types JSON et le critère exact. `contains` recherche un élément de liste ; `eq` ne remplace pas cette recherche. Les critères ne lisent pas les libellés fixes ; `/type` est projeté par le builder.
- DATED conserve les viviers vides pour afficher les postes non couverts ; un job terminé ne signifie donc pas couverture complète. CYCLIC peut refuser un vivier vide au chargement.
- Vérifier durée, pas de grille, plage d'ouverture, `GapPolicy`, stratégie de placement et bornes d'horizon. Une coupure est acceptée selon sa durée et la politique configurée, pas selon son nom « déjeuner ».
- Le mode daté accepte les activités après minuit. Vérifier leur fin réelle, les créneaux du lendemain, les absences sur cette portion et la fin de période. CYCLIC conserve ses propres limites de compilation. Examiner `StartsCompiler`, `TimeGrid` et les helpers temporels plutôt qu'appliquer une interdiction générale de nuit.

## Violations ou stagnation

Lire les pénalités par contrainte et les valeurs brutes. Pour `resource.conflict` ou `booking.unassigned`, comparer les postes simultanés aux candidats libres. Pour disponibilité, examiner les intervalles et absences. Pour charge, comparer besoins, contrat, heures supplémentaires, déduction des absences et semaines partielles sans inventer de prorata.

Des viviers étroits ou une règle dure peuvent limiter les mouvements. Un score stagnant seul ne prouve pas un couplage. Examiner les données, comparer un budget différent et, si nécessaire, une configuration expérimentale restaurable sur une copie locale. Assouplir temporairement `resource.consistency` peut éclairer un blocage ; cela ne vaut pas décision de rendre une obligation souple.

## Planning aberrant ou règle muette

Vérifier plugin enregistré, poids, `constraintNames()`, `horizons()`, paramètres et scope réellement non vide. Une donnée sans règle pour la lire n'a aucun effet sur le solveur. Rechercher aussi un `forEach` qui exclut les entités non affectées, une dépendance oubliée à une variable ombre et une divergence score/rapport.

Si la correction porte sur du code, utiliser [constraint-plugin-creator](../constraint-plugin-creator/SKILL.md) et [validate-planning-change](../validate-planning-change/SKILL.md). Pour l'arbitrage des poids, utiliser [weight-calibrator](../weight-calibrator/SKILL.md).

Rapporter cause, preuve et correction. Après une correction, relancer le scénario quand le runtime est disponible et comparer le résultat ; sinon indiquer précisément ce qui reste non vérifié. Ne pas annoncer une résolution réussie sur la seule base d'un test unitaire.
