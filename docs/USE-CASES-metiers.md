# Usages métier — restauration et entreprise

Les deux parcours utilisent le moteur générique de `planning`. Un espace regroupe
un établissement, une période datée et un scénario. Les données sont enregistrées
côté serveur ; une démonstration est créée uniquement sur demande et porte ce nom.

## Restaurant : préparer les services et leurs remplacements

1. Créer un espace **Restaurant** pour la période à couvrir.
2. Ajouter les employés, leurs compétences (`cuisine`, `salle`, `responsable`…) et
   leur volume hebdomadaire de contrat, leurs heures supplémentaires autorisées
   et la durée déduite par jour d'absence. Renseigner leurs jours ou plages horaires
   habituellement indisponibles dans la fiche d'équipe.
3. Ajouter les services avec une date et des horaires. Un midi peut demander
   1 responsable, 2 personnes en salle et 1 en cuisine ; le samedi soir peut en
   demander davantage. Chaque rôle indique sa quantité et sa compétence requise.
4. Enregistrer un congé ou une maladie sur ses dates. Le calcul peut réattribuer
   les postes à d'autres personnes éligibles ou laisser un besoin non couvert.
5. Calculer, puis lire la couverture, les affectations et les violations. Modifier
   un besoin ou une disponibilité et relancer le calcul pour actualiser le résultat.

Un service 22h–2h se saisit avec **Fin le lendemain**. Une absence commençant le
lendemain interdit aussi la portion du service après minuit. Le repos est mesuré
depuis l'heure réelle de fin, y compris pour la reprise du service suivant.

## Entreprise : couvrir les plages d'une équipe

Créer un espace **Entreprise**, puis définir les compétences utiles : accueil,
support, coordination ou logistique, par exemple. Une plage 8h–12h et une plage
13h–17h constituent deux besoins indépendants, avec leurs postes respectifs.

Un employé à temps partiel peut avoir une cible de 24h/semaine, un autre de 35h.
Le moteur utilise leurs compétences pour l'éligibilité, leurs disponibilités et
absences pour les restrictions, et leurs contrats pour les règles de charge.
Changer une compétence ou ajouter un congé ne modifie pas les besoins de l'équipe :
cela modifie les personnes susceptibles de les couvrir au prochain calcul.

Le planning par personne montre les plages attribuées. Une couverture complète
ne suffit pas à conclure qu'un planning est acceptable : les violations du rapport
doivent aussi être examinées, notamment le repos, les conflits et les plafonds.

## Du formulaire au moteur

| Saisie métier | Données ou règle utilisées |
|---|---|
| Établissement et période | `tenant`, type `employee`, `time_model` DATED, grille de 30 min et `scenario`. |
| Employé et compétences | `resource`, attribut `competences`. Un code UUID stable permet de cibler ses règles après renommage. |
| Contrat hebdomadaire | `quota_hebdo_minutes`, minimum HARD `load.min` WEEK et cible SOFT `load.contract`. |
| Heures supplémentaires autorisées | `heures_supp_hebdo_minutes`, plafond HARD `load.max` WEEK égal au contrat augmenté de cette marge. |
| Durée par jour d'absence | `minutes_absence_jour`, crédit déduit de la cible et du plafond hebdomadaires, limité au contrat. |
| Jours ou heures habituellement indisponibles | `resource_availability`, jours de semaine 0–6 et horaires, compilés en `BitSet`. Une plage 22h–2h se poursuit le lendemain. |
| Date et horaires d'un service | Une `demand`, une occurrence, durée en minutes, placement `fixed` avec `date` et `startTime`. |
| Rôle, quantité et compétence requise | Un `requirement` par rôle ; la quantité crée les postes, le critère `contains /competences` fixe leur vivier. |
| Congé, maladie ou autre absence | `absence`, intervalle de dates inclusif ; retire les créneaux concernés et déduit la durée par jour de chaque semaine concernée. |
| Repos et maximum quotidien | `rest.min` et `load.max` DAY. Les quotas ventilent les nuits selon les périodes réellement travaillées. |
| Équilibrer la répartition du travail | Active ou désactive `load.balance` ; la cible contractuelle et l'équité des week-ends restent actives. |
| Lancer le calcul | Un `solve_job` asynchrone ; affectations et rapport sont conservés à sa fin. |

Les créations d'employé et de service regroupent leurs écritures dans une
transaction. La façade refuse une ressource d'un autre scénario ou établissement,
ainsi que la modification d'un scénario scolaire par ces routes métier.

## Limites actuelles

- **Période métier : 1 à 28 jours**, dates de début et de fin inclusives. L'interface
  propose 7, 14 ou 28 jours ; l'API accepte les autres durées dans cette borne.
- **Grille : 30 minutes.** Horaires et durées saisis doivent respecter ce pas.
  Un service dure de 30 minutes à 24h. Sa fin doit rester dans la période ; l'API
  accepte une fin exactement à minuit comme borne de fin du jour précédent.
- **Temps local sans fuseau.** Les calculs utilisent des dates et heures locales.
  Les changements d'heure et les équipes réparties sur plusieurs fuseaux nécessitent
  une extension du modèle temporel ; ils ne sont pas résolus par ces plugins.
- **Absence : journées entières**, de `from` à `to` incluses. Les absences partielles,
  justificatifs et soldes de congés ne sont pas gérés. Les jours doivent appartenir
  à la période ; une même personne ne peut pas avoir deux absences qui se chevauchent.
- **Contrat obligatoire.** Sans absence ni supplément autorisé, un contrat de 35h
  doit aboutir à 35h. Avec 3h supplémentaires autorisées, la plage admise est de
  35h à 38h ; la cible préférée reste 35h. Les besoins saisis doivent fournir
  suffisamment de travail : le moteur ne crée pas de postes pour compléter un contrat.
- **Déduction des absences.** La fiche suggère contrat/5, arrondi à 30 minutes,
  comme durée par jour ; cette valeur est modifiable et ne constitue pas un
  calendrier contractuel. Chaque date d'absence saisie compte, y compris un
  week-end inclus dans l'intervalle. Le crédit de chaque semaine est plafonné au
  contrat. Pour 35h, une journée comptée à 7h laisse 28h dues ; avec 3h de supplément,
  le maximum devient 31h. Les indisponibilités récurrentes ne déduisent aucune heure.
- **Semaine partielle.** Une semaine partiellement couverte par la période garde
  son contrat complet, après déduction des absences enregistrées. Aucun prorata
  selon les jours de la période n'est inventé. Préférer des semaines complètes.
- **Plages indivisibles.** Le moteur affecte un poste pour toute sa durée. Une
  indisponibilité d'une heure au milieu d'un poste empêche son affectation complète ;
  créer des besoins distincts si plusieurs personnes doivent se relayer.
- **Équité limitée au scénario courant.** `fairness.weekend` compte une fois un
  week-end touché, même si samedi et dimanche sont travaillés. Aucun historique
  des week-ends ni des heures des autres scénarios n'est chargé.
- **Repos regroupé par date de début.** Les postes démarrant le même jour forment
  une journée ; `rest.min` compare sa fin avec le début de la journée suivante.
  `work.days_max` limite les jours calendaires consécutifs occupés, nuits comprises.
- **Résultat à examiner.** Un budget de calcul court peut produire un planning
  partiel ou avec violations. HARD/SOFT hiérarchisent le score ; une règle HARD ne
  garantit pas à elle seule un résultat sans violation lorsqu'il manque du personnel.
- **Périmètre fonctionnel.** Pas de paie, de contrôle automatique des conventions
  collectives, d'import SIRH, d'export iCal ou de transfert automatique de l'équipe
  vers une nouvelle période. L'authentification de l'API n'est pas encore implémentée.

Les démonstrations créées par l'API ajoutent 10 employés en restauration ou 8 en entreprise,
des besoins sur les sept premiers jours au maximum et des absences illustratives.
Leur équipe et leurs besoins sont modifiables ; les absences peuvent être ajoutées
ou retirées comme dans un espace vide.

Pour vérifier des contrats atteignables et des tensions précisément identifiées,
utiliser le [jeu SQL réaliste](DEMO-realiste.md), chargé séparément dans ses propres
espaces de démonstration.

## Parcours scolaire conservé

La console **Scolaire / Expert** reste sous `/expert`, avec le tenant
`college-demo`, le mode CYCLIC et ses écrans de référentiel, demandes, règles et
résolution. Les anciens liens `/referentiel`, `/demandes`, `/regles` et `/resolution`
y redirigent. Les exemples métier se créent dans de nouveaux établissements et
ne remplacent pas le jeu de données scolaire.
