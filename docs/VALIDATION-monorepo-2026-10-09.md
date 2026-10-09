# Validation du monorepo autonome — 9 octobre 2026

## Extraction et historique

Planning est un dépôt Git local autonome avec `backend/` et `frontend/` à sa racine.
L'historique a été extrait avec `git subtree split`, puis cloné sans partager les
objets du dépôt d'origine. Il conserve les quatre livraisons Planning et le commit
préparant l'autonomie. Le tag `planning-v1` pointe sur `bb8dd11`.

Le remote local de transition a été retiré. Aucun dépôt GitHub n'a été créé ni
aucun changement poussé. Le dossier source a été retiré de Progression dans
`eaaa4246` ; les changements de l'utilisateur dans cette application sont préservés.
Les fichiers générés résiduels sont conservés dans `.local/legacy-generated/`,
ignorés par Git et inutilisés par l'application.

## Procédures et découverte

- Neuf skills canoniques dans `.agents/skills`, validés par le validateur du skill-creator.
- Trois rôles natifs TOML dans `.codex/agents`, syntaxe et champs requis vérifiés : plugin, intégration et UI.
- Compatibilité Claude par pointeurs vers les mêmes sources ; liens locaux vérifiés.
- `node scripts/check-codex.mjs` interroge réellement `skills/list` et `config/read` du serveur local Codex : les neuf skills sont découverts et actifs. Aucun modèle ni conversation n'est lancé par ce contrôle.
- Le template de plugin a été instancié dans `.local/template-check`, compilé avec les dépendances verrouillées du projet et soumis à ses quatre tests : conformité, pénalité exacte, configurations concurrentes et scope exclu. Aucun code de ce test n'a été ajouté au moteur.

## Build et parcours isolés

Commande exécutée depuis la racine du nouveau dépôt :

```powershell
./scripts/validate.ps1 -Runtime -Functional -TimeoutSeconds 300
```

Résultats :

- Audit d'autonomie : imports relatifs, manifestes, frontières SQL, contextes Docker et ressources propres au projet conformes.
- Backend : 28 suites, 161 tests, zéro échec, erreur ou test ignoré. Les contrôles score/rapport et FULL_ASSERT existants sont inclus.
- Frontend : installation `npm ci`, vérification TypeScript et build Vite réussis.
- Images Java 21 et frontend Node 22/nginx construites depuis leurs contextes locaux.
- Base neuve PostgreSQL 16 : migrations V1 à V8 appliquées, trois services sains, API directe et proxy `/api` fonctionnels.
- Parcours HTTP : restaurant, entreprise, services de nuit, maladie, résultat obsolète et scolaire conservé.
- Contrats : 35 h strictes, 3 h de supplément autorisé, journée d'absence à 7 h déduite, indisponibilité horaire et plage nocturne.

L'instance `planning-validation` utilise ses ports et son volume propres ; elle est
arrêtée après les tests, sans effacement du volume. Les sorties détaillées restent
localement dans `.local/validation-monorepo.log`.

## Revue indépendante et corrections finales

Le rôle d'intégration a été transmis à un sous-agent en lecture seule. Il a
vérifié séparément l'autonomie, la santé, la découverte native des skills depuis
un dossier parent, les liens locaux et les preuves de tests et de transfert.
Trois problèmes ont été corrigés :

- nginx conserve désormais le port dans `Host` ; le contrôle de santé vérifie une requête OPTIONS avec l'origine frontend effective, pour détecter les refus d'écriture liés au proxy.
- Le README utilise les instructions Codex canoniques et décrit les contrats stricts et les poids actuels.
- L'exemple d'inspection Timefold télécharge son jar dans `.local/` et fonctionne sans cache Maven ni JDK sur l'hôte ; téléchargement et `javap` exécutés avec succès.

La correction du proxy a été validée sur le port personnalisé **15174** :

```powershell
./scripts/validate.ps1 -SkipBackend -SkipFrontend -Runtime -EnvFile .env.example -TimeoutSeconds 300
```

Les trois services et le contrôle d'origine ont réussi, puis la stack isolée a
été arrêtée. Ce volume de test a été réutilisé avec sa configuration initiale ;
un premier essai avec le nouveau `.env` local avait échoué sur son ancien mot de
passe PostgreSQL. Aucun volume n'a été effacé. Journal ciblé :
`.local/validation-proxy-ports.log`.

## Données existantes et aperçu

Une sauvegarde finale du seul schéma `planning` a été restaurée dans la base
propre du projet. Les nombres de lignes et empreintes canoniques des **16 tables**
sont identiques après normalisation UTC et tri en collation C : **20 établissements,
20 scénarios et 130 ressources** sont conservés, ainsi que leurs règles et résultats.
La sauvegarde est dans `backups/planning-before-extraction-20261009.dump`, ignorée
par Git. L'ancien conteneur de base est arrêté et conservé pour récupération.

La stack `planning-standalone` fonctionne sur les ports 5435 (base), 8081 (API) et
5174 (interface). `dev.ps1 -Action start` a créé une configuration `.env` locale
avec un mot de passe généré ; aucun secret n'est versionné. La santé des trois
services et la lecture API à travers nginx ont été vérifiées.

Le navigateur a chargé directement `/entreprise/equipe` depuis nginx : équipes,
contrats, heures supplémentaires et crédit quotidien d'absence sont visibles.
Le rendu desktop a été inspecté. Aucun changement de composition UI n'a été fait ;
ce contrôle ne remplace pas un nouvel audit responsive pour une évolution visuelle.

## Contrôles non exécutés

Le workflow GitHub Actions autonome est versionné ; son exécution distante n'a
pas été déclenchée puisqu'aucun remote GitHub n'est configuré. Les revues natives
Codex sont disponibles, sans prétendre que chacun des trois rôles a été exécuté.
