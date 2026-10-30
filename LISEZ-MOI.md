# MonCheptel

Le compagnon des éleveurs connectés : gestion du cheptel, santé animale et diagnostic orienté,
mise en relation avec un vétérinaire, finances, conseils et alertes sanitaires.

État au 8 octobre 2026 (après l'audit global) : **les trois espaces sont terminés et branchés sur l'API.**

| Dossier | Contenu | Techno | Vérifié |
|---|---|---|---|
| `back/api` | API REST, base, migrations, notifications, courriels | Spring Boot 3.5, Java 21, MySQL, Flyway | `mvn package` : 98 tests OK (dont les 12 migrations Flyway rejouées sur une base MariaDB jetable) ; `test-back-complet.ps1` : à lancer avec `MONCHEPTEL_LIMITE_DEBIT=false` |
| `app` | Application mobile : espace éleveur et espace vétérinaire | Flutter 3.38 (Android) | `flutter analyze` : 0 problème ; `flutter test` : 75 tests OK |
| `admin` | Console web d'administration | Angular 21 | `npm run build` OK ; 78 tests OK |
| `maquettes` | Exports Figma (accueil, vétérinaire, administration) et le logo en SVG (`maquettes/logo`) | SVG | référence de conformité |
| `CONTRAT-API-AJOUTS.md` | Routes ajoutées pour l'espace vétérinaire et la console | | |

## Lancer le projet en local (Windows)

Guide pas à pas, avec le parcours d'essai et les problèmes fréquents : `GUIDE-ESSAI.md`.

Prérequis : JDK 21, Maven 3.9+, MySQL 8, Flutter 3.38 et le SDK Android, Node 22.12+ (ou 24).

1. **Back** : voir `back/api/DEMARRER.md`. En résumé :
   ```powershell
   cd back\api
   .\lancer.ps1
   ```
   L'API répond sur `http://localhost:8080`, Swagger sur `/swagger-ui.html` (profil dev seulement).
2. **Console d'administration** :
   ```powershell
   cd admin
   npm install
   npm start
   ```
   Puis `http://localhost:4200`. Elle doit être servie sur le port 4200 (CORS).
3. **Application mobile** :
   ```powershell
   cd app
   flutter pub get
   flutter run
   ```
   Sur un vrai téléphone : `flutter run --dart-define=API_URL=http://<IP du PC>:8080`.

## Comptes de démonstration (migration V4, base de développement uniquement)

| E-mail | Mot de passe | Espace |
|---|---|---|
| `balla.kone@example.ml` | `eleveur2026` | éleveur (application) |
| `aissata.traore@example.ml` | `veto2026` | vétérinaire (application) |
| `admin@moncheptel.ml` | `admin2026` | administration (console web) |

## Avant une mise en service réelle

- Héberger l'API en **HTTPS** avec `MONCHEPTEL_JWT_SECRET`, `MONCHEPTEL_DB_PASSWORD`,
  `MONCHEPTEL_CORS_ORIGINES` et les variables SMTP (voir `back/api/LISEZ-MOI.md`).
- Ne pas appliquer la migration V4 (jeu de démonstration) en production, ou désactiver ses comptes dès le
  premier jour. Le premier administrateur se crée au démarrage par `MONCHEPTEL_ADMIN_EMAIL` et
  `MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL` (voir `back/api/EXPLOITATION.md`, avec HTTPS, sauvegardes et journaux).
- Faire relire par un vétérinaire les pondérations du diagnostic marquées `[A VALIDER]` (V3).
- Signer l'application avec une vraie clé (`app/android/key.properties`, voir `GUIDE-ESSAI.md`, section 8) :
  la version `--release` refuse de se construire sans elle, ou sans API en HTTPS.
- Notifications push : le back sait les envoyer via Firebase, mais l'application ne les reçoit pas
  encore (pas de `firebase_messaging`). Elles restent visibles dans l'écran Notifications.
