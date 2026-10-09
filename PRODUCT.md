# Planning

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Responsables de restaurant qui organisent les services et responsables d’entreprise qui organisent les horaires des équipes. Le parcours scolaire existant reste accessible séparément.

## Product Purpose

Construire un planning daté à partir des personnes, des compétences, des besoins et des absences. Rendre visibles les postes couverts, les postes encore vacants et les raisons qui empêchent une affectation.

## Positioning

Le moteur recherche des affectations sous contraintes ; les interfaces traduisent ces contraintes dans le vocabulaire propre à chaque métier.

## Operating Context

Restaurant : services du midi et du soir, besoins de cuisine et de salle, couverture quotidienne. Entreprise : horaires et charge de travail par salarié, besoins d’équipe, congés et autres absences. Hypothèse déléguée : l’entreprise est présentée comme une organisation d’équipes et d’horaires, les spécificités sectorielles restant ouvertes.

## Capabilities and Constraints

Créer un espace vide ou une démonstration explicitement identifiée, ajouter et modifier des personnes et leurs compétences, préparer des services ou plages de travail, enregistrer les absences, régler les limites d’organisation, lancer une résolution et consulter ses résultats. Les données sont persistées par l’API. Une indisponibilité du service est un état d’erreur explicite. Le module reste autonome vis-à-vis de Progression.

Le contrat hebdomadaire doit être atteint, avec un plafond individuel d’heures supplémentaires autorisées. Les indisponibilités récurrentes retirent des créneaux disponibles ; elles ne diminuent pas les heures dues. Chaque date d’absence enregistrée déduit la durée quotidienne réglée pour la personne, dans la limite du contrat de la semaine. Le contrat reste entier pour une semaine partiellement incluse dans la période de planning. Les bilans hebdomadaires proviennent du serveur ; les écarts réels ne sont présentés qu’après une résolution actuelle terminée.

## Brand Commitments

Interface en français, claire, facile à utiliser, soignée et professionnelle. Les deux métiers possèdent des compositions adaptées. Les préférences facultatives de présentation sont ouvertes ; aucun choix permanent de méthode de conception n’est enregistré sans réponse.

## Evidence on Hand

Le backend et le parcours scolaire existants. Les données de démonstration restaurant et entreprise sont créées à la demande et enregistrées côté serveur. Aucun témoignage ni résultat commercial n’est fourni.

## Product Principles

- Commencer par les tâches concrètes du responsable.
- Employer les mots du métier et expliquer les résultats en langage courant.
- Afficher un planning courant ou un état vide honnête.
- Montrer ce qui reste à couvrir et comment compléter les données.
- Préserver l’interface scolaire existante.
