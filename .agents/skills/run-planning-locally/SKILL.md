---
name: run-planning-locally
description: Démarrer, arrêter ou diagnostiquer Planning, utiliser Vite en développement et charger des données métier réalistes. Utiliser pour lancer le serveur local, remplir la base de démonstration ou isoler une instance de travail.
---

# Exécuter Planning localement

Travailler depuis la racine Planning. Lire le [README](../../../README.md), [scripts/dev.ps1](../../../scripts/dev.ps1), [docker-compose.yml](../../../docker-compose.yml) et [.env.example](../../../.env.example) selon le besoin. Docker fournit Java 21 et PostgreSQL ; les versions exactes sont celles du dépôt.

```powershell
./scripts/dev.ps1 -Action start
./scripts/dev.ps1 -Action status
./scripts/dev.ps1 -Action health
```

Le projet Compose par défaut est `planning-standalone`. Les ports par défaut sont base 5435, API 8081 et frontend 5174. Si ces ports sont occupés, utiliser les options `-ProjectName`, `-DatabasePort`, `-ApiPort`, `-FrontendPort` pour une instance distincte. Conserver les mêmes paramètres entre démarrage, contrôle et arrêt. `-EnvFile` accepte une configuration dédiée ; ne pas afficher les secrets. `-SkipBuild` réutilise les images seulement si les changements ne nécessitent pas de reconstruction.

La stack doit utiliser ses propres services et volumes. Un problème de démarrage ne justifie pas d'arrêter les services de Progression ni de lire ses secrets. Pour arrêter l'instance concernée, exécuter `./scripts/dev.ps1 -Action stop` avec ses paramètres ; un arrêt conserve ses volumes.

## Développement et données réalistes

Pour modifier l'interface avec rechargement automatique des sources :

```powershell
./scripts/dev.ps1 -Action start -Mode dev -SeedDemo
./scripts/dev.ps1 -Action health -Mode dev
./scripts/dev.ps1 -Action stop -Mode dev
```

Docker fournit Node 22 et les dépendances dans un volume propre au projet. Les
sources frontend sont montées dans Vite ; le proxy conserve l'origine avec son
port. L'API reste compilée dans son image Java : une modification backend demande
un nouveau `start` avec reconstruction. Le mode par défaut `built` utilise nginx
et les fichiers compilés ; revenir à `-Mode built` pour vérifier ce rendu.

`-SeedDemo` est optionnel et réservé au démarrage. Sans date, il charge le prochain
lundi ; `-DemoWeekStart AAAA-MM-JJ` choisit une semaine complète. Pour une instance
personnalisée, garder ses options de projet, ports et environnement.

Lire [les exemples et résultats attendus](../../../docs/DEMO-realiste.md) avant de
modifier ou diagnostiquer ces données. Le [SQL](../../../database/seeds/workforce-realistic.sql)
crée deux références réalisables et une variante avec deux tensions. Il est séparé
de Flyway et préserve les espaces existants et les démos déjà chargées.

```powershell
./scripts/seed-demo.ps1 -WeekStart 2026-10-12
./scripts/validate-demo.ps1 -WeekStart 2026-10-12
```

Le chargement ne calcule pas automatiquement les plannings : lancer le calcul
dans l'interface ou utiliser le validateur. Celui-ci crée des jobs dans les trois
espaces fictifs et vérifie contrats, absences, suppléments et postes vacants.
Un job `DONE` n'est pas une preuve de faisabilité. Une démo modifiée peut ne plus
correspondre aux attentes du validateur ; ne pas la réinitialiser sans demande.

## Diagnostiquer

Lire le statut et les logs du service concerné avec le projet et le fichier Compose exacts. Distinguer une collision de port, une image non construite, une variable manquante, un échec Flyway, une base indisponible et une erreur API. Un volume PostgreSQL existant conserve le mot de passe choisi lors de son initialisation : changer `.env` ne le remplace pas. Retrouver la configuration utilisée pour ce volume sans l'effacer pour corriger un simple refus de connexion. Vérifier les endpoints de santé décrits dans le script puis ouvrir le frontend pour un contrôle fonctionnel utile.

Pour les migrations, suivre [manage-planning-migrations](../manage-planning-migrations/SKILL.md). Pour un job partiel ou en échec, suivre [infeasibility-diagnoser](../infeasibility-diagnoser/SKILL.md). Pour valider une modification, suivre [validate-planning-change](../validate-planning-change/SKILL.md).

Rapporter les URL locales effectives, le statut vérifié et tout contrôle encore manquant. Ne pas annoncer que le projet fonctionne à partir du seul démarrage des conteneurs.
