# Démonstrations réalistes

[Le script SQL](../database/seeds/workforce-realistic.sql) ajoute trois espaces
fictifs pour une semaine complète, du lundi au dimanche. Il reste séparé des
migrations Flyway : les installations n'ont pas à recevoir ces données.

```powershell
./scripts/dev.ps1 -Action start -Mode dev -SeedDemo
```

Cette commande démarre l'API et PostgreSQL habituels, puis Vite dans Docker avec
rechargement des sources frontend. Elle charge les exemples pour le prochain lundi.
Pour fixer la semaine : ajouter `-DemoWeekStart 2026-10-12`. Pour charger uniquement
les données sur une instance déjà démarrée :

```powershell
./scripts/seed-demo.ps1 -WeekStart 2026-10-12
./scripts/validate-demo.ps1 -WeekStart 2026-10-12
```

Choisir un établissement **Démo réaliste** dans le sélecteur Restaurant ou
Entreprise, puis lancer le calcul. Le validateur crée déjà un résultat dans chaque
exemple et compare les heures réellement planifiées aux contrats.

## Restaurant : Le Comptoir

Ouvert du mercredi au dimanche, avec préparation et service de 11 h 30 à 15 h,
puis de 18 h à 21 h 30. Chaque service demande une responsable, deux personnes
en cuisine et trois en salle. Les pauses ne sont pas comptées comme travail.
Le samedi, la mise en place du banquet nécessite trois heures supplémentaires en
salle, de 15 h à 18 h. Le repos minimum est de 11 h, la limite quotidienne de 10 h.

| Employé | Contrat | Absence | Travail attendu dans la référence |
|---|---|---|---|
| Élodie, responsable | 35 h | — | 35 h |
| Karim, cuisine | 35 h | — | 35 h |
| Nora, cuisine | 35 h, jusqu'à 3 h supplémentaires autorisées | — | 35 h |
| Louise, salle | 35 h | — | 35 h |
| Hugo, salle | 35 h, jusqu'à 3 h supplémentaires autorisées | — | 38 h, dont les 3 h du banquet |
| Inès, salle | 35 h | Congé vendredi, 7 h déduites | 28 h |
| Maya, renfort salle | 7 h | — | 7 h le vendredi, en remplacement d'Inès |

Les besoins représentent **213 h**, pour **210 h dues après absence + 3 h de
supplément autorisé**. Les 61 postes sont couvrables, sans contrat incomplet.
La semaine complète évite d'introduire un contrat de 35 h dans un horizon trop court.

L'espace **Deux tensions** reprend cette référence et ajoute seulement :

1. Un besoin de pâtisserie samedi de 15 h à 17 h, avec une compétence que personne
   ne possède : ce poste reste vacant.
2. Une indisponibilité d'Élodie dimanche de 18 h à 19 h : elle empêche son affectation
   sur le service indivisible de 18 h à 21 h 30. Le poste de responsable reste vacant,
   et Élodie perd 3 h 30 de son planning initial. Le moteur lui réattribue les
   **3 h de mise en place du banquet**, grâce à sa compétence salle : elle arrive
   à **34 h 30**, avec seulement **30 minutes manquantes**. Hugo reste alors à 35 h,
   sans supplément. Cette indisponibilité ne déduit aucune heure du contrat,
   contrairement à un congé ou une maladie.

Le résultat attendu conserve tous les autres contrats, la marge autorisée et les
protections de repos et de disponibilité. Il présente **deux postes vacants**, et
reste infaisable tant que ces problèmes ne sont pas corrigés. Ajouter une personne
qualifiée en pâtisserie exige aussi de définir un contrat adapté à son besoin.
Pour la responsabilité, prévoir un relais qualifié et du travail compatible pour
compléter les 30 minutes du contrat d'Élodie, ou corriger l'indisponibilité si elle
est erronée. Avec ces poids, le score HARD attendu est **−32** : deux postes vacants
et 30 minutes de déficit ; aucune protection HARD n'est violée.

## Entreprise : Atelier Horizon

Du lundi au vendredi : 9 h–12 h 30 et 13 h 30–17 h, soit 7 h de travail par jour.
Chaque plage demande deux personnes au support, deux en logistique, une à l'accueil
et une à la coordination.

- Sarah et Mehdi en logistique, Claire en coordination et Thomas à l'accueil : **35 h chacun**.
- Camille au support : contrat de 35 h, maladie mardi déduite à 7 h, donc **28 h dues**.
- Lila, renfort support à 7 h : remplace Camille le mardi et atteint **7 h**.
- Jules au support : contrat de 35 h avec 3 h supplémentaires possibles ; clôture mercredi
  de 17 h à 19 h, donc **37 h planifiées**, dont 2 h supplémentaires.

Les besoins représentent **212 h**, soit **210 h dues + 2 h de supplément**.
Les 61 postes sont couvrables et aucun contrat ne manque d'heures.

## Chargement et conservation

Le SQL utilise uniquement le schéma `planning` et une transaction. Le chargement
ne supprime aucun espace existant, aucune affectation ni aucun résultat. Les UUID
sont déterministes par exemple et semaine. Un deuxième chargement de la même
semaine conserve les données telles quelles, y compris une démo modifiée à la main.
Une autre semaine crée trois nouveaux espaces, avec de nouveaux identifiants.

Le validateur vérifie les données de référence ; une démo modifiée peut donc faire
échouer ce contrôle. Il ne rétablit pas silencieusement les anciennes valeurs.
Pour tester un chargement neuf, utiliser un autre projet Compose ou une autre semaine.

Exécution SQL directe avec PostgreSQL et `psql` déjà disponibles :

```text
psql <connexion-à-la-base-Planning> -X -v ON_ERROR_STOP=1 -v week_start=2026-10-12 -f database/seeds/workforce-realistic.sql
```

`seed-demo.ps1` trouve la base et son utilisateur dans le projet Compose sélectionné,
puis exécute `psql` dans son conteneur : aucun client PostgreSQL hôte n'est requis.

## Validation effectuée le 9 octobre 2026

La semaine du 12 au 18 octobre a été chargée sur l'instance locale et sur une
stack de développement isolée, avec PostgreSQL migré jusqu'à V8. Les trois cas
ont été calculés avec un budget de 15 secondes chacun sur cette stack, à travers
le proxy Vite et avec un en-tête `Origin` correspondant à son port personnalisé.
Le score de chaque job correspond à son rapport.

| Exemple | HARD | Postes vacants | Contrats incomplets |
|---|---|---|---|
| Restaurant équilibré | 0 | 0 | 0 |
| Entreprise équilibrée | 0 | 0 | 0 |
| Restaurant avec deux tensions | −32 | 2 | Élodie, 30 minutes |

Le démarrage `-Mode dev -SeedDemo` a été vérifié sur les ports habituels et sur
des ports personnalisés. Le contrôle de santé accepte les réponses OPTIONS 200
de nginx et 204 de Vite. Le rechargement automatique a été vérifié en modifiant
un module temporaire monté, puis en observant la notification WebSocket et le
contenu actualisé ; ce module a été supprimé après contrôle.

Un second chargement conserve les trois scénarios, leurs 21 employés et leurs
trois jobs de validation, sans duplications. Le build TypeScript/Vite dans Node 22,
la syntaxe PowerShell, l'audit d'autonomie et le validateur du skill réussissent.
Les journaux détaillés sont conservés localement dans `.local/validation-demo.log`,
`.local/start-dev-validation-final.log` et `.local/build-dev-frontend.log`.
