# Ajouts d'API du 7 octobre 2026

État : implémenté et vérifié contre le back réel (console admin et espace vétérinaire branchés dessus). (pour l'espace vétérinaire et la console admin)

Toutes les routes existantes restent valables (voir `back/api/src/main/java/ml/moncheptel/api/controleur/` et les DTO dans `dto/`).
JSON : dates `Instant` en ISO-8601 (`2026-09-28T14:30:00Z`), `LocalDate` en `2026-09-28`. Champs nuls omis.
Énumérations : `Filiere` = VOLAILLE | BOVINS | OVINS | CAPRINS ; `NiveauGravite` = FAIBLE | MOYEN | ELEVE ;
`StatutSignalement` = NOUVEAU | ORIENTE | EN_COURS | TRAITE ; `StatutConseil` = BROUILLON | EN_MODERATION | PUBLIE | RETIRE ;
`CategorieConseil` = SANTE | PRODUCTION | HYGIENE ; `StatutAlerte` = ACTIVE | TERMINEE (vérifier dans `modele/enumeration/`).

Authentification : `POST /api/auth/connexion {email, motDePasse}` → `JetonReponse {jetonAcces, jetonRafraichissement, expireDansSecondes, utilisateurId, nom, role, doitChangerMotDePasse}`.
`role` = ELEVEUR | VETERINAIRE | ADMINISTRATEUR. En-tête `Authorization: Bearer <jetonAcces>`.
Jeton absent/expiré → **403**. `POST /api/auth/rafraichir {jetonRafraichissement}` → nouveau `JetonReponse` (usage unique, rotation). 422 = session expirée.
`POST /api/auth/deconnexion` avec l'en-tête `X-Refresh-Token: <jetonRafraichissement>`. `POST /api/auth/mot-de-passe {ancienMotDePasse, nouveauMotDePasse}` (révoque toutes les sessions).
`GET /api/auth/moi` → `ProfilReponse` (pour un vétérinaire : `titre`, `specialite`, `zoneIntervention`, `disponible`).
Erreurs : `ErreurReponse` (voir `dto/reponse/ErreurReponse.java`) avec un `message` en français.

## Côté vétérinaire (rôle VETERINAIRE)

| Route | Réponse |
|---|---|
| `GET /api/veterinaires/accueil` | `{enAttente, enAttenteGraves, traitesCeMois, conseilsPublies, conseilsEnModeration}` (nombres) |
| `GET /api/veterinaires/competences` | `MaladieReponse[]` : les maladies que le vétérinaire traite |
| `POST /api/veterinaires/competences/{maladieId}` / `DELETE …` | 204 (existant) |
| `GET /api/signalements/maladies-catalogue` | `MaladieReponse[]` : toutes les maladies actives (pour cocher les compétences) |
| `GET /api/signalements/file?statut=&page=` | `PageReponse<SignalementReponse>` (existant). « En attente » = ORIENTE + EN_COURS (appeler sans statut et filtrer, ou deux appels) ; « Traités » = TRAITE |
| `POST /api/signalements/{id}/prise-en-charge` | `SignalementReponse` (ORIENTE → EN_COURS) |
| `POST /api/signalements/{id}/cloture {compteRendu}` | `SignalementReponse` (→ TRAITE). `compteRendu` obligatoire |
| `GET /api/conseils/mes-conseils` | `ConseilDetailReponse[]` (existant) |
| `POST /api/conseils {titre, contenu, filiere, categorie}` | crée un BROUILLON ; enchaîner `POST /api/conseils/{id}/soumission` pour l'envoyer en modération |
| `PUT /api/conseils/{id}` | **modifié** : un conseil PUBLIÉ peut désormais être modifié ; il repasse EN_MODERATION |
| `DELETE /api/conseils/{id}` | **nouveau**, 204, auteur seulement, suppression définitive |

`SignalementReponse` gagne en fin : `uniteType` (libellé du type d'élevage, ex. « Poulets de chair »), `uniteEffectif` (entier, têtes vivantes).
`ConseilDetailReponse` gagne en fin : `dateCreation`, `dateSoumission` (Instant). `auteur` peut être absent (conseil publié par l'administration).

## Côté administration (rôle ADMINISTRATEUR), préfixe `/api/admin`

| Route | Réponse |
|---|---|
| `GET /tableau-de-bord` | `TableauDeBordAdminReponse` (existant) |
| `GET /statistiques` | `StatistiquesReponse` (existant) |
| `GET /utilisateurs?recherche=&filiere=&zoneId=&actif=&page=` | `PageReponse<UtilisateurResumeReponse>` (existant, éleveurs) |
| `GET /utilisateurs/{id}/fiche` | **nouveau** `FicheUtilisateurReponse {profil: ProfilReponse, actif, verrouille, unites, tetes, signalements, signalementsEnCours, activite: [{date: Instant, libelle}]}` |
| `PUT /utilisateurs/{id}` `ProfilRequete {nom, email, telephone, zoneId, langue, filieres}` | `ProfilReponse` (existant) |
| `POST /utilisateurs/{id}/activation?actif=true|false` | 204 (existant, vaut aussi pour un vétérinaire) |
| `DELETE /utilisateurs/{id}` | 204. Supprime définitivement un éleveur (unités, mouvements, carnet sanitaire, signalements, opérations) ou un vétérinaire (compétences, conseils ; ses cas en cours repassent « orienté », ses actes restent sans auteur). 422 pour un administrateur, 404 si absent. Migration V8. |
| `GET /maladies?retirees=true` | les maladies retirées en plus, pour les réactiver |
| `POST /maladies/{id}/reactivation` | `MaladieAdminReponse` : remet une maladie retirée au catalogue |
| `GET /tableau-de-bord` | **nouveau champ** `casSansVeterinaire` : signalements graves qu'aucun vétérinaire ne couvre |
| `POST /utilisateurs/{id}/mot-de-passe` | 204, envoie un mot de passe provisoire (existant) |
| `GET /veterinaires?zoneId=` | **changé** `VeterinaireAdminReponse[]` |
| `GET /veterinaires/{id}` | **nouveau** `VeterinaireAdminReponse` |
| `POST /veterinaires` `VeterinaireRequete {nom, telephone, email, zoneId, zoneInterventionId, titre?, specialite?}` | **changé** 201 `VeterinaireAdminReponse` ; puis cocher les compétences une par une |
| `PUT /veterinaires/{id}` (même corps) | **changé** `VeterinaireAdminReponse` |
| `POST|DELETE /veterinaires/{id}/competences/{maladieId}` | 204 (existant) |
| `GET /maladies` | **changé** `MaladieAdminReponse[]` |
| `GET /maladies/{id}` | **nouveau** `MaladieAdminReponse` |
| `POST /maladies`, `PUT /maladies/{id}` `MaladieRequete {nom, gravite, filieres[], symptomesTexte, traitement, orienteVersVeterinaire, symptomes: [{symptomeId, poids (0..1)}] (au moins 1)}` | `MaladieAdminReponse` |
| `DELETE /maladies/{id}` | 204, retrait logique (existant) |
| `GET /api/signalements/symptomes?filiere=` | `SymptomeReponse[] {id, libelle}` — **désormais ouvert à l'admin** |
| `GET /conseils?statut=` | **nouveau** `ConseilDetailReponse[]`, tous statuts sauf BROUILLON, du plus récent au plus ancien |
| `GET /moderation` | `ConseilDetailReponse[]` en modération (existant) |
| `POST /moderation/{id} {publier: true}` ou `{publier: false, motif}` | `ConseilDetailReponse` ; le retrait exige un motif |
| `POST /conseils {titre, contenu, filiere, categorie}` | **nouveau**, 201, publié directement |
| `GET /alertes` | `AlerteReponse[]` (existant) |
| `GET /alertes/apercu?maladieId=&zoneId=&filiere=` | `{signalementsRecents, eleveursCibles}` (existant) |
| `POST /alertes {maladieId, zoneId, filiere?, message, dateFin?}` | 201 `AlerteReponse` (`dateFin` : dernier jour, facultatif) |
| `PUT /alertes/{id} {message, dateFin?}` | `AlerteReponse` |
| `POST /alertes/{id}/cloture` | `AlerteReponse` |
| `GET /api/zones` | `ZoneReponse[] {id, nom, region}` (public) |

`VeterinaireAdminReponse {id, nom, titre, specialite, telephone, email, zone: ZoneReponse, zoneIntervention: ZoneReponse, actif, disponible, filieres: Filiere[], competences: number[] (ids de maladies), nombreCompetences}`.
`MaladieAdminReponse {id, nom, gravite, filieres[], symptomesTexte, traitement, orienteVersVeterinaire, active, symptomes: [{symptomeId, libelle, poids}], veterinairesCompetents}`.

Comptes de démonstration : voir `back/api/src/main/resources/db/migration/V4__jeu_de_demonstration.sql` (admin `admin@moncheptel.ml`).

## Règles métier revues le 7 octobre (audit de la logique admin)

- **Orientation** (`OrientationService`) : spécialiste de la zone, sinon vétérinaire de la zone, sinon spécialiste ailleurs. Un vétérinaire désactivé ou supprimé rend ses cas ouverts ; un changement de zone ou une compétence retirée rend les cas pas encore ouverts ; chaque arrivée relance les cas en attente.
- **Comptes administrateur** : les routes `/utilisateurs/{id}/…` (modifier, activer, mot de passe, supprimer) refusent un administrateur (422).
- **Alertes** : le doublon tient compte de la filière ; une maladie retirée ne se déclenche plus ; clôturer deux fois répond 422 ; la clôture prévient les éleveurs ciblés.
- **Conseils** : retrait seulement depuis « en modération » ou « publié » ; un vétérinaire ne modifie pas un conseil en relecture ; un conseil de l'administration retiré se republie ; à la suppression d'un vétérinaire, ses conseils publiés restent au nom de l'administration.
- **Signalements** : `date_prise_en_charge` (migration V9) mesure le délai de prise en charge.

## Ajouts du 7 octobre, fin de journée

Administration (`/api/admin`) :

| Route | Réponse |
|---|---|
| `GET /signalements?vue=A_ATTRIBUER\|EN_SUIVI\|TRAITES\|TOUS&page=` | `PageReponse<SignalementAdminReponse>` (10 par page) |
| `GET /signalements/{id}/candidats` | `CandidatVeterinaireReponse[] {id, nom, zone, competent, memeZone, disponible, casOuverts, actuel}`, les mieux placés d'abord |
| `POST /signalements/{id}/orientation {veterinaireId}` | `SignalementAdminReponse` ; 422 si traité, vétérinaire désactivé ou déjà en charge. Le nouveau vétérinaire est notifié, l'ancien prévenu |
| `GET /statistiques/series` | `{semaines[12], inscriptions[12], entonnoir, delais[5], maladies[≤6], moteur}` |
| `GET /types-elevage?filiere=` | actifs puis retirés (les retirés n'étaient pas rendus avant) |
| `POST /types-elevage/{id}/reactivation` | `TypeElevageReponse` |
| `POST/PUT /types-elevage` | 409 si le libellé existe déjà dans la filière ; 422 si température min > max |

`SignalementAdminReponse {id, eleveurId, eleveurNom, zone, uniteCode, maladieProposee, maladieConfirmee, gravite, statut, aAttribuer, veterinaireId, veterinaireNom, dateSignalement, dateOrientation, datePriseEnCharge, dateTraitement}`.

Vétérinaire : `POST /api/signalements/{id}/cloture {compteRendu, maladieConfirmeeId?}`. La maladie retenue par le vétérinaire remplace la proposition du moteur dans les statistiques. `SignalementReponse` gagne `maladieConfirmee`. Clôturer un cas déjà traité répond 422.

Migration V10 : `alerte.date_fin`, `signalement_maladie.maladie_confirmee_id`. Une tâche horaire clôt les alertes dont la date de fin est passée et prévient les éleveurs ; l'application ne montre plus une alerte échue.

## Ajouts du 7 octobre, app mobile

Implémenté, migration **V11**, vérifié au curl sur les comptes démo (`mvn -q -o -B package` : 64 tests verts). Les champs ajoutés aux DTO existants sont placés en fin de record ; les champs nuls restent omis. Erreurs : `ErreurReponse {horodatage, statut, erreur, message, chemin, champs?}`. Un 400 de validation porte le message par champ dans `champs`.

### Nouvelles routes

| # | Route | Rôle | Corps | Réponse | Codes |
|---|---|---|---|---|---|
| 1 | `GET /api/signalements/mes` | ELEVEUR | — | `SignalementReponse[]`, le plus récent d'abord (`dateSignalement` décroissante) | 200 ; 403 pour un autre rôle |
| 2 | `GET /api/signalements/{id}` | ELEVEUR propriétaire ou VETERINAIRE en charge | — | `SignalementReponse`. Vue éleveur : sans `eleveurTelephone`, traitement masqué si la maladie oriente. Vue vétérinaire : avec `eleveurTelephone`/`eleveurZone` | 200 ; **404** pour tout autre lecteur (autre éleveur, confrère, ancien vétérinaire du cas) ou un id absent |
| 3 | `DELETE /api/cheptel/{uniteId}/sorties/{sortieId}` | ELEVEUR propriétaire | — | vide | **204** ; 404 si l'unité n'existe pas ou si la sortie n'appartient pas à cette unité ; 403 unité d'un autre ; 422 si `sortieId` est un mouvement d'entrée (« seule une sortie s'annule ») |
| 4 | `POST /api/cheptel/{uniteId}/cloture` | ELEVEUR propriétaire | — (pas de corps) | `UniteResumeReponse`, `statut: "CLOTURE"` | 200 ; 404 ; 403 ; 422 si déjà terminé ou sorti (« LOT-014 est déjà terminé. ») |
| 5 | `PUT /api/veterinaires/disponibilite` | VETERINAIRE | `{"disponible": true\|false}` (obligatoire) | `{"disponible": bool}` | 200 ; 400 si `disponible` absent (`champs.disponible`) ; 403 pour un autre rôle |

**Route 3, annulation d'une sortie.** `sortieId` est l'**id du mouvement de sortie** (`MouvementCheptel`) : c'est `mouvements[].id` du détail d'unité, et `sorties[].id` (même valeur). Effets, en une transaction : les têtes reviennent à l'effectif ; une unité que cette sortie avait fait sortir (lot `CLOTURE` à 0 tête, animal `VENDU`/`MORT`/`TRANSFERE`) redevient `ACTIF` avec `dateSortie` effacée ; l'opération financière de revenu liée à une vente est supprimée (le solde suit) ; le mouvement est supprimé du journal. Annuler une sortie d'une unité terminée à la main (route 4) la réactive aussi ; ses rappels déjà écartés restent écartés. Après un 204, recharger `GET /api/cheptel/{uniteId}`.

**Route 4, terminer un élevage.** Lot ou animal, avec ou sans têtes restantes : `statut` passe à `CLOTURE` (libellé « Terminé »), `dateSortie` = aujourd'hui, l'unité quitte les listes actives, ses rappels `A_VENIR`/`ECHU` passent à `IGNORE`. L'effectif n'est pas modifié (aucun mouvement créé). Le carnet devient en lecture seule (soins, sorties, modification et signalement répondent 422). Pour une vente des têtes restantes, enregistrer la sortie : à 0 tête le lot se termine seul. Tous les messages du back qui disaient « Clôturez-le » disent désormais « Terminez-le (« Terminer » dans son carnet) » et visent cette route.

**Route 5, disponibilité.** Indisponible : l'orientation automatique ne choisit plus ce vétérinaire (aucun des trois niveaux), et ses cas `ORIENTE` (pas encore ouverts) repartent vers un confrère disponible ; s'il n'y en a pas, ils redeviennent `NOUVEAU` et comptent dans `casSansVeterinaire` de l'administration. Ses cas `EN_COURS` lui restent. Redevenu disponible : les cas en attente sont relancés (il peut en recevoir aussitôt). L'attribution manuelle de l'administration reste possible. `GET /api/veterinaires/accueil` renvoie désormais `{enAttente, enAttenteGraves, traitesCeMois, conseilsPublies, conseilsEnModeration, disponible}` ; `GET /api/auth/moi` renvoie toujours `disponible`.

### Conflit concurrent (409)

`SignalementMaladie` porte un verrou de version (colonne `version`, V11). Deux écritures simultanées sur le même signalement (clôture et réattribution par l'administration, double clôture, prise en charge et clôture) : la première passe, la seconde reçoit **409** `{"erreur":"Conflit","message":"Ce cas vient d'être modifié par quelqu'un d'autre (clôturé ou confié à un confrère). Rechargez-le avant de recommencer."}`. Rien n'est écrit, aucune notification n'est envoyée. L'app doit recharger le cas (`GET /api/signalements/{id}` : 404 s'il a été confié à un confrère). Vérifié : quatre clôtures parallèles donnent un 200 et trois 409, une seule notification.

### Routes et DTO modifiés

`SignalementReponse` gagne en fin :
- `uniteFiliere` (`Filiere`) : pour filtrer le catalogue à la clôture ;
- `veterinaire` (`VeterinaireResumeReponse {id, nom, titre, specialite, zoneIntervention, telephone, disponible}`) : le vétérinaire orienté ou en charge, **renseigné même quand `diagnostic` est nul** (aucune maladie probable). Absent tant que le cas n'est confié à personne (`NOUVEAU`). L'app le lit en priorité sur `diagnostic.veterinaire` ;
- `dateOrientation`, `datePriseEnCharge` (Instant, absents si non advenus).
Champs déjà présents utiles au suivi : `statut`, `compteRendu`, `dateTraitement`, `maladieConfirmee` (nom).

`GET /api/cheptel/{uniteId}` (`LotDetailReponse`) gagne en fin `sorties: SortieReponse[]`, la plus récente d'abord : `{id, date (LocalDate), nature (VENTE|MORT|TRANSFERT), nombre, montant? (ventes seulement), motif?}`. `id` = id du mouvement = `sortieId` de la route 3.

`POST /api/cheptel/{uniteId}/sorties` (`SortieRequete {type, nombreTetes, date, montant?, motif?}`) :
- `montant` doit être **> 0** s'il est fourni : `0` répond **400** `champs.montant = "Le montant d'une vente doit être supérieur à zéro"` (plus de 500). Une `VENTE` sans montant répond 422 « Indiquez le montant de la vente, supérieur à zéro. » ; un montant sur une mort ou un transfert, 422 ;
- `date` : 422 si future (« La date de la sortie ne peut pas être dans le futur. ») ou antérieure à l'entrée (« La date de la sortie (1 septembre 2026) précède l'entrée de LOT-014 (17 septembre 2026). ») ;
- `type` d'entrée (`MISE_EN_PLACE`, `ACQUISITION`, `NAISSANCE`) : 422 ;
- trop de têtes : 422 « LOT-014 ne compte que 300 têtes : impossible d'en retirer 500. » ;
- libellé du revenu créé : « Vente LOT-014 — 5 têtes » (les anciens « tete(s) » sont corrigés en base par V11).

Contrôle des dates (422, message en clair) :
- `POST /api/cheptel/lots` : `dateMiseEnPlace` future refusée ;
- `POST /api/cheptel/animaux` : `dateEntree` future refusée ; `dateNaissance` future ou postérieure à `dateEntree` refusée ;
- `PUT /api/cheptel/{uniteId}` : `dateEntree` future, ou postérieure à la première sortie ou au premier soin, refusée ; `dateNaissance` postérieure à `dateEntree` refusée. Quand la date change, la date du mouvement d'entrée (`MISE_EN_PLACE`/`ACQUISITION`) suit, et les rappels du protocole sont recalculés ;
- `POST /api/sanitaire/unites/{uniteId}/evenements` et `PUT /api/sanitaire/evenements/{id}` : `dateEvenement` future ou antérieure à l'entrée de l'unité refusée (« La date du soin … précède l'entrée de … »).

Rappels :
- `GET /api/sanitaire/rappels` et les rappels du tableau de bord : seuls les rappels de **30 jours de retard au plus** sont rendus (`joursRestants >= -30`). Les plus anciens restent visibles dans `GET /api/cheptel/{uniteId}` (`rappels`) ;
- un rappel créé par `rappelDansJours` mémorise le type de l'acte : un soin du même type, dans une fenêtre de ±7 jours autour de l'échéance, le solde (`FAIT`), comme un rappel du protocole. Les rappels libres existants sont rattachés à leur type par V11.

Signalements, côté vétérinaire :
- `POST /api/signalements/{id}/prise-en-charge` : notifie désormais l'éleveur (voir Notifications). Message d'erreur sans code d'énumération : « Vous avez déjà pris ce cas en charge. », « Ce cas est déjà clôturé. » (422) ;
- `POST /api/signalements/{id}/cloture {compteRendu, maladieConfirmeeId?}` : `maladieConfirmeeId` doit désigner une maladie **active** et **de la filière de l'unité**, sinon 422 (« « Gumboro » a été retirée du catalogue : choisissez une autre maladie. », « « Fièvre aphteuse » ne touche pas la filière Volaille de LOT-014. »). 404 si l'id n'existe pas.

Diagnostic (`POST /api/signalements`) : une hypothèse n'est proposée (`diagnostic.probable`, puis `alternative`) que si elle explique **au moins la moitié des symptômes cochés** et cumule un **poids d'au moins 0,8** (relevé de 0,5 le 8 octobre : avec les poids réels de V3, deux signes banals ne suffisent plus). Sinon `diagnostic` est absent (« aucune maladie identifiée ») et la gravité vient de la seule ampleur. Un symptôme banal seul ne donne plus une « maladie probable à 100 % ».

Simulateur (`POST /api/investissement/simulation`, `…/simulation/{typeElevageId}`) : un poste `PAR_CAPACITE` (logement) est multiplié par le nombre de bâtiments nécessaires, `ceil(sujetsAAcheter / capaciteReference)`, au moins 1. Exemple poulet de chair, budget 2 000 000 : 1 314 sujets achetés, 14 bâtiments de 100 places, installation 260 000 FCFA. Dans le détail, la quantité de la ligne « Logement » suit (ex. 140 m²).

Statut d'unité : le libellé de `CLOTURE` est « Clôturé » (« Terminé » avant le 8 octobre), celui de `TRANSFERE` « Transféré ». Les valeurs d'énumération ne changent pas.

### Notifications

Chaque `NotificationReponse {id, type, titre, corps, lue, referenceType, referenceId, dateCreation}` porte une référence exploitable (vérifié en base : aucune notification sans référence) :

| `referenceType` | `referenceId` | `type` | Émise quand | Destinataire | Écran à ouvrir |
|---|---|---|---|---|---|
| `SIGNALEMENT` | id du signalement | `ORIENTATION` | cas orienté (« Nouveau signalement — Newcastle »), cas réattribué à un confrère (« Cas réattribué ») | vétérinaire | `GET /api/signalements/{id}` (404 si le cas lui a été retiré : afficher « Ce cas a été confié à un confrère ») |
| `SIGNALEMENT` | id du signalement | `ORIENTATION` | **nouveau** : prise en charge (« Votre signalement est pris en charge » / « Dr Aminata Sow a pris en charge votre signalement sur LOT-014. Vous serez contacté prochainement. ») | éleveur | `GET /api/signalements/{id}` |
| `SIGNALEMENT` | id du signalement | `ORIENTATION` | clôture (« Réponse du vétérinaire sur LOT-014 » / « Dr Aminata Sow a répondu à votre signalement : Maladie de Newcastle. Ouvrez-le pour lire son compte rendu. ») | éleveur | `GET /api/signalements/{id}` |
| `ALERTE` | id de l'alerte | `ALERTE` | alerte déclenchée, alerte levée | éleveurs ciblés | fiche de l'alerte : la retrouver par `id` dans `alertes[]` de `GET /api/tableau-de-bord` (une alerte levée n'y figure plus : afficher le corps de la notification) |
| `CONSEIL` | id du conseil | `MODERATION` | conseil publié ou retiré par la modération | vétérinaire auteur | vétérinaire : le conseil dans `GET /api/conseils/mes-conseils` (`GET /api/conseils/{id}` ne rend que les conseils publiés) |

L'app doit router sur `referenceType` (et non sur `type`). Les libellés `TypeNotification` sont accentués (« Modération », « Système »).

### Migration V11

- `signalement_maladie.version BIGINT NOT NULL DEFAULT 0` (verrou optimiste) ;
- `rappel_sanitaire.type_evenement` (ENUM nullable), rempli pour les rappels existants (calendrier, ou libellé « Rappel vaccination »…) ;
- corrections de textes déjà enregistrés : notes de vente « tete(s) », notifications « a repondu a votre signalement ».

### Messages accentués

Les messages remontés à l'utilisateur ont été accentués et débarrassés des codes d'énumération : sorties (« déjà sorti du cheptel », « ne compte que 1 tête »), lot, catégories d'opération, soumission de conseil, alerte déjà clôturée, prise en charge, session expirée, code de réinitialisation, mot de passe oublié, corps de requête illisible, erreur interne (« Réessayez »), validation (« Le type d'événement est obligatoire », « Indiquez au moins une tête »).

## Audit global du 8 octobre

Les valeurs d'énumération ne changent pas. Les changements de statut HTTP sont à prendre en
compte par tout client.

### Statuts HTTP

| Cas | Avant | Maintenant | Corps |
|---|---|---|---|
| Jeton absent, invalide, expiré ou révoqué (changement de mot de passe, réinitialisation) | 403 | **401** | `ErreurReponse`, message « Session expirée. Reconnectez-vous. » |
| Rôle insuffisant, ressource d'autrui | 403 | 403 | inchangé |
| Mot de passe provisoire pas encore changé | — | **403** | « Changez d'abord votre mot de passe provisoire. ». Seules `GET /api/auth/moi`, `POST /api/auth/mot-de-passe` et `POST /api/auth/deconnexion` restent ouvertes |
| Trop de requêtes sur une route d'authentification | — | **429** | en-tête `Retry-After` (secondes) ; message « Trop d'essais depuis ce réseau. Réessayez dans une minute. » |
| Contrainte de base violée (doublon, modification concurrente) | 500 | **409** | « Cette donnée existe déjà ou vient d'être modifiée par quelqu'un d'autre. Rechargez puis réessayez. » |

Les clients renouvellent le jeton **sur 401 seulement** : un 403 n'est jamais rejoué. L'app et
l'intercepteur de la console sont alignés.

Limites de débit, par adresse IP, sur les POST :

| Route | Limite |
|---|---|
| `/api/auth/connexion` | 10 par minute |
| `/api/auth/rafraichir` | 30 par minute |
| `/api/auth/inscription` | 10 par heure |
| `/api/auth/mot-de-passe/oublie` | 5 par 15 minutes, et 200 par heure pour tout le serveur |
| `/api/auth/mot-de-passe/reinitialiser` | 10 par 15 minutes |

`MONCHEPTEL_LIMITE_DEBIT=false` coupe ces limites, pour un banc d'essai seulement.

Le verrou de compte après cinq mauvais mots de passe est désormais **progressif** : 1, 2, 4, 8
minutes, puis 15 au plus. Il ne bloque que la connexion : un jeton déjà émis et le
rafraîchissement continuent de fonctionner.

### Routes et règles modifiées

- `POST /api/cheptel/{uniteId}/cloture` accepte un corps facultatif
  `{type: VENTE|MORT|TRANSFERT, montant?, motif?}` :
  - sans corps, sur une unité qui a encore des têtes : **422** « LOT-014 compte encore 40 têtes.
    Pour le clôturer, dites ce qu'elles sont devenues : vente, mort ou transfert. » ;
  - avec le corps : une sortie de **toutes** les têtes restantes est enregistrée. Une vente crée
    la recette. L'unité est ensuite clôturée ;
  - dans tous les cas, les rappels ouverts sont écartés.
- Annuler une sortie (`DELETE /api/cheptel/{id}/sorties/{sortieId}`) ne rouvre l'unité que si
  **cette** sortie l'avait vidée. Une unité clôturée à la main reste clôturée.
- Carnet sanitaire (`POST /api/sanitaire/unites/{id}/evenements`, `PUT` et `DELETE` sur
  `/api/sanitaire/evenements/{id}`) :
  - un vétérinaire n'écrit, ne corrige et ne supprime que s'il a un cas **ouvert** (`ORIENTE` ou
    `EN_COURS`) sur cette unité, sinon 403. La règle « même zone » est retirée ;
  - l'éleveur ne modifie pas l'acte d'un vétérinaire : 422 « Cet acte a été noté par le
    vétérinaire : seul lui peut le modifier. » ;
  - unité sortie : 422 « … son carnet sanitaire est clos ».
- `PUT /api/utilisateurs/moi` : retirer une filière qui porte un élevage actif donne 422
  « Vous ne pouvez pas retirer la filière Volaille : un élevage y est encore actif (LOT-014).
  Clôturez-les d'abord. »
- Création d'un vétérinaire et réinitialisation d'un mot de passe par l'administration : 422 si
  le serveur n'a pas de SMTP configuré, au lieu d'un mot de passe provisoire perdu. Les
  courriels partent **après** la validation de la transaction, hors de la requête.
- `SignalementReponse` gagne `uniteFiliere`. La clôture vétérinaire ne propose que les
  maladies de cette filière.
- Ouvrir un cas (`GET /api/signalements/{id}`) ne le prend plus en charge côté app. La prise en
  charge passe par le bouton « Accepter le cas », ou se fait à la clôture (voir V14 : refus).
- Orientation automatique, dans cet ordre :
  1. compétent dans la zone ;
  2. de la zone et de la filière de l'unité ;
  3. de la zone ;
  4. compétent ailleurs dans le pays.

  À chaque niveau, le vétérinaire disponible qui a le moins de cas ouverts est choisi. Un
  changement de zone ou de compétence ne reprend au vétérinaire que les cas auxquels il ne
  convient plus.

### Notifications

| `referenceType` | `referenceId` | `type` | Émise quand | Destinataire | Écran à ouvrir |
|---|---|---|---|---|---|
| `UNITE` (ou absent si plusieurs unités) | id de l'unité | `RAPPEL` | chaque jour à 6 h (Bamako) : « Soins à faire aujourd'hui » et « Soins à faire demain » ; une notification par éleveur et par jour, qui liste les soins | éleveur | carnet de l'unité |

Les notifications sont poussées **après** la validation de la transaction. Une opération
annulée ne fait plus sonner de téléphone.

### Libellés accentués

| Énumération | Libellés |
|---|---|
| `StatutSignalement` | « En attente de réponse » (`ORIENTE`, depuis V14), « Pris en charge » (`EN_COURS`), « Traité » |
| `StatutUnite` | « Clôturé » (`CLOTURE`) |
| `StatutConseil` | « En modération », « Publié », « Retiré » |
| `StatutRappel` | « À venir », « Échu », « Ignoré » |
| `TrancheEffectif` | « 10 à 50 » |
| Autres | « Santé », « Hygiène », « Main-d'œuvre », « Équipement », « Français », « Élevé », « Mâle », « Dépense », etc. |

### Migration V12

V12 accentue des textes déjà en base, valeur exacte par valeur exacte :

- 8 symptômes ;
- 2 maladies : « Fièvre aphteuse » et « Diarrhée néonatale » ;
- la zone « Ségou ».


---

## Version 2 (9 octobre 2026) — choix du vétérinaire par l'éleveur, maladies et symptômes ajoutés par le terrain

| Route | Rôle | Effet |
|---|---|---|
| `GET /api/signalements/{id}/veterinaires` | ÉLEVEUR (son signalement) | Tous les vétérinaires **actifs de la zone de l'éleveur** qui traitent la maladie probable (sans maladie reconnue : ceux de la zone qui traitent la filière de l'unité). **Ordre aléatoire à chaque appel.** Un vétérinaire indisponible est rendu avec `disponible: false`. |
| `POST /api/signalements/{id}/veterinaire/{veterinaireId}` | ÉLEVEUR (son signalement) | Confie le cas au vétérinaire choisi (statut `ORIENTE`). 422 si le vétérinaire n'est pas de la zone, ne traite pas la maladie, est indisponible, ou si le cas est déjà `EN_COURS` / `TRAITE`. Un cas `ORIENTE` peut changer de vétérinaire : l'ancien est prévenu. |
| `POST /api/signalements/symptomes` `{ "libelle": "…" }` | VÉTÉRINAIRE, ADMINISTRATEUR | Ajoute un symptôme à la liste. Un libellé déjà présent (casse ignorée) rend le symptôme existant. |
| `POST /api/veterinaires/maladies` (corps de `MaladieRequete`) | VÉTÉRINAIRE | Ajoute une maladie au catalogue, utilisable tout de suite ; le vétérinaire en devient compétent. L'administration peut la corriger ou la retirer. |

Changement de comportement : **plus d'orientation automatique**. `POST /api/signalements` crée le cas en `NOUVEAU` ; l'éleveur choisit ensuite son vétérinaire. Un vétérinaire qui part, change de zone, perd une compétence ou se déclare indisponible rend ses cas pas encore ouverts : ils redeviennent `NOUVEAU` et l'éleveur est notifié pour en choisir un autre. L'attribution manuelle par l'administration est inchangée.


---

## V14 (29 octobre 2026) — le vétérinaire accepte ou refuse un cas ; relances

| Route | Rôle | Effet |
|---|---|---|
| `POST /api/signalements/{id}/prise-en-charge` | VÉTÉRINAIRE (cas confié) | **Accepte** le cas : `ORIENTE` → `EN_COURS`. L'éleveur reçoit « Dr X a accepté votre cas ». Inchangé côté API. |
| `POST /api/signalements/{id}/refus` `{ "motif": "…" }` (corps facultatif, motif ≤ 500) | VÉTÉRINAIRE (cas confié) | **Refuse** le cas : il redevient `NOUVEAU`, l'éleveur reçoit « Dr X ne peut pas prendre votre cas » (avec le motif) et choisit un autre vétérinaire. 422 si le cas est déjà accepté (`EN_COURS` : il se clôture) ou traité. |

Réponses :

- `SignalementReponse.refus` : `[{ veterinaireNom, motif, dateRefus }]`, du plus récent au plus ancien. Rempli dans le détail (`GET /api/signalements/{id}`), le choix du vétérinaire et « Mes signalements » ; vide dans la file du vétérinaire.
- `SignalementAdminReponse.refus` : même forme, pour la console.
- `CandidatVeterinaireReponse.aRefuse` : ce vétérinaire a déjà refusé ce cas (l'administration peut passer outre).

Règles :

- Un vétérinaire qui a refusé un cas n'est plus proposé à l'éleveur pour ce cas.
- Relances (tâche toutes les 15 min) : un cas confié sans réponse après `moncheptel.orientation.delai-urgent` (2 h, cas grave ou maladie qui oriente) ou `delai-normal` (12 h) → rappel au vétérinaire ; au double du délai → l'éleveur est invité à attendre ou à changer de vétérinaire, l'administration est prévenue si le cas est urgent. Un cas urgent resté sans vétérinaire 2 h → l'éleveur est invité à en choisir un, l'administration est prévenue. Une relance par période d'attente.
- Un cas urgent **déjà accepté** dont le vétérinaire est désactivé ou supprimé : l'administration est prévenue tout de suite.
- Un éleveur qui change de zone : un cas confié mais pas encore accepté par un vétérinaire de l'ancienne zone lui est retiré ; les deux sont prévenus.
- Annuler la sortie qui avait terminé une unité rouvre les rappels de soins écartés par cette sortie (pas ceux que l'éleveur avait ignorés).
- Supprimer un conseil supprime aussi les notifications qui pointaient vers lui (« Conseil à modérer »).

Migration **V14** : table `signalement_refus`, colonnes `date_relance_veterinaire`, `date_relance_eleveur`, `date_remise_en_attente` (signalement), `ecarte_a_la_sortie` (rappel). Les cas déjà ouverts sont marqués comme relancés : pas de salve de notifications au démarrage.
