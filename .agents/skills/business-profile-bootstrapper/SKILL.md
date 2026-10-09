---
name: business-profile-bootstrapper
description: Amorcer ou étendre une verticale métier Planning, un tenant et ses données, en réutilisant le moteur générique et les façades métier. Utiliser pour un nouveau parcours sectoriel, des types de ressources, un horizon ou une première configuration de planning.
---

# Amorcer une verticale métier

Lire le [produit](../../../PRODUCT.md), les [usages actuels](../../../docs/USE-CASES-metiers.md) et les invariants de [l'ingénierie](../../../docs/ENGINEERING.md). Partir de la racine Planning ; garder le module autonome et les données dans son schéma PostgreSQL.

## Modéliser le besoin

Déduire du contexte ce qui est planifié, les rôles et quantités requis, les ressources et compétences, les disponibilités, les absences et l'horizon. Ne demander que les décisions métier réellement manquantes. Choisir CYCLIC pour une rotation récurrente, DATED pour des dates réelles.

Séparer une case à remplir (`demand`/`requirement`) d'une règle qui juge son affectation. Garder rôle et type distincts. L'éligibilité va dans les critères ; les disponibilités vont dans `resource_availability` et `TimeProfile`, pas dans `attributes`.

Réutiliser `/api/workforce` et `application/workforce` pour restaurant/entreprise : les créations atomiques produisent les données génériques. Les limites de grille, période, contrats, absences et nuit sont maintenues dans les usages métier ; ne pas en inventer de nouvelles ni recopier ces règles dans le frontend.

## Étendre seulement ce qui manque

- Le vocabulaire partagé par une verticale va dans `AttributeCatalog`. L'attribut propre à un tenant peut compléter `resource_type.attr_schema` ; vérifier la fusion réelle du formulaire.
- `BusinessProfile` sert le vocabulaire et le parcours. Le noyau de score reste générique, sans branche sectorielle en dur.
- Des données et paramètres existants suffisent souvent. Un nouveau parcours peut aussi exiger une façade Java, des DTO, une projection, une UI ou un plugin. Implémenter l'extension autorisée dans sa couche ; utiliser [constraint-plugin-creator](../constraint-plugin-creator/SKILL.md) ou [criterion-operator-creator](../criterion-operator-creator/SKILL.md) si nécessaire.
- Une évolution persistée passe par [manage-planning-migrations](../manage-planning-migrations/SKILL.md). Préférer l'API pour les données d'un espace utilisateur et une nouvelle migration pour une démonstration livrée.

## Première résolution

Vérifier les plugins et leurs horizons dans le catalogue, puis poser des poids et scopes explicites. Un poids nul désactive une contrainte. Commencer avec les obligations utiles et ajouter les préférences par mesure, via [weight-calibrator](../weight-calibrator/SKILL.md).

Contrôler les viviers, les départs et les scopes. DATED conserve les postes sans candidat pour expliquer la couverture manquante ; CYCLIC peut les refuser au chargement. Une case vacante doit rester visible. Lancer une résolution locale, lire les affectations et le rapport, puis valider le parcours avec [validate-planning-change](../validate-planning-change/SKILL.md).

Livrer les données ou appels utilisés, l'espace créé, le résultat vérifié et les limites utiles au métier. Pour une nouvelle UI, suivre [build-planning-business-ui](../build-planning-business-ui/SKILL.md).
