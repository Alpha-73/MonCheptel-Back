# MonCheptel — API

Back-end Spring Boot de la plateforme. MVC en couches, MySQL, migrations
Flyway, notifications Firebase.

## Démarrer

```bash
# 1. base de données
mysql -u root -e "CREATE DATABASE moncheptel_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. lancer en profil dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway crée le schéma et charge le référentiel au premier démarrage.
Le contrat d'API est sur `http://localhost:8080/swagger-ui.html`.

En profil par défaut (production), ces variables d'environnement sont lues :

| Variable | Rôle |
|---|---|
| `MONCHEPTEL_DB_PASSWORD` | mot de passe MySQL, **obligatoire** |
| `MONCHEPTEL_JWT_SECRET` | signature des jetons, **obligatoire, 32 octets minimum** |
| `MONCHEPTEL_DB_URL`, `MONCHEPTEL_DB_USER` | facultatives, ont une valeur par défaut |
| `MONCHEPTEL_CORS_ORIGINES` | adresses de la console admin, séparées par des virgules (défaut : `http://localhost:4200,https://admin.moncheptel.ml`) |
| `MONCHEPTEL_SWAGGER` | `true` pour ouvrir Swagger ; **fermé par défaut**, ouvert en profil `dev` |
| `MONCHEPTEL_SMTP_HOTE`, `_PORT`, `_USER`, `_PASSWORD`, `MONCHEPTEL_COURRIEL_EXPEDITEUR` | envoi des courriels ; sans identifiants, les courriels sont écrits dans `target/courriels-dev.log` |
| `MONCHEPTEL_FIREBASE_CLE` | chemin de la clé Firebase ; vide = notifications enregistrées sans être poussées |
| `MONCHEPTEL_ADMIN_EMAIL`, `MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL`, `MONCHEPTEL_ADMIN_NOM` | premier administrateur, créé au démarrage si la base n'en a aucun (mot de passe provisoire) |
| `MONCHEPTEL_LIMITE_DEBIT` | `true` par défaut : limite de débit des routes d'authentification (429) |
| `MONCHEPTEL_JOURNAUX` | dossier des journaux tournants hors profil dev (défaut `./journaux`) |

Mise en service (HTTPS, sauvegardes, journaux, systemd) : `EXPLOITATION.md`.

Pour le SMTP en local, copiez `config/application.properties.exemple` en
`config/application.properties` (exclu de Git) et remplissez-le. **Ne mettez jamais ce
fichier dans `src/`.**

L'application **refuse de démarrer** sans `MONCHEPTEL_JWT_SECRET`, et c'est
voulu : une valeur par défaut pour un secret finit toujours en production, et
elle serait dans ce dépôt.

## Ce qui est vérifié

État au 8 octobre 2026 :

- `mvn package` : **98 tests, 0 échec**. Dont `SecuriteRoutesTest`, qui démarre le
  contexte Spring complet (H2) : toutes les requêtes `@Query` sont analysées par
  Hibernate et tous les beans sont câblés.
- `MigrationsMariaDbTest` : les **12 migrations** passent sur une base MariaDB jetable, puis le
  contexte démarre avec `ddl-auto: validate` sans aucun écart. Sans serveur joignable, le test est
  ignoré.
- `test-back-complet.ps1` : à lancer avec `MONCHEPTEL_LIMITE_DEBIT=false` (il se connecte des
  dizaines de fois). Il attend désormais 401 pour un jeton absent ou invalide.

## Structure

```
ml/moncheptel/api/
  modele/            26 entités + 25 énumérations — les règles métier
  repository/        24 interfaces Spring Data + 4 projections
  dto/requete/       20 objets d'entrée, validés
  dto/reponse/       32 objets de sortie — la couche « Vue » de ce MVC
  mapper/            10 traducteurs, écrits à la main
  service/           13 services + 2 interfaces d'infrastructure
  controleur/        11 contrôleurs REST
  securite/          JWT, filtre, principal
  config/            sécurité, Firebase, OpenAPI, tâches planifiées
  exception/         4 exceptions métier + le gestionnaire global
  infrastructure/    Firebase et SMTP, derrière les interfaces
```

### Où est la « Vue » d'un MVC sans page HTML ?

Dans `dto/reponse`. Une API REST ne rend rien à l'écran : ce sont ces objets,
sérialisés en JSON, que Flutter et Angular affichent. C'est donc là, et nulle
part ailleurs, que se décide ce qui sort du serveur. Le `package-info.java`
du paquet le dit explicitement — plutôt que de faire semblant avec un dossier
`vue/` vide.

## Les décisions qui méritent une question à la soutenance

**Les règles métier sont sur les entités, pas dans les services.**
`UniteElevage.retirer()` refuse de sortir 200 têtes d'un lot qui en compte
180. Un service qui ferait cette vérification laisserait la porte ouverte au
second appelant qui l'oublierait ; sur l'entité, elle est incontournable.

**Le verrou est pessimiste, pas optimiste.** Deux saisies simultanées de
sortie liraient toutes deux « 180 têtes » et écriraient 175 au lieu de 170 :
cinq bêtes disparaîtraient de la comptabilité. Un `@Version` aurait suffi
techniquement, mais il fait échouer la seconde saisie par une exception que
l'éleveur verrait comme un plantage. Ici, la seconde vente réussit, après la
première.

**Une vente se saisit une fois, pas deux.** L'écran de sortie demande un
montant ; le service crée le mouvement *et* la recette. Sinon l'éleveur
oublierait la seconde saisie une fois sur deux et le solde du mois cesserait
de correspondre au cheptel.

**Le seuil épidémique ne déclenche rien.** Le service compte les
signalements ; c'est un administrateur qui décide. Une alerte part vers tous
les éleveurs d'une région et ne se rattrape pas — un déclenchement
automatique ferait crier au loup à la première série de saisies mal faites,
et la deuxième alerte ne serait plus lue.

**Le score du diagnostic ne sort pas de l'API.** Les 115 écrans ont été
relus : aucun n'affiche de pourcentage. Deux noms, une gravité et une
conduite à tenir sont une orientation ; deux pourcentages sont une illusion
de mesure — d'autant que la pondération n'est pas validée (voir ci-dessous).

**Les mappers sont écrits à la main.** MapStruct génère le code à la
compilation : à la soutenance, personne ne pourrait montrer la ligne qui
masque le traitement d'une maladie. Ici c'est
`MaladieMapper.versReponseEleveur` contre `versReponseAdmin`, deux noms
différents pour qu'aucun appel ne choisisse le mauvais par inadvertance.

## Ce qui n'est pas validé

**Les 24 pondérations symptôme/maladie de `V3__referentiel.sql`.** Elles ne
viennent d'aucune source vétérinaire. Elles sont vraisemblables, pas
établies. Mesure faite : sur des symptômes spécifiques elles ne changent pas
le classement ; sur des symptômes communs (abattement, perte d'appétit) elles
sont la **seule** chose qui départage deux maladies. Elles ne sont donc ni
supprimables ni fiables. **Un vétérinaire doit les relire.**

**Le détail des coûts bovins, ovins et caprins.** Seuls les totaux par cycle
viennent de la maquette (48 000, 80 000, 260 000 FCFA) ; leur décomposition
en postes est une répartition plausible. Le référentiel volaille, lui, est
intégralement traçable.

Les deux sont signalés `[A VALIDER]` dans le fichier de migration.

## Routes ajoutées le 7 octobre 2026

Pour l'espace vétérinaire de l'application et la console d'administration. Détail dans
`../../CONTRAT-API-AJOUTS.md`.

- Vétérinaire : `GET /api/veterinaires/accueil`, `GET /api/veterinaires/competences`,
  `GET /api/signalements/maladies-catalogue`, `DELETE /api/conseils/{id}` ; un conseil publié
  peut être modifié et repasse en modération.
- Administration : `GET|POST /api/admin/conseils` (le POST publie directement, migration V7),
  `GET /api/admin/maladies/{id}`, `GET /api/admin/veterinaires/{id}`,
  `GET /api/admin/utilisateurs/{id}/fiche` ; les routes maladies et vétérinaires de
  l'administration rendent désormais `MaladieAdminReponse` et `VeterinaireAdminReponse`.
- `SignalementReponse` gagne `uniteType` et `uniteEffectif` ; `ConseilDetailReponse` gagne
  `dateCreation` et `dateSoumission`.
- Corrigé : la répartition des dépenses du rapport respecte maintenant le filtre de filière ;
  une adresse inconnue rend 404 (et non 500), une méthode refusée 405.

## Reste à faire avant une mise en service réelle

- Retirer la migration V4 (jeu de démonstration, mots de passe publics) de la base de production.
- Faire relire par un vétérinaire les pondérations `[A VALIDER]` de V3.
- Servir l'API en HTTPS et renseigner les variables ci-dessus (voir `EXPLOITATION.md`).
