---
name: infeasibility-diagnoser
description: Diagnostique pourquoi une résolution de planning a échoué, n'a produit aucune solution, ou a rendu un planning avec des violations dures. Utiliser dès qu'un utilisateur signale « pas de solution », « le planning est vide », « ça ne trouve rien », « le score ne bouge pas », « telle règle ne s'applique pas », ou qu'un solve_job est en statut FAILED. Couvre aussi les plannings aberrants qui semblent pourtant valides.
---

# Diagnostiquer une infaisabilité

Suivre les étapes dans l'ordre. Chacune élimine une classe de causes ; les
premières sont les plus fréquentes et les moins coûteuses à vérifier.

**Ne jamais « corriger » en ajoutant un filtre au vivier.** Une exclusion par vivier
est définitive et muette : elle déplacerait le problème et le rendrait
indiagnostiquable la fois suivante.

---

## Étape 0 — Récupérer les faits

```bash
# Le job et son code d'erreur
curl -s http://localhost:8081/api/solve-jobs/$JOB | python -m json.tool

# Le rapport, s'il existe
curl -s http://localhost:8081/api/solve-jobs/$JOB/report | python -m json.tool

# Les logs du builder : tailles de vivier, viviers étroits
docker logs planning-api 2>&1 | grep -A6 "Problème construit"
```

Le builder journalise systématiquement le nombre de créneaux, de ressources, de
séances, de postes et de règles, puis le vivier minimal par rôle, puis un
avertissement pour chaque activité dont les départs valides tombent sous 10 % de
l'horizon. **C'est la première chose à lire.**

---

## Étape 1 — Lire `error_code`

| Code | Cause | Suite |
|---|---|---|
| `INFEASIBLE_EMPTY_POOL` | Un vivier est vide | Étape 2 |
| `INFEASIBLE_NO_START` | Aucun départ valide pour une activité | Étape 3 |
| `INCOMPATIBLE_CONFIG` | Règle incompatible avec l'horizon | §14.5 — le message nomme la règle |
| `INCOMPATIBLE_TIME_MODEL` | Durée non représentable sur la grille | Étape 3 |
| `UNKNOWN_CONSTRAINT` | Poids ou config référençant un plugin inexistant | Un identifiant a été renommé |
| `INTERRUPTED` | Le service a redémarré pendant la résolution | Relancer |
| *(vide, statut DONE)* | Le solveur a tourné | Étape 4 |

Les messages sont déjà rédigés pour un utilisateur métier : les lire avant de
plonger dans le code.

---

## Étape 2 — Vivier vide

Le message nomme le rôle, l'activité et les critères. Vérifier **dans cet ordre** :

1. **Existe-t-il des ressources du bon `type_id` ?** C'est la cause la plus
   fréquente. Un besoin de salle ne regarde que les ressources de type salle.
2. **Les `criteria` portent-ils sur des attributs réellement présents ?** Un
   attribut absent fait échouer tous les critères, silencieusement.
3. **Le critère utilise-t-il `contains` sur un tableau, et non `eq` ?**
   `{"eq":{"path":"/matieres","value":"maths"}}` sur `["maths","physique"]` ne
   matche jamais. Il faut `contains`.
4. **Le chemin existe-t-il ?** Le moteur ne lit que `resource.attributes`. Le
   libellé affiché vit dans le champ fixe `label`, invisible aux critères. Les clés
   disponibles sont celles des attributs, plus `/type` projeté par le builder.

```bash
# Ce que le moteur voit réellement d'une ressource
curl -s http://localhost:8081/api/resources/$ID | python -c \
  "import sys,json; print(json.dumps(json.load(sys.stdin)['attributes'], indent=2, ensure_ascii=False))"
```

---

## Étape 3 — Aucun départ valide

1. **La durée est-elle un multiple du pas de la grille ?** Une durée de 90 min sur
   une grille de 60 ne retombe jamais juste. Le contrôle est volontaire : mieux vaut
   une exception explicite qu'un cours silencieusement rallongé.
2. **La durée tient-elle dans une plage d'ouverture continue ?** Vérifier
   `time_model.max_gap_minutes` : à 0, une activité ne peut pas enjamber une
   récréation. Une pause déjeuner reste infranchissable dans tous les cas.
3. **Une activité ne peut jamais déborder sur le jour suivant.**
4. **La stratégie de placement n'est-elle pas trop étroite ?** Un `window` de deux
   heures sur une grille où rien ne démarre dans cet intervalle donne zéro départ.

---

## Étape 4 — Le solveur a tourné mais laisse des violations dures

Regarder le **détail par contrainte**, jamais le score global.

```bash
curl -s http://localhost:8081/api/solve-jobs/$JOB/report | python -c "
import sys, json
r = json.load(sys.stdin)['report']
for k, v in sorted(r['penaltyByConstraint'].items(), key=lambda x: -x[1]):
    print('%-28s %8d' % (k, v))
print()
for v in r['hardViolations'][:10]:
    print('-', v['message'])
"
```

La contrainte dominante indique la nature du blocage :

| Contrainte dominante | Piste |
|---|---|
| `resource.conflict` | Trop de demandes pour trop peu de ressources. Comparer le nombre de bookings simultanés à la taille du vivier |
| `resource.availability` | Disponibilités trop restrictives. Certaines devraient-elles être `DISCOURAGED` plutôt qu'`UNAVAILABLE` ? |
| `load.max` | Quotas incompatibles avec le volume à planifier. Sommer les durées et comparer aux quotas |
| `resource.consistency` | Souvent en conflit avec les disponibilités : le même professeur ne peut pas couvrir toutes les séances d'un cours |
| `booking.unassigned` | Aucune ressource éligible n'est libre au moment retenu. Regarder d'abord les viviers |

---

## Étape 5 — Le score stagne tôt

Symptôme : plus de progression après quelques dizaines de secondes sur un budget de
plusieurs minutes, avec des violations qu'un humain corrigerait à la main en trente
secondes.

C'est la signature du **couplage des variables**. Vérifier dans cet ordre :

1. **Journaliser la taille des viviers.** Des viviers très étroits sont une cause
   plus fréquente que le couplage.
2. **Augmenter le budget** et observer si le score reprend.
3. **Chercher une contrainte dure qui interdit tout pas intermédiaire.** C'est le
   cas le plus subtil, et il a été observé sur ce jeu de données.

### Le cas vérifié — `resource.consistency`

Un enseignant sous-chargé ne reçoit aucune séance, alors que `load.min` le signale
et que la pénalité est élevée. Cause : `resource.consistency` est **dure**. Changer
l'enseignant d'un seul des quatre postes d'un cours crée aussitôt une violation
dure, et le dur domine lexicographiquement. Aucun premier pas n'est acceptable,
alors que l'arrivée est bien meilleure.

**Test de confirmation** — basculer la contrainte en souple et relancer :

```bash
curl -s -X PUT http://localhost:8081/api/scenarios/$SC/constraint-weights \
  -H 'Content-Type: application/json' \
  -d '{"constraintId":"resource.consistency","level":"SOFT","weight":300}'
```

Si le blocage se lève, le diagnostic est confirmé. Sur le jeu mocké, le score
souple est passé de −17 750 à −8 450, **la continuité restant de fait respectée** :
la contrainte ne protégeait pas le résultat, elle bloquait la recherche.

Trois parades, par coût croissant :

1. Basculer la contrainte en souple avec un poids élevé — une ligne de
   configuration, et souvent suffisant.
2. Modéliser le besoin en demande dédiée : des `criteria` qui ne matchent que la
   ressource visée. Garanti, mais fige qui fait quoi.
3. Implémenter un `cartesianProductMoveSelector` restreint, qui change créneau et
   ressource d'un coup. C'est l'artillerie ; elle bénéficierait à tout le reste.

---

## Étape 6 — Planning valide mais aberrant

Le moteur a bien travaillé ; il manque une règle. **Une donnée n'existe pour le
solveur que par la contrainte qui la consulte** — des données parfaites sans plugin
pour les lire ne produisent rien.

| Symptôme | Plugin manquant ou à poids nul |
|---|---|
| Professeurs différents sur les séances d'un même cours | `resource.consistency` |
| Les 4 séances hebdomadaires tombent le même jour | `demand.spread` |
| Emplois du temps criblés de trous | `gaps.avoid` |
| Séances d'un cours à des horaires différents chaque semaine | `time.weekly_regularity` |
| Journées à rallonge | `day.span_max` |
| Un enseignant surchargé, un autre à vide | `load.balance`, `load.min` |

Vérifier aussi que le poids ① n'est pas à 0 :

```bash
curl -s http://localhost:8081/api/scenarios/$SC/constraint-weights | python -c "
import sys, json
for w in json.load(sys.stdin):
    flag = '  (défaut)' if w['fromDefault'] else ''
    print('%-28s %-5s %5d%s' % (w['constraintId'], w['level'], w['weight'], flag))
"
```

---

## Étape 7 — « Ma règle ne s'applique pas »

Cas fréquent et entièrement silencieux : **le scope ne vise personne.**

Le moteur de critères ne lit que `resource.attributes` — ou `demand.attributes`
pour les plugins qui parlent de la demande. Un scope portant sur une clé absente ne
lève aucune erreur et ne matche rien.

Vérifier que le scope désigne réellement quelqu'un :

```bash
curl -s http://localhost:8081/api/scenarios/$SC/resources | python -c "
import sys, json
cible = ('statut', 'vacataire')          # ← adapter à la clé et la valeur du scope
res = [r for r in json.load(sys.stdin) if r['attributes'].get(cible[0]) == cible[1]]
print(len(res), 'ressource(s) visée(s) :', [r['label'] for r in res])
"
```

Si le compte est 0, le scope est en cause. Erreurs classiques :

- une clé qui n'existe pas dans les attributs — `/name` au lieu de `/code` ;
- `eq` au lieu de `contains` sur un tableau ;
- une valeur mal orthographiée ou de casse différente ;
- un scope adossé à un libellé d'affichage, défait par un renommage.

---

## Conclure

Indiquer la cause identifiée, la commande qui l'a confirmée, et la correction —
donnée, configuration ou code. **Ne jamais conclure sans avoir relancé une
résolution** et constaté le changement de score.

Si la correction relève d'une règle manquante, enchaîner sur le skill
`constraint-plugin-creator`. Si elle relève d'un arbitrage entre règles existantes,
sur `weight-calibrator`.
