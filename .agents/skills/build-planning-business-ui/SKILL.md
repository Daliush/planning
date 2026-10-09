---
name: build-planning-business-ui
description: Construire, corriger ou revoir les interfaces métier restaurant et entreprise de Planning, leurs formulaires et résultats. Utiliser pour l'ergonomie, le responsive, l'accessibilité et la cohérence entre données serveur et affichage métier.
---

# Développer l'interface métier

Lire [PRODUCT.md](../../../PRODUCT.md), [DESIGN.md](../../../DESIGN.md) et les [usages métier](../../../docs/USE-CASES-metiers.md) avant de changer le parcours. Ces documents portent respectivement l'intention, les choix visuels et les règles/limites actuelles ; les implémentations et contrats API restent à vérifier.

## Points d'entrée

Les parcours vivent dans `frontend/src/features/workforce/`, avec `WorkforceApp.tsx`, `Editors.tsx`, `EmployeeEditor.tsx`, `PlanningViews.tsx` et `ContractBalances.tsx`. Les primitives et tokens locaux sont `components.tsx` et `workforce.css`. Les appels et contrats vivent dans `frontend/src/api/workforce.ts`, `frontend/src/api/types.ts` et les types de feature.

Conserver les compositions restaurant et entreprise, leurs accents et leurs libellés. Le parcours scolaire `/expert` a son propre système ; ne pas lui appliquer les tokens métier par défaut. Réutiliser les composants existants avant d'ajouter une nouvelle primitive. Préserver la navigation mobile et les styles d'impression concernés.

## Données et actions

- Présenter les tâches du responsable en français courant, avec des horaires et états explicites. Une couleur accompagne le texte d'état.
- Garder les appels HTTP dans l'API et les états complexes dans un hook ou composant adapté. Le frontend collecte et présente ; les règles métier et bilans de contrat viennent du serveur.
- Respecter les états chargement, vide, erreur et nouvelle tentative. La démonstration se crée à la demande et conserve son libellé. Ne pas simuler une persistance réussie quand l'API est indisponible.
- Après une modification de données, rendre visible qu'un résultat doit être recalculé. Les écarts de contrat réels ne sont affichés qu'après une résolution actuelle terminée ; les postes vacants restent visibles même avec un job terminé.
- Ne pas masquer les nuits, absences, repos ou semaines partielles par un calcul UI simplifié. Si un comportement métier change, suivre la chaîne DTO/service/projection avec [validate-planning-change](../validate-planning-change/SKILL.md).

## Vérifier

Lancer le build frontend ciblé, puis inspecter le parcours dans le navigateur avec [run-planning-locally](../run-planning-locally/SKILL.md). Vérifier desktop et mobile, navigation au clavier, focus, libellés, dialogues et retours d'erreur. Ajouter les états et données limites pertinents au changement : noms longs, postes vacants, nuits, absences ou nombreuses lignes. Les bilans, grille et cartes doivent correspondre au rapport serveur.

Livrer la modification et ce qui a été vérifié. Si le navigateur n'est pas disponible, signaler cette limite sans prétendre à une validation visuelle. Mettre à jour DESIGN.md seulement si les choix visuels durables ont réellement changé.
