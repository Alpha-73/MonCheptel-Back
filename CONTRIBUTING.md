# Travailler a deux

## Contributions existantes

Membres : `ballasow` (BS) et `Alpha-73` (AD).
Source consultee le 30 octobre 2026 : [tableau Trello](https://trello.com/b/1Op3KNua/modele-kanban).
La correspondance AD = Alpha-73 et BS = ballasow a ete fournie par Balla Sow.
Dans Trello, AD apparait sous Aliou DICKO et BS sous Balla Sow.

| Carte | Element de checklist | Responsable visible | Etat visible |
| --- | --- | --- | --- |
| [Codage frontend](https://trello.com/c/YXWML4C8/12-codage-frontend) | Admin | AD / Alpha-73 | Acheve |
| Codage frontend | Eleveur | BS / ballasow | Acheve |
| Codage frontend | Veterinaire | AD / Alpha-73 (confirmation de Balla Sow) | Acheve |
| [Codage backend](https://trello.com/c/KmhxvFHE/20-codage-backend) | Admin | AD / Alpha-73 | Acheve |
| Codage backend | Veterinaire | BS / ballasow (confirmation de Balla Sow) | Acheve |
| Codage backend | Eleveur | BS / ballasow (confirmation de Balla Sow) | Acheve |

Une affectation Trello indique une responsabilite de tache ; elle ne permet pas
d'attribuer automatiquement chaque fichier ou les composants partages.
Les confirmations de Balla Sow completent les affectations absentes de Trello.
La connexion et les composants partages (modeles, services API, navigation et
configuration) ont ete realises ensemble, selon confirmation de Balla Sow.
Un import decoupe en plusieurs commits decrit l'ajout du code existant au depot ;
il ne reconstitue pas les dates ni les etapes du developpement passe.

## Depots cibles

- Frontend : https://github.com/Alpha-73/MonCheptel-Front
- Backend : https://github.com/Alpha-73/MonCheptel-Back

Les deux depots doivent conserver leur historique existant. La version V2 est exclue.

## Organisation des prochains travaux

Conserver les responsabilites confirmees dans Trello et attribuer explicitement
les nouvelles taches avant de commencer. Pour les composants communs
(connexion, infrastructure, navigation, modeles et services partages), identifier
les contributions de chacun ou utiliser la coautorat quand le travail est commun.

## Procedure

1. Chaque personne configure son nom Git et une adresse associee a son propre compte GitHub.
2. Creer une branche par fonctionnalite, par exemple `ballasow/gestion-cheptel`
   ou `Alpha-73/alertes`.
3. Faire des commits correspondant a des changements reels et coherents.
4. Ouvrir une pull request decrivant le changement et sa verification.
5. L'autre membre relit et fusionne apres resolution des remarques.

Pour un changement reellement realise ensemble, ajouter un trailer
`Co-authored-by: Nom <adresse-associee-au-compte>` dans le message du commit,
avec l'identite confirmee du coauteur. Un pseudo seul ne suffit pas a choisir son adresse.
Ne pas antidater les commits d'import.

## Historique

Apres creation des commits, ces commandes permettent de consulter les contributions :

```sh
git log --graph --decorate --oneline --all
git shortlog -sne --all
```

## Import du code existant

Les commits d'import sont crees a la date de l'import, sans antidatage.
Leur auteur suit la repartition confirmee par Balla Sow :
- Alpha-73 : administration frontend et backend, frontend veterinaire.
- ballasow : frontend eleveur, backend eleveur et veterinaire.
- Ensemble : connexion et composants partages (modeles, services API,
  navigation et configuration).

Les commits regroupent les fichiers par perimetre pour rendre l'import lisible.
Les ressources et guides de reference accompagnent le socle commun ; cette
organisation ne prouve pas l'auteur original de chaque ligne ou ressource.
Les commits precedents du depot sont conserves. Les etapes intermediaires
de l'import ne sont pas des versions de livraison ; utiliser l'etat final.
Les commandes de verification mentionnees dans les guides historiques n'ont
pas ete reexecutees pour cet import.

## Branches personnelles

- `main` : README initial en attendant la fusion des contributions.
- `balla` : socle commun et contributions de ballasow.
- `Aliou` : meme socle commun et contributions de Alpha-73.

Les composants partages ont un commit commun avec les deux coauteurs.
Chaque branche personnelle ne contient qu'une partie des modules : elle n'est
pas une version complete a livrer. Fusionner les deux branches pour retrouver
l'application complete. Les commits de retrait sur main conservent l'historique
du premier import, qui avait ete publie directement sur main.