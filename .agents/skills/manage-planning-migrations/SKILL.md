---
name: manage-planning-migrations
description: Créer, auditer ou diagnostiquer les migrations Flyway PostgreSQL de Planning, son schéma et ses données livrées. Utiliser pour une évolution persistée, une migration de configurations ou un échec Flyway sur la stack autonome.
---

# Gérer les migrations Planning

Travailler depuis la racine Planning. Les migrations sont dans [backend/src/main/resources/db/migration](../../../backend/src/main/resources/db/migration), la configuration Flyway dans [application.yml](../../../backend/src/main/resources/application.yml). Lire les migrations directement concernées et les entités/repositories correspondants.

Planning possède son schéma PostgreSQL `planning`. Ne pas lire les tables d'un autre produit, créer une clé étrangère externe ou utiliser les migrations Flyway de Progression.

## Écrire

Ajouter une nouvelle migration `V<version>__<description>.sql` après la plus haute version du dossier. Conserver les migrations déjà livrées : changer leurs sommes de contrôle ferait échouer les bases existantes. Une migration encore inédite peut être corrigée si son statut est établi ; en cas de doute, ajouter une version.

Séparer schéma, conversion des données et données de démonstration selon le besoin. Vérifier les types, contraintes, indexes, valeurs nulles, références tenant/scénario et configurations JSON qui doivent rester lisibles. Les identifiants de plugin et formes de critères sont aussi des contrats persistés : leur changement demande une conversion explicite.

Les données d'un espace utilisateur passent normalement par l'API ; ne pas transformer chaque action métier en migration. Une démonstration livrée ou un référentiel commun peut justifier une migration. Ajouter au besoin les validations métier au service ; une contrainte SQL reste le filet de sécurité.

## Vérifier et diagnostiquer

Utiliser [run-planning-locally](../run-planning-locally/SKILL.md) et [validate-planning-change](../validate-planning-change/SKILL.md) sur une stack dédiée. Vérifier une installation neuve et, si la conversion affecte les données existantes, une base reproduisant la version précédente. Examiner les logs Flyway et son historique dans le schéma effectivement configuré avant d'interpréter un échec.

Un échec de checksum n'autorise pas automatiquement `repair`, une réécriture d'historique ou un effacement de volume. Déterminer d'abord si la base est jetable et si sa destruction a été demandée. Corriger le SQL et les données dans le périmètre autorisé, sans neutraliser les contrôles de Flyway.

Après application, vérifier le schéma, les valeurs converties et les endpoints concernés. Rapporter la version ajoutée et la validation faite ; documenter toute opération sur une base existante qui reste nécessaire.
