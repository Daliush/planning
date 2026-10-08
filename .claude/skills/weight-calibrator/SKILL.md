---
name: weight-calibrator
description: Ajuste les poids des contraintes du moteur de planification par résolutions comparatives. Utiliser quand l'utilisateur dit « le planning privilégie trop X », « il y a trop de trous », « les profs sont mal servis », « cette règle n'a aucun effet », « le planning est valide mais mauvais », ou demande d'arbitrer entre deux objectifs qui s'opposent.
---

# Calibrer les poids

La vraie concurrence n'est pas entre les emplois du temps, elle est **entre
objectifs**. Un bon emploi du temps pour les professeurs — journées groupées, peu
de trous — s'oppose souvent à un bon emploi du temps pour les élèves.

Le solveur ne tranche pas ce dilemme : il applique la décision que l'établissement
a exprimée en réglant les poids. Calibrer, c'est expliciter cet arbitrage.

---

## Les trois poids

```
pénalité = ① poids de la contrainte × ② poids de la règle × ③ ampleur
```

| | Portée | Où | Modifiable par |
|---|---|---|---|
| ① | un type de contrainte | `constraint_weight` | l'écran Règles |
| ② | une ligne de configuration | `constraint_config.weight` | l'écran Règles |
| ③ | une violation | calculée dans le plugin | code |

**Identifier le bon levier avant de toucher quoi que ce soit.**

- « Les trous comptent trop » → ① sur `gaps.avoid`.
- « Les trous des élèves doivent compter plus que ceux des profs » → ② sur les deux
  lignes de `gaps.avoid`. Un seul plugin, donc un seul poids ① : c'est le poids ②
  qui fait la différence.
- « Dépasser de 3 h devrait coûter plus que dépasser d'1 h » → c'est déjà le cas
  si le plugin a un gradient. Sinon c'est un défaut du plugin, pas un calibrage.

---

## Règles de calibrage

**Seul le rapport compte, jamais les valeurs absolues.** `(30, 200)` et `(3, 20)`
produisent exactement le même planning.

**Garder l'écart maximal dans deux ordres de grandeur.** Au-delà, la contrainte
dominante écrase tout et le solveur cesse de progresser sur le reste.

Ordres de grandeur de départ :

| Enjeu | Poids ① indicatif |
|---|---|
| Confort individuel | 10 |
| Préférence forte | 50 |
| Enjeu important | 200 |

**Un poids à 0 désactive la contrainte.** C'est la façon propre de neutraliser une
règle sans supprimer ses lignes de configuration.

**Une contrainte est soit dure, soit souple, jamais les deux.** Le score étant
lexicographique, dès qu'une violation est comptée en dur la solution est invalide
et les points souples ne sont plus jamais regardés. Pour obtenir des paliers — 7 h
déconseillé, 9 h interdit — utiliser deux lignes de configuration sur deux plugins
distincts, pas deux niveaux sur une contrainte.

---

## Procédure

### 1. Établir la référence

```bash
SC=<scenario>
lancer () {
  JOB=$(curl -s -X POST http://localhost:8081/api/scenarios/$SC/solve-jobs \
        -H 'Content-Type: application/json' -d '{"timeLimitSeconds":60}' \
        | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
  while true; do
    S=$(curl -s http://localhost:8081/api/solve-jobs/$JOB | python -c "import sys,json; print(json.load(sys.stdin)['status'])")
    case "$S" in DONE|FAILED) break;; esac
    sleep 5
  done
  curl -s http://localhost:8081/api/solve-jobs/$JOB/report | python -c "
import sys, json
r = json.load(sys.stdin)['report']
print('  dur %d / souple %d' % (r['hardScore'], r['softScore']))
for k, v in sorted(r['penaltyByConstraint'].items(), key=lambda x: -x[1]):
    print('    %-28s %8d' % (k, v))
"
}
lancer
```

**Utiliser le même budget de temps pour toutes les mesures.** Comparer une
résolution de 30 s à une de 120 s ne dit rien sur les poids.

### 2. Lire le tableau des pénalités

C'est le seul diagnostic qui compte. Trois lectures possibles :

- **Une contrainte écrase les autres** — son poids est trop élevé, ou sa
  configuration est incohérente. Une règle massivement violée signale plus souvent
  une erreur de saisie qu'un planning difficile.
- **Une contrainte est absente du tableau** — elle n'est jamais violée. Soit elle
  est satisfaite naturellement, soit son poids est à 0, soit son scope ne vise
  personne. Vérifier avec `infeasibility-diagnoser`, étape 7.
- **Le total est dominé par une contrainte qu'on juge secondaire** — c'est le cas
  qui appelle un recalibrage.

### 3. Changer un seul poids à la fois

```bash
curl -s -X PUT http://localhost:8081/api/scenarios/$SC/constraint-weights \
  -H 'Content-Type: application/json' \
  -d '{"constraintId":"gaps.avoid","level":"SOFT","weight":200}'
```

Relancer, comparer. **Changer deux poids simultanément rend le résultat
ininterprétable.**

### 4. Vérifier que le dur n'a pas bougé

Un recalibrage souple ne doit jamais dégrader le score dur. S'il le fait, le budget
de temps était trop court pour l'une des deux mesures : recommencer avec un budget
plus généreux.

---

## Avant de conclure à un problème de poids

Trois causes se déguisent en mauvais calibrage :

**Une contrainte dure qui bloque la recherche.** Si une règle dure rend invalide
tout pas intermédiaire vers une meilleure solution, aucun poids souple ne
débloquera quoi que ce soit. Symptôme : le score stagne très tôt, et la solution
comporte des défauts qu'un humain corrigerait à la main. Voir
`infeasibility-diagnoser`, étape 5.

**Un scope qui ne vise personne.** La règle semble sans effet parce qu'elle ne
s'applique à aucune ressource. Aucune erreur n'est levée.

**Un vivier trop étroit.** Si une seule salle convient à un cours, aucun poids ne
créera de marge de manœuvre. Lire les tailles de vivier dans les logs du builder.

---

## Quand le calibrage devient impossible

Si tu te retrouves à vouloir des rapports de 1 à 1000 — typiquement parce qu'une
obligation contractuelle doit dominer intégralement un confort sans pour autant
être dure — **c'est le signal qu'il faut passer à `BendableScore`**, qui offre N
niveaux durs et M niveaux souples hiérarchisés.

Ce changement implique de reprendre **toutes** les contraintes. Il se tranche tôt,
pendant les premiers essais sur données réelles, pas après la mise en production.

---

## Conclure

Présenter un tableau avant/après : score dur, score souple, et pénalité par
contrainte. Indiquer explicitement quel arbitrage a été choisi et ce qu'il coûte à
l'autre partie — « les élèves gagnent 40 % de trous en moins, les professeurs
perdent une journée groupée sur deux ».

Ne jamais annoncer un poids « meilleur » dans l'absolu : il n'y a que des
arbitrages, et c'est l'établissement qui les fait.
