# Démarrer le back — de A à Z

Une seule fois à faire, dans l'ordre. Chaque étape dit ce que vous devez
voir. Si vous ne le voyez pas, arrêtez-vous là et envoyez-moi la sortie.

---

## A. Ouvrir PowerShell dans le bon dossier

```powershell
cd C:\Users\balla\Downloads\moncheptel\back\api
```

**Vous devez voir** l'invite finir par `...\api>`.

---

## B. Vérifier que Maven trouve un Java

```powershell
mvn -version
```

**Vous devez voir** une ligne `Java version: 25.0.2` (ou 21, peu importe).
Si Maven n'est pas reconnu, il n'est pas installé ou pas dans le PATH.

---

## C. Vérifier que MySQL répond

```powershell
mysql -u root -p -e "SELECT VERSION();"
```

Tapez votre mot de passe MySQL quand il le demande.

**Vous devez voir** un numéro de version (`8.0.xx`).

- `Access denied` → ce n'est pas le bon mot de passe.
- `mysql n'est pas reconnu` → MySQL n'est pas dans le PATH. Utilisez
  MySQL Workbench pour l'étape D, puis passez à E.

---

## D. Créer la base

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS moncheptel_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**Vous devez voir** : rien du tout. Pas de message = c'est bon.

---

## E. Donner le mot de passe MySQL à l'application

```powershell
$env:MONCHEPTEL_DB_PASSWORD = "votre-mot-de-passe-mysql"
```

**Vous devez voir** : rien. Vérifiez avec :

```powershell
$env:MONCHEPTEL_DB_PASSWORD
```

qui doit réafficher votre mot de passe.

> Cette variable ne vit que dans **cette fenêtre** de terminal. Si vous la
> fermez, il faut refaire l'étape E. C'est voulu : votre mot de passe ne
> finit dans aucun fichier du projet.

---

## F. Lancer

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Les guillemets sont obligatoires.** Sans eux, PowerShell coupe l'argument
au premier point et Maven répond `Unknown lifecycle phase`.

---

## G. Lire les quatre lignes qui comptent

Dans le flot de démarrage, cherchez ces quatre-là, dans cet ordre :

**1. Le profil**

```
The following 1 profile is active: "dev"
```

Si vous lisez `No active profile set`, les guillemets de l'étape F ont sauté.

**2. Les migrations**

```
Successfully applied 12 migrations to schema `moncheptel_dev`
```

C'est Flyway qui a créé les 28 tables, les 22 index, le référentiel et le
jeu de démonstration.

**3. Les requêtes**

Pas de ligne à voir ici — c'est une **absence** qui est bon signe. Si une de
mes 21 requêtes `@Query` était fautive, vous verriez
`QuerySyntaxException` ou `Could not interpret path`.

**4. Le démarrage**

```
Started MonCheptelApplication in X.XXX seconds
```

---

## H. Ouvrir Swagger

http://localhost:8080/swagger-ui.html

**Vous devez voir** la liste des routes, groupées par contrôleur.

---

## I. Se connecter

Dans Swagger, dépliez `POST /api/auth/connexion`, cliquez **Try it out**,
et collez :

```json
{ "email": "balla.kone@example.ml", "motDePasse": "eleveur2026" }
```

**Vous devez voir** une réponse `200` contenant un `jetonAcces` (une longue
chaîne en trois parties séparées par des points).

---

## J. Autoriser Swagger, puis vérifier un chiffre

Copiez la valeur de `jetonAcces`. En haut à droite de la page, bouton
**Authorize**, collez-la, **Authorize**, **Close**.

Puis dépliez `GET /api/tableau-de-bord`, **Try it out**, **Execute**.

**Vous devez voir** :

```json
"unitesActives": 3,
"tetesRestantes": 320,
"soldeDuMois": { "revenus": 101500, "depenses": 27500, "solde": 74000 }
```

Ce sont les chiffres de la maquette. Si vous les lisez, le back fonctionne
de bout en bout : base, migrations, requêtes, sécurité, sérialisation.

---

## Arrêter

`Ctrl+C` dans le terminal.

## Relancer plus tard

Étapes **E** et **F** seulement. La base et les migrations sont déjà là.

## Tout refaire à zéro

```powershell
mysql -u root -p -e "DROP DATABASE moncheptel_dev;"
```

puis reprendre à l'étape D. À faire **la veille de la soutenance** : les
dates du jeu de démonstration sont calculées au moment des migrations, et
LOT-014 doit être à J+16 le jour où vous le montrez.
