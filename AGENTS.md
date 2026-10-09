# Planning — instructions du dépôt

Planning est un monorepo autonome : moteur Java et interface React. Ouvrir ce
dossier comme projet et exécuter les commandes depuis cette racine. Aucun fichier,
service, secret, base ou outil installé dans un autre projet ne doit être requis.

## Produit et sources de vérité

- [PRODUCT.md](PRODUCT.md) : utilisateurs, objectifs et décisions produit.
- [docs/USE-CASES-metiers.md](docs/USE-CASES-metiers.md) : règles métier, exemples et limites actuelles.
- [docs/DEMO-realiste.md](docs/DEMO-realiste.md) : chargement de données fictives, contrats atteignables et résultats attendus.
- [DESIGN.md](DESIGN.md) et [.impeccable/surfaces/workforce.md](.impeccable/surfaces/workforce.md) : identité et parcours visuels.
- [docs/ENGINEERING.md](docs/ENGINEERING.md) : architecture, invariants et pièges Timefold vérifiés.
- [docs/SDD-moteur-planification.md](docs/SDD-moteur-planification.md) : intentions de conception ; [README.md](README.md) documente les écarts de l'implémentation.

Lire uniquement les documents utiles. Une nouvelle décision métier durable doit
mettre à jour sa référence et ses exemples ; ne pas maintenir des copies de la
même règle dans plusieurs skills. En cas d'ambiguïté métier déterminante, demander
une précision et avancer sur les travaux indépendants.

## Architecture

Stack verrouillée dans `backend/pom.xml` et `frontend/package-lock.json` : Java 21,
Spring Boot 3.3.5, Timefold 1.16.0 Community, PostgreSQL 16, React 18, TypeScript et Vite.
Ne pas mettre à niveau ces dépendances par opportunité.

Backend sous `backend/src/main/java/com/planning/` :

- `api` : HTTP, validation de surface et DTOs ; dépend de `application` et `domain`.
- `application` : orchestration, transactions et façade métier `workforce`.
- `domain` : modèle du problème, compilation, critères, plugins et solveur ; aucun Spring, JPA ou HTTP.
- `repository` : JPA et adaptateurs des ports `ProblemSource` et `SolveResultSink`.
- `config` : assemblage des composants et configuration.

Frontend sous `frontend/src/` : `api` pour les appels HTTP, `features` pour les
parcours, `ui` pour les composants communs, `hooks` et `utils` pour les helpers.
Les bilans métier viennent des projections serveur : ne pas les recalculer dans
les composants. Pas de règle métier lourde ni de requête HTTP dans la présentation.

Privilégier les changements ciblés, typés et lisibles. Pas de réorganisation
globale, de dépendance à un autre projet ni de code métier propre à une verticale
dans un plugin générique. Ne pas modifier `target`, `dist`, `node_modules`, les
logs ou les migrations Flyway déjà appliquées.

## Invariants sensibles

- Les données JPA deviennent des faits immuables avant la résolution.
- Score incrémental et explication doivent exprimer exactement la même règle.
- Une donnée supplémentaire sans lecteur déclaré ne change aucun résultat. Déclarer les attributs lus et vérifier le catalogue.
- Durées en minutes, dates/heures réelles en base ; pas d'index de créneau persistant.
- `CYCLIC` et `DATED` coexistent ; les calculs datés et nuits utilisent `TemporalBuckets`.
- Les contrats, suppléments et crédits d'absence restent ceux de la référence métier. Une indisponibilité récurrente ne réduit pas le contrat.
- Un job terminé peut avoir des violations HARD ; son état technique ne prouve pas la faisabilité.
- Préserver les scénarios scolaires, les configurations expertes et les rapports historiques.
- L'API actuelle n'a pas d'authentification : ne pas inventer une garantie de contrôle d'accès déjà implémentée.

## Skills et agents

Les skills canoniques et versionnés sont dans `.agents/skills/<nom>/SKILL.md`.
Les lire lorsqu'ils s'appliquent, sans charger tout le catalogue.

| Besoin | Skill |
|---|---|
| Ajouter ou modifier une contrainte | `constraint-plugin-creator` |
| Ajouter un opérateur de critères | `criterion-operator-creator` |
| Créer un parcours pour un nouveau métier | `business-profile-bootstrapper` |
| Régler les priorités et poids | `weight-calibrator` |
| Comprendre un résultat impossible ou aberrant | `infeasibility-diagnoser` |
| Démarrer en développement, charger les démos ou diagnostiquer l'environnement local | `run-planning-locally` |
| Valider une modification et préparer sa livraison | `validate-planning-change` |
| Modifier schéma, données ou migrations | `manage-planning-migrations` |
| Créer, corriger ou vérifier un parcours visuel métier | `build-planning-business-ui` |

Les rôles Codex sont versionnés sous `.codex/agents/` : `plugin-reviewer`,
`integration-reviewer` et `ui-reviewer`. Leur présence n'impose pas de lancer tous
les agents. Si l'outil ne charge pas un rôle nativement, transmettre ses
instructions au sous-agent disponible, sans changer leur portée.

Classer la tâche avant toute délégation :

- Petite correction localisée : travail direct et contrôle ciblé.
- Exploration intermédiaire : au plus un sous-agent en lecture seule si son retour évite réellement un chargement volumineux.
- Tâche large avec branches indépendantes : déléguer par périmètres de fichiers distincts.

L'agent principal garde les décisions métier, l'intégration et la validation
finale. Donner aux spécialistes les seuls documents nécessaires, préciser leurs
fichiers autorisés et demander dix points maximum avec références. Les reviewers
restent en lecture seule. Éviter les doubles lectures et les écritures concurrentes
dans les mêmes fichiers. Ne pas faire tourner deux Maven sur le même `target`.

## Exécution et livraison

Consulter [README.md](README.md) pour les prérequis et options des scripts.

```powershell
./scripts/dev.ps1 -Action start
./scripts/dev.ps1 -Action health
./scripts/validate.ps1
./scripts/dev.ps1 -Action stop
```

Adapter les contrôles au changement : moteur → tests ciblés et score/rapport ;
contrat API ou persistance → parcours HTTP sur PostgreSQL ; UI → compilation et
vérification visuelle. Les captures doivent être inspectées et correspondre au
viewport annoncé. Les scripts HTTP ajoutent uniquement des données fictives dans
des espaces identifiés sur une base locale ou de test.

Conclure avec les changements, preuves exécutées et limites restantes. Ne pas
annoncer un test ou une revue non exécuté. Commits préfixés `feat`, `fix`, `docs`
ou `chore`, en préservant les modifications de l'utilisateur hors périmètre.
