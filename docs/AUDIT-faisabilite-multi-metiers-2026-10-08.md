# Audit de faisabilité — Planning scolaire, restauration et entreprise

Date : 8 octobre 2026. Audit de l'état local du module `planning/`, encore non suivi par Git à l'ouverture de cet audit.

## 1. Verdict

**Le noyau mérite d'être conservé. Il est extensible, mais le produit actuel ne couvre pas encore la planification opérationnelle d'un restaurant ou d'une entreprise.**

La bonne abstraction présente est : **placer des activités de durée fixe sur une grille, puis leur affecter des ressources exclusives admissibles**. Cours, réunions et services prédéfinis entrent dans cette famille. Choisir automatiquement les durées de service, générer les équipes en fonction de l'affluence, gérer des tournées ou partager une capacité entre plusieurs activités ne sont pas de simples variations de formulaire.

L'architecture est bien structurée : domaine indépendant de Spring et de PostgreSQL, ports de lecture/écriture, compilation des disponibilités et des candidats, règles paramétrées, explications métier, calcul global des conflits. Ses défauts les plus importants sont six erreurs de comportement reproduites, un catalogue encore orienté scolaire et une interface qui expose une grande partie du modèle technique.

**Trois interfaces différentes sur un moteur commun sont faisables.** En revanche, annoncer aujourd'hui trois modes pleinement opérationnels serait prématuré. `scenario.profile` filtre le vocabulaire des attributs ; il ne crée ni parcours spécifique, ni modèle daté, ni ensemble de règles métier prêt à l'emploi.

Il n'est ni nécessaire ni souhaitable de promettre un moteur capable de « tout prendre en compte ». Définir le périmètre des décisions est plus utile : activités imposées ou services candidats, durée fixe ou variable, ressources exclusives ou capacité partagée, cycle récurrent ou dates réelles.

## 2. Méthode et niveau de preuve

- Consignes étudiées : `AGENTS.md`, `planning/CLAUDE.md`, README et spécification SDD.
- Agent local appliqué : `planning/.claude/agents/plugin-reviewer.md`, exécuté par `/root/plugin_reviewer`.
- Skills locaux appliqués : `business-profile-bootstrapper`, `constraint-plugin-creator`, `infeasibility-diagnoser`, `weight-calibrator`, `criterion-operator-creator` selon leur périmètre d'audit. Aucun amorçage de tenant ou réglage de poids n'a été effectué.
- Deux évaluations indépendantes de l'interface : `/root/ux_metiers` et `/root/ux_detection`, selon le skill Impeccable.
- **54 tests backend exécutés : tous passent**, dont vérification du score incrémental en `FULL_ASSERT` et cohérence score/rapport.
- **Vérification TypeScript exécutée : réussie.**
- **Scan Impeccable unique : 0 signalement.** Il ne prouve pas l'absence de défauts fonctionnels ou d'accessibilité.
- Six contre-exemples supplémentaires exécutés dans un programme temporaire, hors des sources du dépôt ; résultats détaillés ci-dessous.
- Revue de l'API, des services, du schéma et des adaptateurs de persistance par l'agent principal.
- Pas de rendu visuel de l'application : `localhost:5174` refusait la connexion et Docker était arrêté. Aucun contraste, débordement ou parcours ne doit être présenté comme observé dans un navigateur. Les constats d'interface sont tirés des sources ; le contraste indiqué est calculé à partir des couleurs.
- Pas de tests PostgreSQL/HTTP, de benchmark de volume réel ou de preuve d'optimalité. Le périmètre vérifié est le code présent, pas une installation de production.

## 3. Six défauts reproduits à corriger avant l'extension

P1 signifie ici : comportement incorrect ou obstacle majeur dans le périmètre pris en charge. Ces constats sont indépendants des fonctions encore prévues dans la feuille de route.

### 3.1 [P1] Heure de fin fausse après une pause

Cas exécuté : activité de 120 minutes démarrant à 09:00, avec les créneaux 09:00–10:00 et 10:15–11:15. Le départ est admissible. Le moteur occupe deux créneaux, mais `booking.endTime()` donne **11:00**, au lieu de **11:15**.

Le même calcul figure dans l'écriture du résultat. Les conflits et disponibilités travaillent sur les indices occupés, alors que les trous, amplitudes, préférences et exports utilisent une fin différente. Un score cohérent avec son rapport peut donc rester faux sur le plan métier.

Sources : `backend/src/main/java/com/planning/domain/model/ResourceBooking.java:116`, `domain/builder/StartsCompiler.java:52`, `domain/solver/SolveJobRunner.java:100`, `domain/constraint/Bookings.java:41`, `domain/constraint/plugin/PreferredWindowConstraint.java:55`.

Correction : calculer la fin physique à partir du dernier créneau occupé et distinguer durée travaillée, durée écoulée et pauses. Conserver une seule définition de la fin dans les règles et la persistance.

### 3.2 [P1] Une grille chevauchante masque une double affectation

Cas exécuté : créneaux 09:00–10:00 et 09:30–10:30 ; deux activités d'une heure sur la même personne. L'horizon accepte la grille et le plugin retourne **zéro conflit**, malgré 30 minutes communes. Il accepte également un départ de 120 minutes qui tient dans seulement 90 minutes écoulées.

La contrainte de conflit suppose que les intervalles d'indices correspondent à des intervalles temporels disjoints. Cette hypothèse n'est pas validée. Le schéma ne vérifie que l'ordre des bornes de chaque ligne. Le générateur de grille accepte des pauses négatives dans ses entrées, faute de validation, ce qui constitue un chemin vers cette situation ; ce chemin HTTP n'a pas été exécuté.

Sources : `domain/builder/HorizonFactory.java:47`, `domain/time/TimeGrid.java:27`, `domain/time/GapPolicy.java:22`, `domain/constraint/plugin/ResourceConflictConstraint.java:79`, `application/ReferentialService.java:128`, `api/dto/Dtos.java:49`, `resources/db/migration/V1__schema_planning.sql:63`.

Correction : refuser chevauchements, intervalles négatifs et incohérences jour/ordinal avant compilation, avec validation dans le service et le domaine. Tester aussi le générateur via l'API.

### 3.3 [P1] Une règle structurelle explicite double son explication

Cas exécuté avec le calculateur de score Timefold : un conflit réel, deux configurations `resource.conflict`. **Score du solveur : -1 dur ; score reconstitué par le rapport : -2 dur.**

Le builder conserve les configurations explicites puis ajoute sa configuration synthétique. Le plugin structurel score sans les joindre, alors que l'explication traverse chacune. La couche application ne refuse pas une configuration explicite pour ces plugins.

Sources : `domain/builder/ProblemBuilder.java:179`, `domain/solver/ExplanationService.java:89`, `domain/constraint/plugin/ResourceConflictConstraint.java:79`, `application/ConstraintService.java:148`.

Correction : rejeter une configuration de plugin non configurable à la saisie et au chargement. Garder la configuration synthétique comme seule source de son explication.

### 3.4 [P1] Les minimums ignorent les périodes entièrement vides

Cas exécuté : personne disponible les trois jours du fixture, minimum de 60 minutes par jour, une heure le premier jour et aucune les deux suivants. **Zéro violation.**

L'agrégation ne crée que les paniers alimentés par une affectation. Le pipeline complémentaire détecte une personne jamais utilisée sur tout l'horizon, pas une journée ou semaine vide chez une personne utilisée ailleurs.

Source : `domain/constraint/plugin/MinLoadConstraint.java:134` et `:175`.

Correction : évaluer les couples ressource/période concernés, y compris ceux de charge zéro, en définissant explicitement les jours auxquels le minimum s'applique. Un congé ne doit pas créer artificiellement une obligation de travail.

### 3.5 [P1] Le calcul d'équilibre déborde avant la division

Cas exécuté : cinq journées de huit heures sur une grille valide, soit 2 400 minutes ; poids de règle 400. **Pénalité obtenue : -1 990 967 ; valeur attendue : 2 304 000.**

La formule multiplie des entiers 32 bits avant de diviser par 1 000. La division ne protège pas le produit intermédiaire. Ce poids est permis par les contrats actuels. Une pénalité négative peut provoquer un échec de scoring ou un résultat incohérent.

Source : `domain/constraint/plugin/LoadBalanceConstraint.java:48`. Les produits et cumuls d'`ExplanationService` sont également en `int` et demandent une vérification.

Correction : calculer avec une largeur suffisante, contrôler les bornes et décider si `HardSoftLongScore` est préférable pour les horizons et poids envisagés. Ne pas réduire arbitrairement les poids pour masquer le défaut.

### 3.6 [P1] La continuité ne sait pas exprimer une équipe stable

Cas exécuté : **une seule occurrence**, un rôle « serveur » occupé par A et B. Le plugin de continuité retourne une violation parce qu'il compte deux personnes distinctes. Il annonce un changement d'une séance à l'autre alors qu'il n'y a qu'une séance.

Avec plusieurs occurrences, une équipe A+B parfaitement stable viole également cette règle. Faire occuper les deux postes par une seule personne crée alors un conflit. La combinaison quantité multiple + continuité ne représente donc pas la stabilité d'une équipe.

Source : `domain/constraint/plugin/ResourceConsistencyConstraint.java:48` et `:94` ; création des postes multiples dans `domain/builder/ProblemBuilder.java:139`.

Correction : distinguer continuité d'un poste individuel et stabilité d'un ensemble. Restreindre la règle actuelle aux rôles singleton tant qu'aucune sémantique d'équipe n'est disponible. Prévoir une identité stable de poste si la retouche doit agir poste par poste.

## 4. Faisabilité par besoin

« Présent » désigne la capacité de modélisation, sous réserve de corriger les défauts ci-dessus ; cela ne signifie pas produit prêt à exploiter.

| Besoin | État actuel | Travail nécessaire |
|---|---|---|
| Cours récurrents, salles et enseignants | Présent | Fiabiliser horaires et règles ; améliorer saisie et résultat |
| Réunions ou activités récurrentes à durée fixe | Présent | Parcours entreprise et critères adaptés |
| Affecter cinq personnes dont un responsable à un service fixé | Présent | Postes dédiés, dates/jours explicites, compétences et règles adaptées |
| Choisir le début entre 06:00 et 09:00 sur une grille | Présent | Placement `window` et granularité suffisante ; pas de refonte nécessaire pour ce cas discret |
| Congés individuels, absences du jeudi, jours fériés | Absent | Horizon daté, disponibilités datées, contrats API/persistance adaptés |
| Remplacer un absent sans déplacer tout le planning | Prévu, non opérationnel | Recharger la solution, épingler, réoptimiser localement, limiter les changements |
| Services traversant minuit | Non pris en charge | Instants ou dates/heures et calculs de frontières ; ne pas découper naïvement la personne en deux activités |
| Besoin d'effectifs variant au fil de la journée | Approximable avec des blocs fixes | Pour de vrais services optimisés : couverture par intervalle et sélection de services candidats |
| Choisir automatiquement les durées et pauses des services | Absent | Étendre les décisions de planification ou utiliser des modèles de services candidats bornés |
| Repos entre journées et rotations équilibrées dans le temps | Catalogue absent | Règles dédiées, dates réelles, historique avant/après l'horizon |
| Coût de personnel et heures supplémentaires | Catalogue absent | Coûts et objectifs dédiés, contrats métier fiables |
| Charge contractuelle différente pour chaque salarié | Partiel | `load.min/max` à seuil configuré ; compiler les quotas individuels ou faire lire les bons attributs |
| Capacité d'une salle comme filtre d'admissibilité | Présent | Critère explicite par besoin ; le chiffre saisi seul ne crée pas la règle |
| Machine, lieu ou stock partagé simultanément jusqu'à une capacité | Non représenté par le conflit exclusif | Règle de capacité cumulée et sémantique de consommation |
| Tournées, précédences industrielles ou durée dépendant de l'effectif | Hors périmètre courant | Autre modèle de décision ; ne pas promettre une extension par configuration |

### 4.1 Ce que la documentation prévoit correctement

Le SDD distingue déjà `Cyclic` et `Dated`, et classe repos, rotations, publication et retouche dans les jalons suivants. Le skill `business-profile-bootstrapper` demande explicitement de reconnaître la limite des absences datées. Ce sont des travaux manquants, pas des surprises découvertes à posteriori.

Cependant, « l'interface d'horizon existe » ne signifie pas que le passage aux dates sera mécanique. `Timeslot`, les disponibilités, les DTO, les résultats et plusieurs messages manipulent encore le jour de cycle et `LocalTime`. Les appels directs à `CycleBuckets` devront être revus. Les colonnes datées déjà en SQL ne suffisent pas à rendre le moteur daté.

Le SDD présente également l'heure de début flexible comme une limite majeure : **il faut nuancer**. Choisir parmi les créneaux admissibles d'une fenêtre est déjà possible. Une heure réellement continue, une durée variable ou un ensemble de services dont le solveur choisit l'activation demandent une évolution plus importante.

### 4.2 Le risque principal en restauration

« Il faut quatre serveurs de 19:00 à 22:00 » et « construire les meilleurs services pour suivre l'affluence entre midi et minuit » sont deux problèmes différents.

Le premier se compile en activités et postes fixes. Pour le second, l'effectif demandé varie dans le temps, alors que le moteur crée un nombre fixe d'activités, avec une durée et des postes fixes. Découper l'affluence en petits blocs peut couvrir les cases sans produire des services de travail cohérents.

Deux directions à évaluer : services prédéfinis simples pour une première version ; sélection de modèles de services candidats avec couverture et coût pour une version plus ambitieuse. Les services facultatifs doivent être distincts des besoins obligatoires, sinon le solveur essaiera de pourvoir tous les candidats.

Cette distinction est aussi documentée dans les modèles métier actuels de Timefold : [affectation de services fixés et couverture de demande horaire](https://docs.timefold.ai/employee-shift-scheduling/latest/shift-service-constraints/demand-based-scheduling). Il s'agit d'une référence de modélisation, **pas de fonctionnalités supposées présentes dans notre bibliothèque 1.16 ni d'une recommandation de migrer vers cette API hébergée**.

### 4.3 « Entreprise » doit être délimité

Pour des horaires d'équipes, les extensions datées, de repos, de couverture et de charge sont proches de la restauration. Pour des réunions, l'actuel modèle d'activités fixes suffit davantage. Pour des projets, interventions mobiles, chaînes de production ou affectations de tâches divisibles, les décisions changent : il faut définir une autre sous-famille de produit avant de juger sa faisabilité.

## 5. Audit de l'expérience utilisateur

Method: dual-agent (A: /root/ux_metiers · B: /root/ux_detection)

### 5.1 Verdict de conception

L'interface présente une console de configuration du moteur, avec des améliorations utiles de vocabulaire. Elle ne propose pas encore trois produits métier. Les routes `Référentiel / Demandes / Règles / Résolution` et la grille restent communes aux profils.

Le plus grand gain ne serait pas une nouvelle palette : il serait de supprimer la nécessité de traduire soi-même les besoins en attributs, placements, postes et critères.

### 5.2 Évaluation heuristique provisoire

Évaluation qualitative des parcours déduits du code, sans session utilisateur ni rendu. Elle ne mesure pas la qualité esthétique réelle.

| Critère | Score / 4 | Motif principal |
|---|---:|---|
| Visibilité de l'état | 2 | Suivi des jobs, mais détail non rechargé à la fin |
| Correspondance avec le métier | 1 | Demandes et postes exposent le langage du moteur |
| Contrôle utilisateur | 2 | Modales et annulation présentes ; mutations partielles surprenantes |
| Cohérence | 2 | Composants communs ; vocabulaire et frontières d'enregistrement inégales |
| Prévention des erreurs | 1 | JSON, suppressions immédiates, saisies incomplètes possibles |
| Reconnaissance plutôt que mémorisation | 2 | Phrases de règles utiles ; liens entre écrans à reconstruire |
| Efficacité | 1 | Enregistrer puis rouvrir pour compléter une nouvelle demande |
| Esthétique et sobriété | Non évalué | Aucun rendu de l'application accessible |
| Récupération des erreurs | 2 | Messages existants, mais erreurs de résultat absorbées |
| Aide | 2 | Explications présentes, encore proches de la configuration technique |
| **Total provisoire** | **15/36** | Parcours à revoir avant ouverture métier |

### 5.3 Ce qui fonctionne déjà

1. Les attributs des ressources deviennent des champs ordinaires ; le JSON avancé peut être replié (`frontend/src/features/resources/AttributesForm.tsx:39`).
2. L'éditeur reformule une règle sous forme de phrase (`features/constraints/RuleEditor.tsx:79`).
3. La sélection des personnes visées est confrontée aux données, avec avertissement quand elle ne vise personne (`features/constraints/ScopeEditor.tsx:258`).

Ces deux derniers mécanismes sont la meilleure base pour une interface qui traduit les besoins.

### 5.4 Cinq priorités UX

| Priorité | Défaut et conséquence | Correction concrète | Référence |
|---|---|---|---|
| P1 | Les profils ne changent ni parcours ni présentation ; exemples encore scolaires | Structures d'écran et vocabulaire propres au métier | `frontend/src/App.tsx:12`, `api/types.ts:5` |
| P1 | Créer une demande complète nécessite JSON et réouverture | Formulaire métier complet avec postes préparés dans le même brouillon | `features/demands/DemandEditor.tsx:89`, `:107`, `:203` |
| P1 | Les règles suivent les familles techniques et plusieurs poids | Modèles métier, décisions guidées, aperçu de la règle et arbitrages compréhensibles | `features/constraints/ConstraintsPage.tsx:80` |
| P1 | Résultat non rechargé à `DONE`, erreurs avalées, ancienne sélection potentiellement conservée | Détail piloté par statut et scénario ; états attente/erreur/réessai distincts | `features/solve/SolvePage.tsx:27`, `:33`, `:43`, `:130` |
| P1 | Mutations partielles : postes immédiatement écrits, disponibilité échouée après création, suppressions sans récupération | Sauvegarde cohérente, reprise sur l'identifiant créé, confirmation ou restauration | `features/demands/DemandEditor.tsx:132`, `features/resources/ResourceEditor.tsx:62`, `ResourcesPage.tsx:33` |

Commandes Impeccable adaptées à une phase ultérieure : `shape` pour les parcours, `distill`/`clarify` pour les règles et le vocabulaire, `harden` pour les états et sauvegardes. Aucune refonte n'a été lancée pendant l'audit.

### 5.5 Charge cognitive et personnes exposées

La création suit un parcours probable : début compréhensible, difficulté au JSON, amélioration avec les phrases de règle, puis incertitude devant les résultats et pénalités. Les nombreux choix, leur dispersion entre écrans et les poids à plusieurs niveaux demandent de comprendre le modèle avant d'obtenir un planning.

- **Première utilisation** : difficulté à choisir les critères JSON et à comprendre pourquoi un attribut saisi n'agit pas.
- **Responsable expérimenté** : saisie à l'unité, réouverture des besoins et absence de traitement groupé visible ralentissent les changements.
- **Usage mobile et clavier** : grille minimale de 44rem, noms accessibles manquants sur des sélecteurs, piégeage de focus incomplet au premier Shift+Tab.

### 5.6 Observations complémentaires

- [P2] Le formulaire des ressources utilise le catalogue d'attributs, mais le filtre de scope lit seulement `attrSchema` : certaines données saisissables ne deviennent pas des filtres ordinaires (`ScopeEditor.tsx:49`).
- [P2] Sélecteurs sans nom accessible et annonces d'état absentes (`ScenarioPicker.tsx:55`, `ui/DurationInput.tsx:26`, `ui/Feedback.tsx:1`).
- [P2] Focus initial sur le panneau de modale non traité par la première navigation inverse (`ui/Modal.tsx:31`, `:59`).
- [P2] Accent sombre `#f06a9d` avec texte blanc : contraste calculé d'environ **2,90:1** pour les sélections de texte petit (`index.css:63`, `App.tsx:45`).
- [P2] Rapport à risque de débordement mobile : 224px de libellé + 80px de valeur + 16px d'espacement avant la barre, dans des sections avec marges internes (`features/solve/ReportPanel.tsx:51`). À confirmer visuellement.
- Le scan mécanique n'a signalé aucun de ces défauts ; ils viennent de la revue des comportements et styles.

## 6. Produit recommandé : trois expériences, une traduction maîtrisée

| Mode | Navigation et point d'entrée | Résultat prioritaire |
|---|---|---|
| École | Classes, enseignements, enseignants, salles, organisation | Emploi du temps par classe, professeur et salle ; volumes et incompatibilités |
| Restaurant | Services, besoins par tranche, équipe, disponibilités, planning | Couverture salle/cuisine, postes manquants, services individuels et heures |
| Entreprise — horaires d'équipes | Sites/équipes, horaires, compétences, absences, planning | Affectations par salarié, charge contractuelle, couverture et rotations |

L'école privilégie la semaine et les cours. Le restaurant privilégie le service et la couverture au fil de la journée. L'entreprise privilégie les personnes, sites et charges. Des composants visuels peuvent être partagés sans imposer le même écran, ni une esthétique identique.

### 6.1 Exemple d'interface traductrice

Saisie restaurant : « Vendredi, de 19:00 à 23:00, cinq personnes en salle, dont un chef de rang ».

Traduction déterministe proposée : une activité au jour explicitement fixé, durée de quatre heures, un poste de chef de rang avec critère de compétence, quatre postes de salle, puis règles de disponibilité/conflit et objectifs retenus. L'utilisateur choisit les personnes ou compétences dans des champs métier ; il ne rédige ni JSON ni `contains`.

Il voit avant validation : « Cinq postes à couvrir vendredi soir. Trois personnes peuvent assurer le rôle de chef de rang. » Après calcul : personnes proposées, poste éventuellement manquant et raison connue. Une cause déduite doit être distinguée d'une cause prouvée par une analyse de relaxation.

**Important : `occurrences=5` avec une heure fixe sans jour ne signifie pas un service chacun des cinq jours.** Le placement actuel n'impose pas cette distribution. Le traducteur doit créer les bons jours ou la bonne règle explicitement (`domain/placement/FixedPlacement.java:37`).

### 6.2 Où placer cette traduction

- Contrats d'entrée métier explicites : cours, besoin de service, plage d'équipe, absence.
- Conversion et validation dans la couche application ; le frontend collecte l'intention, le backend reste la référence.
- Données canoniques et règles compilées lisibles par le domaine existant, sans `if restaurant` dans les plugins génériques.
- Identifiant/version de l'intention et lien vers les activités/règles générées, pour rouvrir le formulaire sans reconstruire un besoin à partir de JSON arbitraire.
- Mise à jour atomique ou reprise explicite des écritures ; aucune duplication de règles lors d'une seconde sauvegarde.
- Résultats et indicateurs propres au métier : cours non placés, postes non couverts, heures prévues/contractuelles, changements depuis le planning précédent. Les pénalités techniques restent accessibles au diagnostic.

Des choix plus limités par métier sont souhaitables s'ils évitent des configurations incohérentes. Une console experte secondaire peut conserver l'accès aux cas avancés. Les obligations restent non désactivables dans le parcours normal ; seules les préférences s'arbitrent.

## 7. Autres points à traiter avant exploitation

1. **Attributs qui n'agissent pas.** `quota_hebdo_minutes` est proposé avec une description promettant un seuil, mais les plugins de charge lisent `params.minMinutes/maxMinutes`, pas cet attribut (`domain/attribute/AttributeCatalog.java:94`). Le traducteur doit créer les règles individuelles ou le catalogue doit être corrigé. Le site n'est pas une règle de déplacement ; aucune donnée ne crée automatiquement une contrainte.
2. **Critères et données incomplètes.** Négation d'un test sur un attribut absent, égalité à `null`, scalaires convertis en texte et tableau vide interprété comme critère universel demandent une sémantique documentée et des validations (`domain/criteria/Criterion.java:128`, `JsonValues.java:23`, `CriterionParser.java:52`). La négation logique peut être intentionnelle ; elle ne doit pas servir de preuve de compétence ou d'habilitation.
3. **Isolation des organisations.** L'API est volontairement ouverte. En plus de l'authentification future, les services doivent vérifier l'appartenance des références : création de scénario avec modèle d'un autre tenant, ressource avec type d'un autre tenant (`application/ReferentialService.java:169`, `ResourceCatalogService.java:126`). Les clés étrangères actuelles garantissent l'existence, pas l'appartenance. L'ensemble n'est pas prêt pour plusieurs clients réels.
4. **Historique et résultats.** Le rapport est figé, mais les noms et la grille du résultat sont relus dans le référentiel actuel ; supprimer une demande ou ressource cascade vers ses affectations historiques (`application/SolveService.java:164`, `:181`, migration V1:224, :226). Un planning diffusé doit conserver sa propre représentation et les suppressions ne doivent pas détruire silencieusement son historique.
5. **Concurrence et montée en charge.** Deux jobs du même scénario peuvent être soumis ; les statuts sont ensuite mis à jour sans arbitrage commun. Au démarrage, `OrphanJobRecovery` marque tous les jobs actifs de la base comme interrompus, y compris ceux d'une autre instance encore en train de travailler (`application/SolveService.java:83`, `repository/adapter/OrphanJobRecovery.java:37`). Une file en mémoire et cette reprise globale ne constituent pas encore une exploitation horizontale sûre. Prévoir propriétaire/bail du job, reprise ciblée, limites de file et cohérence des versions d'entrée.
6. **Priorités et équité.** Le score a deux niveaux ; le niveau dur/souple s'applique à une famille de plugin, pas séparément à chaque règle. Les charges sont équilibrées en minutes brutes, sans tenir compte du ratio de contrat. Temps partiel, temps plein, couverture et confort nécessitent des objectifs et niveaux définis, éventuellement un score plus hiérarchisé après essais métier.
7. **Tests et performance.** Trois plugins sur quatorze ont des tests dédiés ; la fixture de cohérence ne configure pas `time.weekly_regularity`. Les activités résolues du fixture durent une heure ; périodes vides, pauses internes et quantités multiples n'y sont pas éprouvées. Aucun résultat actuel ne prouve la performance sur un collège réel ou 2 000–5 000 affectations datées. Mesurer qualité faisable, durée, mémoire et stabilité sur des jeux métier représentatifs.

## 8. Ordre de travail recommandé

1. **Fiabilité du périmètre actuel.** Corriger les six cas reproduits et les transitions de résultat ; ajouter leurs tests ciblés. Vérifier les sauvegardes partielles et les attributs présentés comme actifs.
2. **Traduction métier sur le scolaire.** Remplacer la demande technique par un formulaire « enseignement à organiser », avec sauvegarde complète et emploi du temps lisible. Cela valide la couche de traduction sur le cas déjà compris.
3. **Socle daté et retouche.** Disponibilités datées, services traversant minuit, historique aux frontières de l'horizon, pinning/rechargement, remplacement et limitation des déplacements inutiles. Ce chantier sert aussi le scolaire.
4. **Restauration pilote bornée.** Commencer avec des services prédéfinis, besoins explicites par jour, compétences, repos et charges individuelles. Ouvrir l'optimisation de la forme des services seulement après validation de cette version.
5. **Entreprise précisément cadrée.** Réutiliser l'expérience horaires d'équipes lorsque le besoin correspond ; traiter séparément réunions, interventions ou production.
6. **Exploitation réelle.** Publication, historique fiable, exports, droits, isolation et benchmarks constituent des conditions de livraison, à prévoir dans les étapes concernées plutôt qu'après la diffusion.

Ne pas commencer par fabriquer deux nouvelles peaux autour des quatre écrans actuels. Le travail produit est de définir leurs tâches et la traduction ; le travail moteur est de corriger les hypothèses et d'étendre les décisions réellement nécessaires.

## 9. Décisions à cadrer pour la suite

1. Priorité de la prochaine phase : **fiabilité du moteur**, **parcours scolaire**, ou **pilote restaurant** ? La fiabilité reste un préalable à toute exploitation.
2. Sens du mode entreprise : **horaires d'équipes**, **réunions et ressources**, ou **projets/interventions** ? Ces choix changent la faisabilité et le périmètre, pas seulement les libellés.

