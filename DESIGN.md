---
name: Planning — équipes et services
description: Un tableau de briefing clair, adapté au restaurant et à l’entreprise.
colors:
  company-cobalt: "#245bd6"
  company-cobalt-soft: "#eaf0ff"
  restaurant-plum: "#5d365b"
  restaurant-plum-soft: "#efe7ef"
  company-ink: "#202937"
  company-muted: "#657081"
  company-ground: "#f5f7fb"
  company-line: "#e4e8ee"
  restaurant-ink: "#332534"
  restaurant-muted: "#796b7a"
  restaurant-ground: "#faf7f9"
  restaurant-line: "#e9e0e7"
  paper: "#fff"
  restaurant-sidebar: "#3e2940"
  restaurant-sidebar-text: "#fbf5fb"
  restaurant-nav-active: "#735473"
  company-covered: "#29624c"
  company-covered-soft: "#edf5ef"
  restaurant-covered: "#35674f"
  restaurant-covered-soft: "#eef5ef"
  warning: "#865a1c"
  warning-soft: "#fff4de"
  danger: "#a23543"
  error-text: "#852d3a"
  error-surface: "#ffedf0"
  shift-blue-text: "#254e9e"
  shift-sage: "#edf4ef"
  shift-sage-text: "#39644b"
  shift-lilac: "#f3eef8"
  shift-lilac-text: "#755194"
typography:
  display:
    fontFamily: "Manrope, sans-serif"
    fontSize: "clamp(40px, 4.7vw, 67px)"
    fontWeight: 800
    lineHeight: 1.12
    letterSpacing: "-0.04em"
  headline-company:
    fontFamily: "Manrope, sans-serif"
    fontSize: "clamp(25px, 2.05vw, 34px)"
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: "-0.035em"
  headline-restaurant:
    fontFamily: "Literata, serif"
    fontSize: "clamp(28px, 2.6vw, 39px)"
    fontWeight: 550
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  title-service:
    fontFamily: "Manrope, sans-serif"
    fontSize: "17px"
    fontWeight: 750
    lineHeight: 1.5
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Manrope, sans-serif"
    fontSize: "14px"
    lineHeight: 1.5
  label-button:
    fontFamily: "Manrope, sans-serif"
    fontSize: "12px"
    fontWeight: 750
    lineHeight: 1.3
  label-status:
    fontFamily: "Manrope, sans-serif"
    fontSize: "10px"
    fontWeight: 750
    lineHeight: 1.5
  grid-time:
    fontFamily: "Manrope, sans-serif"
    fontSize: "11px"
    fontWeight: 750
    lineHeight: 1.5
    letterSpacing: "-0.02em"
  grid-label:
    fontFamily: "Manrope, sans-serif"
    fontSize: "11px"
    lineHeight: 1.5
  grid-employee:
    fontFamily: "Manrope, sans-serif"
    fontSize: "12px"
    fontWeight: 750
    lineHeight: 1.5
  grid-meta:
    fontFamily: "Manrope, sans-serif"
    fontSize: "10px"
    fontWeight: 500
    lineHeight: 1.5
  grid-availability:
    fontFamily: "Manrope, sans-serif"
    fontSize: "11px"
    lineHeight: 1.5
rounded:
  tag: "5px"
  shift: "6px"
  control: "7px"
  button: "8px"
  navigation: "9px"
  day: "10px"
  symbol: "12px"
  surface: "14px"
  dialog: "16px"
  avatar: "50%"
spacing:
  gap-small: "8px"
  gap-medium: "12px"
  gap-large: "20px"
  inset-small: "16px"
  inset-medium: "24px"
  inset-large: "28px"
  content-desktop-inline: "38px"
  content-mobile-inline: "19px"
components:
  button-primary-company:
    backgroundColor: "{colors.company-cobalt}"
    textColor: "{colors.paper}"
    typography: "{typography.label-button}"
    rounded: "{rounded.button}"
    padding: "10px 16px"
  button-primary-restaurant:
    backgroundColor: "{colors.restaurant-plum}"
    textColor: "{colors.paper}"
    typography: "{typography.label-button}"
    rounded: "{rounded.button}"
    padding: "10px 16px"
  button-secondary:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.company-ink}"
    typography: "{typography.label-button}"
    rounded: "{rounded.button}"
    padding: "10px 16px"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.company-muted}"
    typography: "{typography.label-button}"
    rounded: "{rounded.button}"
    padding: "10px 9px"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.paper}"
    typography: "{typography.label-button}"
    rounded: "{rounded.button}"
    padding: "10px 16px"
  field:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.company-ink}"
    rounded: "{rounded.control}"
    padding: "11px 12px"
    width: "100%"
  navigation-company-active:
    backgroundColor: "{colors.company-cobalt-soft}"
    textColor: "{colors.company-cobalt}"
    rounded: "{rounded.navigation}"
    padding: "12px 13px"
  status-covered-restaurant:
    backgroundColor: "{colors.restaurant-covered-soft}"
    textColor: "{colors.restaurant-covered}"
    typography: "{typography.label-status}"
    rounded: "{rounded.tag}"
    padding: "5px 8px"
  status-warning:
    backgroundColor: "{colors.warning-soft}"
    textColor: "{colors.warning}"
    typography: "{typography.label-status}"
    rounded: "{rounded.tag}"
    padding: "5px 8px"
  service-card:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.restaurant-ink}"
    rounded: "{rounded.surface}"
  day-selected-restaurant:
    backgroundColor: "{colors.restaurant-plum}"
    textColor: "{colors.paper}"
    rounded: "{rounded.day}"
    padding: "13px 10px 15px"
  shift-company:
    backgroundColor: "{colors.company-cobalt-soft}"
    textColor: "{colors.shift-blue-text}"
    typography: "{typography.grid-time}"
    rounded: "{rounded.shift}"
    padding: "11px 8px"
---

# Design System: Planning — équipes et services

## Overview

**Creative North Star: "Le tableau de briefing d’équipe"**

Un outil quotidien calme et précis : les personnes, les horaires et les postes à couvrir restent immédiatement repérables. Les surfaces claires, les séparations fines et les actions courtes privilégient la lecture des données.

Le restaurant associe le prune, le papier rosé et les grands titres littéraires. L’entreprise associe le cobalt, le papier froid et une typographie sans empattement. Ces variantes partagent leurs contrôles et états, tout en conservant leurs compositions métier.

**Key Characteristics:**

- Deux variantes métier reconnaissables, avec des contrôles communs.
- Une profondeur discrète, portée surtout par les surfaces et les bordures.
- Des dates et horaires alignés, des noms lisibles et des états explicités par du texte.
- Une navigation latérale qui devient une barre inférieure sur mobile.

Périmètre : le sélecteur de contexte et les écrans restaurant/entreprise de `frontend/src/features/workforce/`. La console scolaire sous `/expert/*` conserve son thème existant ; ces tokens ne s’y appliquent pas. La source des valeurs est `workforce.css`, les primitives React sont dans `components.tsx`. Cette documentation extrait l’implémentation ; elle ne remplace pas la vérification visuelle des écrans.

## Colors

Les accents identifient le métier et les actions ; les fonds restent pâles pour faire ressortir les informations du planning.

### Primary

- **Cobalt entreprise** : action principale, sélection et navigation active ; sa version pâle accueille les sélections secondaires.
- **Prune restaurant** : mêmes rôles, sur un fond rosé. Le prune plus profond de la barre latérale encadre l’espace de travail.

### Secondary

- **Sauge de couverture** : affectations réussies et états couverts, avec une nuance propre à chaque métier.
- **Ambre d’attention** : postes encore vacants et avertissements. L’état conserve un libellé visible.
- **Rouge d’erreur** : action de suppression et messages d’erreur ; le message utilise son propre fond pâle.
- **Bleu, sauge et lilas de plage** : différenciation des plages dans la grille entreprise. Ces couleurs ne constituent pas une échelle de réussite.

### Neutral

- **Papier blanc** : cartes, champs, barre supérieure et navigation entreprise.
- **Fond froid / fond rosé** : toile de l’entreprise / du restaurant.
- **Encre / encre secondaire** : informations principales / détails et aides, adaptés à chaque variante.
- **Lignes froides / rosées** : contours et séparateurs fins.

**The Coverage Rule.** Une couleur de couverture accompagne toujours un nom, un compte ou un libellé ; elle ne remplace pas l’information.

Les rampes tonales du sidecar sont dérivées pour l’aperçu ; elles ne constituent pas de nouvelles couleurs d’exécution.

## Typography

**Body Font:** Manrope, avec repli sans-serif. **Restaurant Headline Font:** Literata, avec repli serif. Les deux fontes variables sont servies localement depuis `frontend/public/fonts/`, avec `font-display: swap` et leurs licences SIL OFL.

Le titre restaurant est plus souple ; les noms, formulaires et horaires restent en Manrope. Literata n’est pas appliquée à tous les titres secondaires. Les tailles de détail varient selon la densité du planning, sans nouvelle police auxiliaire.

### Hierarchy

- **Display** : grand titre du choix de contexte ; sur mobile il passe à (43px).
- **Headline company / restaurant** : titres de page ; sur mobile ils passent respectivement à (27px) et (32px).
- **Title service** : nom d’un service ; sur mobile il passe à (15px).
- **Body** : base de l’espace de travail. Les descriptions de page utilisent (12px), avec une longueur maximale de (68ch).
- **Label button / status** : actions et états compacts. Les champs utilisent (12px) sur desktop et (16px) sur mobile.
- **Dates et horaires** : chiffres tabulaires dans le sélecteur de jour, les en-têtes de grille et les plages.
- **Grille entreprise** : les horaires, intitulés de plage et libellés d’absence/repos utilisent (11px) ; les noms de salariés utilisent (12px) et leurs détails (10px). Les fiches mobiles conservent leurs tailles spécifiques : horaires (11px), intitulés et repos (10px).

**The Sector Type Rule.** Réserver Literata aux grands titres restaurant ; maintenir Manrope pour les données et contrôles partagés.

## Layout

Le bureau utilise une barre latérale collante (222px), une barre supérieure et un contenu centré limité à (1680px). Les marges du contenu sont (34px 38px 60px). Les cartes de service empilent des lignes de postes ; la grille entreprise utilise une première colonne de personnes et des colonnes de jours, avec défilement horizontal à partir de sa largeur minimale (850px).

Les adaptations sont explicites : à (1200px), la barre latérale passe à (195px) et les marges horizontales à (25px) ; à (950px), elle devient une colonne d’icônes (78px). À (700px), elle devient une barre inférieure, le contenu adopte des marges (26px 19px 38px), les lignes restaurant s’empilent et la grille entreprise laisse place à des fiches quotidiennes. Le sélecteur de jours reste défilable horizontalement.

Les boutons ont une hauteur minimale de (41px), puis (43px) sur mobile. Les champs ont une hauteur minimale de (44px). Les espacements documentés sont des valeurs réutilisées du CSS, pas une grille uniforme imposée après coup.

L’impression retire la navigation et les actions, conserve la grille entreprise, réduit les marges et évite les coupures à l’intérieur des services et lignes de personnes.

## Elevation & Depth

Les cartes ordinaires reposent sur le papier et une bordure, sans ombre. La profondeur apparaît au survol de l’action principale et des choix de contexte, puis au-dessus du tableau pour le dialogue modal. Les ombres exactes, le voile modal et les transitions sont consignés dans le sidecar.

**The Surface Rule.** Garder les cartes de travail planes ; réserver les ombres aux interactions et au dialogue.

Le survol des contrôles est bref. Le dialogue entre par une translation verticale ; les petites scènes du choix de contexte se révèlent par découpe. La préférence de mouvement réduit désactive animations et transitions, y compris la translation des choix de contexte.

## Shapes

Les coins s’arrondissent suivant l’échelle du contenu : tags compacts, contrôles modérés, cartes et dialogues plus ouverts. Les avatars restent circulaires. Les bordures pleines séparent les données ; le contour pointillé distingue un poste vacant. Les services et choix de contexte découpent leurs débordements dans leurs coins.

## Components

### Buttons

Quatre variantes React : `primary`, `secondary`, `quiet`, `danger`. L’action principale reprend l’accent du métier ; l’action secondaire utilise le papier et une bordure ; l’action discrète utilise l’encre secondaire ; la suppression utilise le rouge. Le survol principal assombrit l’accent et ajoute une petite ombre ; secondaire et discrète prennent le fond d’accent pâle. La pression décale le bouton de (1px). Un contrôle désactivé passe à une opacité de (0.45).

### Cards / Containers

Les services associent un en-tête séparé, le nom et les horaires, un état de couverture et des lignes de postes. Ils sont ouverts par défaut. Un bouton natif replie ou affiche les postes ; `aria-expanded` expose l’état, `aria-controls` désigne le panneau et `hidden` masque ses lignes lorsqu’il est replié. Le chevron change d’orientation et le séparateur sous l’en-tête disparaît à la fermeture. Les personnes affectées occupent une surface sauge ; les places vacantes affichent un contour pointillé, puis une teinte d’attention si la résolution les laisse ouvertes. Les listes de personnes et besoins réemploient les surfaces claires et séparateurs.

### Inputs / Fields

Champ blanc, contour fin, coins de contrôle, libellé au-dessus et aide sous le champ. Le focus visible commun utilise un contour de (3px) dans l’accent du métier, décalé de (4px). Les erreurs de service sont affichées dans un panneau d’alerte avec une action de nouvelle tentative lorsqu’elle est disponible.

### Navigation

Liens avec icône et libellé, état courant rempli et éventuel compteur. L’entreprise utilise l’accent pâle ; le restaurant emploie un fond prune éclairci sur sa barre sombre. Le focus des contrôles de cette navigation restaurant utilise l’encre claire de la barre, avec le même contour de (3px) décalé de (4px), sur desktop et mobile. Sur mobile, les libellés reviennent sous les icônes dans la barre inférieure et un lien de retour au choix du métier apparaît dans la barre supérieure.

### Planning Signatures

Le jour sélectionné est rempli avec l’accent et sa date en blanc. Un petit point accompagne la couverture du jour. Dans l’entreprise, les plages sont des blocs compacts de la grille avec horaire et intitulé ; les absences et repos ont leur propre libellé. Les résultats longs utilisent des éléments natifs de dévoilement, avec un résumé dans l’accent et un compte secondaire ; les ajustements supplémentaires se regroupent par thème. Le sidecar fournit des aperçus autonomes de ces composants, avec des classes préfixées `ds-`.

### Dialogs

Le dialogue natif utilise une largeur maximale de (660px), un voile sombre et un contenu défilable. À l’ouverture, il bloque le défilement de la page ; à la fermeture, il restitue le focus précédent. Son titre est relié au dialogue et la croix de fermeture possède un libellé accessible.

## Do's and Don'ts

- Do conserver l’accent et les neutres propres au métier sur chaque écran.
- Do rendre les postes couverts, vacants et les absences lisibles avec du texte.
- Do garder les chiffres tabulaires pour les dates et horaires comparables.
- Do respecter le focus visible, le mouvement réduit et les adaptations mobiles existants.
- Don't appliquer ce système à la console scolaire sous `/expert/*`.
- Don't appliquer Literata aux données ou à l’ensemble des sous-titres.
- Don't ajouter d’ombres permanentes aux cartes ordinaires.
- Don't présenter une démonstration comme un planning enregistré par l’utilisateur.
