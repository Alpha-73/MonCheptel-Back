# Essayer MonCheptel sur votre PC (Windows)

Trois choses tournent en même temps, chacune dans sa propre fenêtre PowerShell :
le **back** (l'API), la **console d'administration** et l'**application mobile**.
Lancez toujours le back en premier : les deux autres en dépendent.

---

## 0. Ce qu'il faut avoir installé

Ouvrez PowerShell et vérifiez chaque ligne :

| Commande | Il faut voir |
|---|---|
| `java -version` | 21 ou plus |
| `mvn -version` | Apache Maven 3.9 ou plus |
| `mysql --version` | MySQL 8 (le service MySQL doit être démarré) |
| `node -v` | v22.12 ou plus (ou v24) |
| `flutter --version` | Flutter 3.38 |
| `flutter doctor` | coches vertes sur « Android toolchain » |

Pour l'application, il faut **soit** un émulateur Android (Android Studio, menu Device Manager),
**soit** un vrai téléphone Android avec un câble USB.

---

## 1. Décompresser le projet

Décompressez `moncheptel-propre.zip`, par exemple dans `C:\moncheptel`. Vous obtenez
`C:\moncheptel\moncheptel\` avec les dossiers `back`, `app`, `admin` et `maquettes`.

---

## 2. Lancer le back (fenêtre 1)

```powershell
cd C:\moncheptel\moncheptel\back\api
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\lancer.ps1
```

Le script vous demande le mot de passe MySQL, crée la base `moncheptel_dev` et démarre l'API.
La première fois, Maven télécharge ses dépendances : comptez quelques minutes.

**C'est prêt quand** la fenêtre affiche `Started MonCheptelApplication`.
Vérifiez dans le navigateur : http://localhost:8080/api/zones doit afficher une liste de zones.

**Laissez cette fenêtre ouverte.** `Ctrl+C` arrête le back.

> Les courriels (mot de passe oublié) ne partent que si vous créez
> `back\api\config\application.properties` à partir de `application.properties.exemple`,
> avec un **nouveau** mot de passe d'application Gmail. Révoquez d'abord l'ancien, celui
> qui était dans le premier zip. Tout le reste fonctionne sans courriel.

---

## 3. Lancer la console d'administration (fenêtre 2)

```powershell
cd C:\moncheptel\moncheptel\admin
npm install
npm start
```

`npm install` ne sert que la première fois. Ouvrez ensuite **http://localhost:4200**
(ce port précis, sinon l'API refuse la console).

Connexion : `admin@moncheptel.ml` / `admin2026`.

---

## 4. Lancer l'application mobile (fenêtre 3)

```powershell
cd C:\moncheptel\moncheptel\app
flutter pub get
```

La première compilation Android prend plusieurs minutes (Gradle). Les suivantes sont rapides.

### Option A : émulateur Android

1. Dans Android Studio, ouvrez **Device Manager** et démarrez un appareil.
2. Lancez :
   ```powershell
   flutter run
   ```

L'émulateur joint le back tout seul : `10.0.2.2` est l'adresse du PC vue depuis l'émulateur.

### Option B : vrai téléphone (même Wi-Fi que le PC)

1. Sur le téléphone : **Paramètres > À propos > Numéro de build**, tapez 7 fois. Puis, dans
   **Options pour les développeurs**, activez **Débogage USB**.
2. Branchez le câble et acceptez l'autorisation sur le téléphone. `flutter devices` doit
   lister le téléphone.
3. Trouvez l'adresse du PC : `ipconfig`, ligne **Adresse IPv4** de la carte Wi-Fi
   (par exemple `192.168.1.20`).
4. Ouvrez le port 8080 du PC. Dans un PowerShell **lancé en administrateur** :
   ```powershell
   New-NetFirewallRule -DisplayName "MonCheptel API" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow -Profile Private
   ```
5. Test depuis le navigateur **du téléphone** : `http://192.168.1.20:8080/api/zones` doit afficher
   la liste des zones. Sinon, inutile d'aller plus loin : c'est le réseau ou le pare-feu.
6. Lancez, avec **votre** adresse :
   ```powershell
   flutter run --dart-define=API_URL=http://192.168.1.20:8080
   ```

### Option C : un APK à installer sans câble

```powershell
flutter build apk --debug --dart-define=API_URL=http://192.168.1.20:8080
```

Le fichier est `build\app\outputs\flutter-apk\app-debug.apk`. Copiez-le sur le téléphone et
ouvrez-le (autorisez « installer des applications inconnues »). La version de publication
(`--release`) exige une API en HTTPS et une clé de signature : voir la section 8.

---

## 5. Le parcours à essayer

**Éleveur** (application)
1. Le splash s'anime pendant 3 secondes, puis le choix du profil apparaît. Touchez **Éleveur**.
2. Connectez-vous avec `balla.kone@example.ml` / `eleveur2026`.
3. Parcourez les cinq onglets : Accueil, Animaux, Santé, Conseils, Profil.
4. Dans **Santé**, choisissez **Déclarer une maladie**, cochez des symptômes et envoyez.
5. **Profil > Se déconnecter** ramène à la connexion éleveur : touchez **Créer un compte** pour
   essayer l'inscription en deux étapes.

**Vétérinaire** (application)
1. Au choix du profil, touchez **Vétérinaire**.
2. Connectez-vous avec `aissata.traore@example.ml` / `veto2026`.
3. Dans **Demandes**, ouvrez un cas : l'ouvrir ne l'accepte pas. Touchez **Accepter le cas**
   (l'éleveur est prévenu) ou **Refuser** avec un motif facultatif (l'éleveur est prévenu et
   choisit un autre vétérinaire). Appeler l'éleveur n'engage à rien ; la clôture vaut acceptation.
4. Dans **Conseils**, rédigez un conseil : il part en modération.

**Administrateur** (console web)
1. Le tableau de bord affiche les chiffres de la plateforme.
2. Dans **Conseils**, validez le conseil que le vétérinaire vient d'écrire.
3. Dans **Alertes**, déclenchez une alerte sur une zone. Les éleveurs de la zone la voient sur leur accueil.
4. Dans **Vétérinaires**, créez un compte. Le vétérinaire reçoit par courriel un mot de passe
   provisoire, qu'il change à sa première connexion. Il faut donc que les courriels soient
   configurés (encadré de l'étape 2) pour aller jusqu'au bout.

---

## 6. Si ça bloque

| Ce que vous voyez | Ce qu'il faut faire |
|---|---|
| `lancer.ps1` refusé (« exécution de scripts désactivée ») | Refaire la ligne `Set-ExecutionPolicy` de l'étape 2, dans la même fenêtre |
| `Access denied for user 'root'` | Mauvais mot de passe MySQL : relancez `.\lancer.ps1` |
| `Port 8080 was already in use` | Un ancien back tourne encore : fermez sa fenêtre, ou redémarrez le PC |
| L'application reste sur « Connexion… » puis « Connexion impossible » | Le téléphone ne joint pas le back : refaites le test du point 5 de l'option B |
| La console affiche une erreur à la connexion | Le back n'est pas lancé, ou la console n'est pas sur le port 4200 |
| « Trop d'essais depuis ce réseau. Réessayez dans … » (429) | Limite de débit des connexions : attendez le délai indiqué. Pour dérouler `test-back-complet.ps1`, lancez le back avec `$env:MONCHEPTEL_LIMITE_DEBIT = 'false'` |
| `flutter devices` ne voit pas le téléphone | Câble de données (pas seulement de charge), débogage USB activé, autorisation acceptée |

---

## 7. Repartir de zéro

Pour retrouver les données de démonstration d'origine :

```powershell
mysql -u root -p -e "DROP DATABASE moncheptel_dev;"
```

Puis relancez `.\lancer.ps1` : la base est recréée et remplie de nouveau.
À faire la veille d'une démonstration, car les dates du jeu d'essai sont calculées le jour de la création.

---

## 8. Version de publication Android (APK ou AAB signé)

La version `--release` refuse de se construire tant que deux conditions ne sont pas remplies :
une **clé de signature** décrite dans `app\android\key.properties`, et une **adresse d'API en
HTTPS**. Le message d'erreur de Gradle dit laquelle manque. Si par erreur une version de
publication est lancée avec une adresse HTTP, l'application affiche un écran
« Configuration invalide » au lieu d'envoyer des mots de passe en clair.

1. **Créer la clé, une seule fois** (PowerShell ; `keytool` est fourni avec Android Studio) :
   ```powershell
   mkdir C:\cles
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore C:\cles\moncheptel-publication.jks -keyalg RSA -keysize 2048 -validity 10000 `
     -alias moncheptel
   ```
   Retenez les deux mots de passe demandés. **Sauvegardez le fichier `.jks` et ses mots de passe
   hors du PC** : sans eux, plus aucune mise à jour de l'application n'est possible.
2. **Décrire la clé** :
   ```powershell
   cd app\android
   Copy-Item key.properties.exemple key.properties
   notepad key.properties
   ```
   Renseignez `storeFile=C:/cles/moncheptel-publication.jks` (barres obliques `/`), les deux mots
   de passe et `keyAlias=moncheptel`. `key.properties` et les fichiers `.jks` sont exclus de git :
   ne les commitez jamais.
3. **Construire**, depuis le dossier `app`, avec l'adresse HTTPS de l'API hébergée :
   ```powershell
   flutter build apk --release --dart-define=API_URL=https://api.votre-domaine.ml
   # ou, pour le Play Store :
   flutter build appbundle --release --dart-define=API_URL=https://api.votre-domaine.ml
   ```
   Fichiers produits : `build\app\outputs\flutter-apk\app-release.apk`, ou
   `build\app\outputs\bundle\release\app-release.aab`.

---

## 9. Mise en ligne du back

Tout est décrit dans `back\api\EXPLOITATION.md` : premier administrateur, HTTPS par un proxy
nginx, sauvegarde et restauration de la base, journaux. L'essentiel :

- **Premier administrateur.** Une base de production n'a pas le jeu de démonstration, donc aucun
  administrateur. Avant le premier lancement, définissez `MONCHEPTEL_ADMIN_EMAIL` et
  `MONCHEPTEL_ADMIN_MOT_DE_PASSE_INITIAL` (12 caractères au moins), ou les lignes commentées
  de `back\api\config\application.properties.exemple`. Le compte est créé au démarrage avec ce
  mot de passe **provisoire**, à changer à la première connexion. Retirez ensuite ces valeurs.
  S'il existe déjà un administrateur, rien n'est modifié.
- **Sauvegarde** : `back\api\exploitation\sauvegarde-mariadb.ps1` (Windows) ou
  `sauvegarde-mariadb.sh` (Linux), à planifier chaque nuit ; restauration par
  `restauration-mariadb.ps1` / `.sh`. Essayez une restauration au moins une fois.
- **HTTPS** : `back\api\exploitation\nginx-moncheptel.conf.exemple`. L'API n'écoute qu'en local,
  nginx porte le certificat.

