---
name: run-planning-locally
description: Démarrer, arrêter ou diagnostiquer la stack locale autonome Planning avec PostgreSQL, l'API Java et le frontend. Utiliser pour lancer le projet, vérifier sa santé ou isoler une instance de développement sans dépendance à Progression.
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

## Diagnostiquer

Lire le statut et les logs du service concerné avec le projet et le fichier Compose exacts. Distinguer une collision de port, une image non construite, une variable manquante, un échec Flyway, une base indisponible et une erreur API. Vérifier les endpoints de santé décrits dans le script puis ouvrir le frontend pour un contrôle fonctionnel utile.

Pour les migrations, suivre [manage-planning-migrations](../manage-planning-migrations/SKILL.md). Pour un job partiel ou en échec, suivre [infeasibility-diagnoser](../infeasibility-diagnoser/SKILL.md). Pour valider une modification, suivre [validate-planning-change](../validate-planning-change/SKILL.md).

Rapporter les URL locales effectives, le statut vérifié et tout contrôle encore manquant. Ne pas annoncer que le projet fonctionne à partir du seul démarrage des conteneurs.
