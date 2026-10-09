---
name: validate-planning-change
description: Valider une modification Planning avec les contrôles ciblés de domaine Java, frontend, migrations, autonomie et parcours métier. Utiliser avant de conclure une implémentation ou de préparer un commit, et pour diagnostiquer un contrôle en échec.
---

# Valider une modification

Travailler depuis la racine Planning. Lire [scripts/validate.ps1](../../../scripts/validate.ps1) et sélectionner les contrôles nécessaires au changement. Docker fournit Java 21 pour les tests ; le build frontend requiert Node.js 22 et npm. Aucun outil ni fichier d'un autre projet n'est utilisé.

```powershell
./scripts/validate.ps1
./scripts/validate.ps1 -SkipFrontend
./scripts/validate.ps1 -SkipBackend
./scripts/validate.ps1 -Runtime
./scripts/validate.ps1 -Runtime -Functional
```

Sans option, lancer l'audit d'autonomie, les tests backend et le build frontend. `-SkipFrontend` convient à un changement backend localisé ; `-SkipBackend` à une modification UI sans contrat partagé. `-Runtime` ajoute un démarrage isolé et la santé de la stack. `-Functional` vérifie les parcours workforce et contrats sur cette stack. Vérifier les paramètres effectifs avant d'utiliser une instance personnalisée.

## Contrôles par impact

- Plugin, critères, builder ou modèle temporel : tests backend, cas métier touchés, cohérence score/rapport et `FULL_ASSERT` quand le score incrémental est concerné.
- API, DTO ou façade workforce : vérifier la chaîne formulaire, payload, service, persistance et projection. Utiliser les scripts [validate-workforce.ps1](../../../scripts/validate-workforce.ps1) et [validate-contracts.ps1](../../../scripts/validate-contracts.ps1), ou `-Functional`, selon les scénarios modifiés. Ces scripts créent des données de validation ; utiliser une base locale dédiée.
- Schéma ou données Flyway : base isolée, migrations et tests du comportement concerné ; suivre [manage-planning-migrations](../manage-planning-migrations/SKILL.md).
- UI : build puis contrôle visuel et fonctionnel sur desktop et mobile pour le parcours changé, avec les états vide, erreur et résultat utiles. Suivre [build-planning-business-ui](../build-planning-business-ui/SKILL.md).
- Runtime ou extraction : audit d'autonomie, découverte des skills avec `node ./scripts/check-codex.mjs` lorsque Codex est installé, configuration Compose, santé et requêtes métier pertinentes ; aucune dépendance aux chemins ou secrets d'un dépôt parent. Vérifier l'origine navigateur à travers le proxy sur le port frontend effectif ; un GET sans `Origin` ne détecte pas un refus CORS.

Lire le diff des fichiers réellement modifiés et chercher les fichiers générés, changements hors périmètre, chemins absolus locaux et couplages externes involontaires. Ne pas modifier `target/`, `dist/` ou les dépendances générées comme sources.

Après une réussite, ne relancer ou élargir les contrôles que si une nouvelle modification, un échec ou un doute concret le justifie. Rapporter les contrôles exécutés, leurs résultats et les limites précises ; un test non exécuté reste non vérifié.
