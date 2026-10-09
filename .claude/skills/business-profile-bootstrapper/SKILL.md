---
name: business-profile-bootstrapper
description: Amorce une nouvelle verticale métier dans le module planning — types de ressources, modèle temporel, catalogue de règles et poids. Utiliser quand l'utilisateur dit « on attaque les restaurants », « il nous faut un profil entreprise », « on veut planifier autre chose que des cours », ou demande de créer un nouveau tenant avec ses données.
---

# Amorcer une verticale métier

Les verticales partagent le moteur. Elles changent **le modèle temporel**, **les
types de ressources** et **le catalogue de règles actives**. Elles ne changent pas
le noyau.

Le seul Java légitime dans un amorçage est **déclaratif** : enrichir
`AttributeCatalog` du vocabulaire de la verticale (étape 1). Si l'amorçage exige
d'écrire une *règle* — un plugin, un opérateur de critère — c'est que le besoin
dépasse la paramétrisation : s'arrêter et le dire.

---

## Étape 0 — Cadrer le besoin

Cinq questions, dans cet ordre. **Y répondre avec l'utilisateur avant tout
`INSERT`.**

1. **Qu'est-ce qu'on planifie ?** Une occurrence à placer devient une `demand` avec
   ses `occurrences` et sa `duration_minutes`.
2. **De quoi chaque occurrence a-t-elle besoin ?** Chaque poste devient un
   `requirement` : un rôle, un type accepté, une quantité, des critères.
3. **Quelles ressources existent, et qu'est-ce qui les distingue ?** Ce qui varie
   d'un individu à l'autre va dans `attributes`. Ce que le moteur lit en boucle
   chaude — la disponibilité — va dans `resource_availability`.
4. **Comment le temps est-il découpé ?** Créneaux réguliers ou irréguliers ?
   Longueur du cycle ? Une coupure peut-elle être enjambée ?
5. **Les absences sont-elles collectives et alignées, ou individuelles et datées ?**
   Choisir `CYCLIC` pour une rotation récurrente et `DATED` pour une période réelle.
   Les deux sont implémentés. Les absences individuelles sont des intervalles de
   dates inclusifs, compilés dans les disponibilités, nuits comprises.

Les parcours restaurant et entreprise disposent de la façade `/api/workforce`.
La réutiliser pour leurs créations atomiques plutôt que rejouer des écritures SQL.
Voir `docs/USE-CASES-metiers.md` à la racine du module pour les contrats et limites.

| Verticale | Ressources typiques | Spécificités |
|---|---|---|
| Scolaire | enseignants, salles, groupes | absences collectives alignées sur le cycle |
| Entreprise | employés, postes, véhicules, matériel | congés individuels datés, équité sur la durée, repos minimum |
| Restauration | personnel de salle, cuisine, postes | couverture par tranche horaire, pics d'affluence, rotations |

---

## Étape 1 — Le vocabulaire métier de la verticale

**Avant le moindre `INSERT`**, décider quels attributs la verticale manipule, et
les déclarer dans `domain/attribute/AttributeCatalog.java` :

```java
new AttributeDefinition("competences", "Compétences",
        "Ce que la personne sait faire. Un critère d'éligibilité y cherche la compétence exigée.",
        TEXT_LIST, List.of(), null, Set.of(COMPANY, RESTAURANT), Set.of()),
```

| Champ | Ce qu'il décide |
|---|---|
| `kind` | le widget du formulaire, et l'opérateur qui a du sens — `TEXT_LIST` impose `contains`, `TEXT` impose `eq` |
| `profiles` | les verticales où l'attribut est proposé |
| `resourceTypes` | les codes de type concernés ; **vide = tous les types du profil** |

C'est ce catalogue qui génère le **formulaire de saisie des ressources**. Un
attribut absent n'a aucun champ : il ne se saisit qu'en JSON brut, par l'éditeur
avancé. Rien ne casse — le jsonb reste libre — mais l'utilisateur métier est
renvoyé à un éditeur de texte.

**Le profil ne change rien au moteur.** Aucun plugin, aucun critère et aucune
contrainte ne lit `scenario.profile` : il ne filtre que le vocabulaire proposé, et
un scénario sans profil les propose tous. Une verticale qui aurait besoin d'être
lue par le noyau serait une valeur métier en dur, ce que le §2.6 interdit.

**Prévoir un `code` stable.** Il est déjà au catalogue, universel : c'est la clé
sur laquelle les scopes cibleront un individu. Le libellé affiché n'est pas lisible
par le moteur de critères, et un scope adossé à un libellé se déferait au premier
renommage.

---

## Étape 2 — Le tenant et les types de ressources

`resource_type` est le pivot de l'extension. Son `attr_schema` complète le
catalogue pour les attributs **propres à ce tenant** : le formulaire affiche
l'union des deux, le catalogue l'emportant sur les clés communes.

Un attribut que toute la verticale partage va au catalogue ; un attribut qu'un
seul client invente va dans `attr_schema`.

```sql
insert into planning.tenant (id, code, label, profile)
values (:tenant, 'brasserie-demo', 'Brasserie de démonstration', 'RESTAURANT');

insert into planning.resource_type (id, tenant_id, code, label, attr_schema) values
 (:t_serveur, :tenant, 'serveur', 'Personnel de salle', jsonb_build_object(
    'type', 'object',
    'required', jsonb_build_array('code', 'competences', 'statut'),
    'properties', jsonb_build_object(
      'code',        jsonb_build_object('type', 'string'),
      'competences', jsonb_build_object('type', 'array', 'items', jsonb_build_object('type','string')),
      'statut',      jsonb_build_object('enum', jsonb_build_array('cdi', 'extra')))));
```

Les clés déjà déclarées au catalogue peuvent être omises ici : elles seront
proposées de toute façon. Les répéter dans `attr_schema` ne sert qu'à leur donner
un `enum` ou un libellé propre à ce tenant.

**Ne pas mettre la disponibilité dans `attributes`.** Elle est lue en boucle chaude
et vit dans `resource_availability`, compilée en `BitSet`. Mettre la disponibilité
en jsonb serait une catastrophe de performance.

---

## Étape 3 — Le modèle temporel

La grille est **stockée**, pas calculée : les métiers réels ont des séquences
irrégulières, et une grille à pas constant ne peut pas les décrire.

**Restriction d'implémentation** : tous les créneaux d'un modèle doivent avoir la
**même durée**. Les intervalles entre eux restent libres — c'est ce qui permet
récréations, pause déjeuner et journées raccourcies.

Le plus simple est de passer par l'API, qui génère la grille :

```bash
curl -s -X POST http://localhost:8081/api/tenants/$TENANT/time-models \
  -H 'Content-Type: application/json' -d '{
    "label":"Journée type",
    "cycleDays":7, "workingDays":5,
    "dayStart":"08:00", "slotMinutes":60, "slotsPerDay":8,
    "maxGapMinutes":20,
    "breaks":[{"afterOrdinal":1,"minutes":15},
              {"afterOrdinal":3,"minutes":75},
              {"afterOrdinal":5,"minutes":15}]
  }'
```

**Une pause n'est pas un créneau, c'est un intervalle entre deux créneaux.**
`afterOrdinal` est l'index du créneau *après lequel* insérer la pause, à partir de
0 : la ligne ci-dessus produit 08:00–10:00, récréation, 10:15–12:15, déjeuner,
13:30–15:30, récréation, 15:45–17:45 — la grille du jeu de démonstration. La même
pause s'applique à tous les jours travaillés.

C'est ainsi qu'on décrit une grille irrégulière sans sortir du générateur : la
contrainte est que tous les créneaux aient la **même durée**, pas qu'ils soient
collés. Une journée particulière — le mercredi raccourci — se décrit en retirant
la disponibilité des ressources, jamais en tordant la grille.

`maxGapMinutes` est la politique de coupure, et elle se règle **en fonction des
pauses** : à 20, une séance de 2 h enjambe la récréation de 15 min mais jamais le
déjeuner de 75. À 0, rien n'enjambe rien. Une réunion client coupée en deux n'est
pas acceptable ; un cours de 2 h à cheval sur la récréation l'est. **C'est un
paramètre de métier, pas un détail technique.**

Compter le cycle **en jours, pas en semaines** : cela autorise les rotations qui ne
tombent pas juste — décadaires, cycles de 10 ou 21 jours.

---

## Étape 4 — Les ressources et leurs disponibilités

Deux archétypes opposés, choisis par `default_effect` :

- **« indisponible sauf »** — un extra, deux services par semaine. Défaut
  `UNAVAILABLE` + des règles d'ouverture.
- **« disponible sauf »** — un poste ouvert en continu sauf le lundi. Défaut
  `AVAILABLE` + des règles de fermeture.

Sans ce défaut, décrire le second exigerait de saisir tous ses créneaux
d'ouverture un par un.

Une colonne à `NULL` signifie « pas de restriction sur cet axe ». À priorité égale,
l'indisponibilité l'emporte.

Un troisième effet existe : `DISCOURAGED` — permis mais pénalisé. C'est le
mécanisme de bascule dur ↔ souple par ressource, sans toucher au code.

---

## Étape 5 — Les demandes

**Contrainte ou demande ?** C'est la distinction la plus importante. Une contrainte
juge une affectation qui existe ; une demande crée une case à remplir. Le solveur
ne crée jamais d'entités.

Le `quantity` d'un requirement est le mécanisme du niveau 0 :

```sql
insert into planning.demand (id, scenario_id, label, duration_minutes, occurrences, attributes, placement)
values (:d, :sc, 'Couverture service du soir', 300, 7,
        '{"service":"soir"}', '{"strategy":"fixed","startTime":"18:00"}');

insert into planning.requirement (id, demand_id, role, type_id, quantity, criteria) values
 (gen_random_uuid(), :d, 'chef_de_rang', :t_serveur, 1,
  '{"contains":{"path":"/competences","value":"chef_de_rang"}}'),
 (gen_random_uuid(), :d, 'serveur',      :t_serveur, 4, '{}');
```

« Il faut 5 personnes dont un chef de rang » se modélise ainsi — deux lignes. Le
builder boucle sur `quantity`, il ne sait pas qu'il crée 5 postes plutôt que 2.

**Garder le rôle distinct du type**, même quand ils se ressemblent. Les fusionner
est tentant et coûteux à défaire : `resource.consistency` raisonne sur le rôle.

---

## Étape 6 — Le catalogue de règles et les poids

Vérifier d'abord la compatibilité horizon/plugins :

```bash
curl -s http://localhost:8081/api/catalog | python -c "
import sys, json
for p in json.load(sys.stdin)['plugins']:
    restreint = '' if len(p['horizons']) > 1 else '  ← ' + p['horizons'][0] + ' uniquement'
    print('%-28s %-5s %s%s' % (p['type'], p['defaultLevel'], p['label'], restreint))
"
```

Poser **un poids ① par plugin du catalogue**, y compris ceux à 0 pour les règles qu'on ne veut pas :
c'est explicite, et ça évite qu'un défaut de plugin s'applique sans qu'on l'ait
choisi.

Puis les lignes de `constraint_config`. Commencer **petit** : les structurelles
suffisent à produire un planning cohérent. Ajouter les règles de confort une par
une, en mesurant à chaque fois — voir `weight-calibrator`.

---

## Étape 7 — Vérifier

1. **Lancer une résolution courte** et lire les logs du builder : tailles de vivier,
   activités aux départs rares.
2. **Vérifier les viviers** — `CYCLIC` refuse un vivier vide ; `DATED` conserve le
   poste vacant pour le rapport métier. Un poste sans personne éligible doit rester
   visible et expliqué, jamais être compté comme couvert.
3. **Lire le rapport**, pas seulement le score. Une règle absente du tableau des
   pénalités est soit satisfaite, soit muette.
4. **Contrôler que chaque scope vise réellement quelqu'un.** Un scope qui ne matche
   personne ne lève aucune erreur.

---

## Conclure

Livrer le script SQL ou la séquence d'appels API, le tenant créé, et le résultat
d'une première résolution avec son rapport. Indiquer explicitement les règles du
catalogue qui **ne** s'appliquent pas à cette verticale et pourquoi.

Les absences datées sont prises en charge. Une heure de début flexible se décrit
avec une stratégie de placement existante ; une durée variable au cours de la
résolution exige une extension du modèle. Les interfaces métier livrées utilisent
des horaires fixes et une grille de 30 minutes, sur 1 à 28 jours.
