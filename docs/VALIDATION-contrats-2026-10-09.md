# Validation — contrats, absences et indisponibilités horaires

Validation locale du 9 octobre 2026. Données fictives dans des espaces isolés.

## Comportement livré

- Contrat hebdomadaire obligatoire : minimum égal au contrat, maximum égal au
  contrat augmenté des heures supplémentaires autorisées par la personne.
- Chaque date d'absence enregistrée déduit la durée par jour choisie dans la fiche.
  Le crédit est plafonné au contrat, par semaine ; les jours ne sont comptés qu'une
  fois. La marge supplémentaire reste disponible après déduction.
- Les indisponibilités récurrentes limitent les créneaux mais ne réduisent pas
  les heures dues. Elles acceptent des journées, des heures et des plages nocturnes.
- Les cibles et bilans hebdomadaires sont projetés côté serveur ; le frontend
  affiche heures dues, attribuées, manquantes et supplémentaires.
- Un résultat infaisable affiche **Planning à compléter**. Une modification
  masque le résultat précédent et demande un nouveau calcul.

## Tests et build

`mvn -B test` : **161 tests, 0 échec, 0 erreur, 0 ignoré**.

Les résolutions `FULL_ASSERT` contrôlent les protections, les contrats exacts,
la marge supplémentaire, les absences, les indisponibilités horaires et nocturnes.
Le score incrémental est confronté au recalcul complet, puis au rapport métier.
Les tests de compilation couvrent les bornes, les jours sans créneau, les doublons
d'absence et l'immuabilité du dictionnaire hebdomadaire. Les paramètres JSON et
constructeurs précédents restent utilisables ; les tests scolaires sont conservés.

`npm run build` : TypeScript et Vite réussis, 76 modules, JavaScript 296,80 kB
(89,87 kB compressés). Contrôle du diff réussi.

## Base PostgreSQL et migration

PostgreSQL 16.13, conteneur local `planning-v2-db`, port 5434, base `planning_v2`.
Flyway a validé les migrations existantes puis appliqué **V8 avec succès**.

Une revue indépendante a identifié des codes d'employé dupliqués susceptibles
de rendre les scopes ambigus. V8 exclut ces scénarios. Reproduction en SELECT/CTE :
le scénario simulé est sélectionné sans cette garde et exclu avec elle.

14 espaces de façade existants correspondaient aux anciennes politiques reconnues.
Les réglages métier repos/jour/équilibrage sont conservés. Une personnalisation
experte divergente reste hors migration. Les scénarios concernés sont remis en
brouillon ; les résultats historiques sont conservés.

Empreintes avant/après identiques pour le scénario scolaire et le scénario expert
de contrôle : poids, configurations, attributs de ressources et statut. V1–V7
ne présentent aucun diff. Aucun candidat n'est encore sélectionné après V8.

## Parcours API réels

`scripts/validate-contracts.ps1` réussit avec le scénario
`4ab999b4-bd4e-4ac6-ad92-a6a264b714d9` :

| Cas | Résultat contrôlé |
|---|---|
| 5 postes de 7h, contrat 35h | 35h, résultat faisable, aucun manque ni supplément. |
| Renfort supplémentaire de 3h, marge 0 | 35h, un poste vacant, résultat infaisable. |
| Même renfort, marge 3h | 38h, résultat faisable, 3h supplémentaires. |
| Une maladie comptée à 7h | 28h dues, plafond 31h, 31h attribuées hors absence. |
| Indisponibilité jeudi 11h–12h | Cible toujours 28h, 24h attribuées, manque 4h, résultat infaisable. |
| Indisponibilité samedi 22h–2h | Plage enregistrée et relue sans altération. |

Chaque résolution compare exactement score du job et score du rapport.
`scripts/validate-workforce.ps1` réussit aussi : restaurant, entreprise, nuits,
maladie, poste court pendant une absence, résultat obsolète et résolution scolaire.

## Vérification de l'interface

Navigateur de l'application, backend réel. Contrôles ordinateur et téléphone :
formulaire 35h + 3h de supplément + 7h par jour d'absence, ajout de plage horaire,
mention de fin le lendemain, reprise des plages enregistrées, enregistrement de
la fiche, masquage des anciens résultats, recalcul et affichage du manque de 4h.
Les changements saisis dans Alice Martin pour la capture ont été annulés.

Un bouton de modification sans nom sur mobile et un message de réussite pour
un résultat infaisable ont été corrigés. Le build final inclut ces corrections.
Le détecteur de l'agent UI n'a signalé aucun résultat sur son périmètre initial.

Captures finales sous `.impeccable/review/captures/` :

- `contrat-restaurant-desktop.png` et `contrat-entreprise-desktop.png` : 1265×889.
- `contrat-restaurant-mobile.png` et `contrat-entreprise-mobile.png` : 375×812.

Viewport demandé : 1280×900 / 390×844 ; normalisation du navigateur lors de la
capture. Les dimensions des quatre fichiers ont été contrôlées sur disque.
L'override temporaire a été retiré.
Revue finale indépendante de cette extension : **disposition: ship**, aucune
correction matérielle restante. Les deux captures mobiles initialement invalides
ont été remplacées avant ce verdict ; aucune source n'a changé après les captures finales.

## Limites et runtime

Les semaines partielles gardent leur contrat entier après absences. Chaque date
incluse dans un intervalle d'absence compte, y compris un repos habituel. La fiche
propose contrat/5 arrondi à 30 minutes comme suggestion modifiable. Une plage de
travail reste indivisible : il faut saisir plusieurs besoins pour prévoir un relais.

La console experte peut modifier la politique de contraintes ; les scénarios déjà
personnalisés sont préservés et ne sont pas convertis automatiquement par V8.

Services de prévisualisation conservés pour la livraison : frontend
`http://127.0.0.1:5174` (PID 29640), backend `http://localhost:8081` (PID 35320).
Arrêt des processus : `Stop-Process -Id 29640,35320` après vérification des PID.
Base : `docker stop planning-v2-db`. Les suites de validation sont terminées.
