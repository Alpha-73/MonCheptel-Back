# Tester le back avec Swagger

De zéro à « je clique sur les routes et je vois les chiffres de la maquette ».
Comptez 30 à 45 minutes la première fois, dont l'essentiel en téléchargements.

---

## 1. Ce qu'il faut installer

| | version | vérifier avec |
|---|---|---|
| JDK | **21** | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |

**Java 21 exactement, pas 17 ni 25.** Le `pom.xml` cible 21 ; avec 17 la
compilation échoue, avec 25 Spring Boot 3.5 n'est pas encore validé dessus.
Si `java -version` dit autre chose, installez le JDK 21 (Temurin ou Oracle)
et pointez `JAVA_HOME` dessus.

> Sous Windows, vérifiez que `mvn -version` affiche bien `Java version: 21`.
> Maven utilise `JAVA_HOME`, pas le `java` du PATH : les deux peuvent
> diverger, et c'est la première cause d'échec.

---

## 2. Créer la base

```sql
mysql -u root -p
```

```sql
CREATE DATABASE moncheptel_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

Le profil `dev` se connecte en `root` sans mot de passe. Si votre `root` en
a un, ouvrez `src/main/resources/application-dev.yml` et renseignez-le — ce
fichier n'est pas destiné à la production, il peut contenir un mot de passe
local.

**Rien d'autre à créer.** Flyway construit les 28 tables, les 22 index, le
référentiel et le jeu de démonstration au premier démarrage.

---

## 3. Compiler et tester

```bash
cd api
mvn clean test
```

> `mvn clean test` ne prend pas d'argument `-D`, il passe tel quel sous
> PowerShell. C'est la commande suivante qui demande des guillemets.

**C'est le premier vrai test de ce code.** Maven Central est bloqué dans mon
environnement : je n'ai jamais pu télécharger une seule dépendance ni lancer
`mvn`. J'ai vérifié les types avec des *stubs* d'annotations (160 classes aux
signatures réelles) — 0 erreur, 0 avertissement sur 189 fichiers — mais ça ne
prouve pas que les versions réelles ont exactement ces signatures.

**Renvoyez-moi la sortie, même si elle est longue.** Ce qui peut tomber, par
ordre de probabilité :

| erreur | cause probable |
|---|---|
| `package X does not exist` | une signature de bibliothèque diffère de mon stub |
| `cannot find symbol` dans un test | AssertJ n'a pas cette méthode sous ce nom |
| `Error creating bean` | une dépendance circulaire entre services |
| `QuerySyntaxException` | faute de frappe dans un `@Query` — Hibernate les analyse au démarrage |
| `Schema-validation: missing column` | une entité et une migration divergent |

Les deux dernières sont les intéressantes : elles ne peuvent apparaître qu'au
démarrage de Spring, et c'est précisément ce que je n'ai pas pu faire.

---

## 4. Lancer

**Sous PowerShell** (le terminal par défaut de Windows) :

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Les guillemets sont **obligatoires**. Sans eux, PowerShell découpe l'argument
au premier point et passe `.run.profiles=dev` à Maven comme un argument
séparé ; Maven répond `Unknown lifecycle phase ".run.profiles=dev"`.

Plus simple, et valable pour toute la session du terminal :

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

Sous bash, macOS ou Linux, les guillemets ne sont pas nécessaires :

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Au premier lancement, Flyway écrit dans le journal :

```
Migrating schema `moncheptel_dev` to version "1 - schema initial"
Migrating schema `moncheptel_dev` to version "2 - index"
Migrating schema `moncheptel_dev` to version "3 - referentiel"
Migrating schema `moncheptel_dev` to version "4 - jeu de demonstration"
Successfully applied 12 migrations
```

Puis :

```
Started MonCheptelApplication in X.XXX seconds
```

Ouvrez **http://localhost:8080/swagger-ui.html**

---

## 5. Les quatre comptes

Depuis la migration **V5**, l'identifiant de connexion est l'**adresse de
courriel**, plus le numéro de téléphone. Le téléphone reste dans la fiche,
mais il n'est plus unique et ne sert plus à se connecter.

| adresse | mot de passe | rôle |
|---|---|---|
| `balla.kone@example.ml` | `eleveur2026` | Balla Koné — éleveur, Koutiala |
| `aissata.traore@example.ml` | `veto2026` | Dr Aïssata Traoré — vétérinaire |
| `admin@moncheptel.ml` | `admin2026` | Administrateur |
| `moussa.diarra@example.ml` | `veto2026` | Dr Moussa Diarra — vétérinaire |

Ces mots de passe sont écrits dans `V4__jeu_de_demonstration.sql`, donc
publics. **Supprimez cette migration avant toute mise en service réelle**,
ainsi que le bloc d'identifiants de l'en-tête de `V5__identifiant_email.sql`.

Attention pour la démonstration du courriel : `@example.ml` n'est pas un
domaine livrable. La réinitialisation par courriel ne peut être montrée
**que** sur un compte dont l'adresse existe vraiment, ou en profil `dev`,
qui affiche le message dans la console du serveur au lieu de l'envoyer.

---

## 6. Le parcours de test

Faites-le dans cet ordre : chaque étape utilise ce que la précédente produit.

### Se connecter

`POST /api/auth/connexion`

```json
{ "email": "balla.kone@example.ml", "motDePasse": "eleveur2026" }
```

Copiez le `jetonAcces` de la réponse. En haut à droite de Swagger, cliquez
**Authorize**, collez le jeton, validez. Toutes les routes suivantes en ont
besoin.

> **Vérifiez aussi l'échec.** Rejouez avec un mauvais mot de passe : vous
> devez recevoir `422` et le message « Numéro ou mot de passe incorrect ».
> Le même message pour un numéro inconnu — c'est voulu, distinguer les deux
> renseignerait un attaquant. **Cinq échecs de suite verrouillent le compte
> 15 minutes.** Si vous testez ça, testez-le en dernier.

### Le tableau de bord

`GET /api/tableau-de-bord`

Vous devez lire exactement les chiffres de la maquette :

```
unitesActives      3
tetesRestantes     320
unitesCloturees    1
tetesSorties       150
soldeDuMois        { revenus: 101500, depenses: 27500, solde: 74000 }
alertes            1  (Newcastle — Koutiala)
rappels            1  (Newcastle J21, dans 5 jours)
notificationsNonLues  2
```

**Si `solde` ne vaut pas 74 000**, c'est que la migration a été lancée dans
les premiers jours du mois : certaines opérations se sont tassées sur le 1er.
Le total reste juste, la répartition par date change.

### Le cheptel

`GET /api/cheptel` → 11 unités : LOT-014 (180), LOT-009 (60), LOT-006 (80),
LOT-003 (clôturé), et 7 animaux dont BOV-009 vendu.

`GET /api/cheptel?filiere=VOLAILLE&statut=ACTIF` → les 3 lots.

`GET /api/cheptel/{id}` sur LOT-014 → la fiche avec ses mouvements, son
carnet sanitaire, ses 2 rappels et sa rentabilité.

### La règle qui compte

`POST /api/cheptel/{id}/sorties` sur **LOT-014** (180 têtes) :

```json
{ "type": "VENTE", "nombreTetes": 200, "date": "2026-09-28", "montant": 150000 }
```

→ **`422`** et le message : *« L'unité LOT-014 ne compte que 180 tête(s) »*

C'est la règle la plus importante du modèle, et elle vit sur l'entité, pas
dans le service. Essayez aussi :

- `"nombreTetes": 0` → 422
- `"type": "MISE_EN_PLACE"` → 422 (un motif d'entrée n'est pas une sortie)
- `"type": "MORT"` avec un `montant` → 422 (seule une vente porte un montant)
- `"type": "VENTE"` sans `montant` → 422

Puis la vraie vente :

```json
{ "type": "VENTE", "nombreTetes": 80, "date": "2026-09-28",
  "montant": 67200, "motif": "Vente au marché" }
```

→ `201`, LOT-014 passe à 100 têtes, statut toujours `ACTIF`.

**Rappelez `GET /api/finances/solde`** : le solde a augmenté de 67 200 sans
que vous ayez saisi la recette. Une vente se saisit une fois, pas deux — le
service crée le mouvement **et** l'opération financière.

Vendez les 100 restantes : le lot passe à `CLOTURE`, pas à `VENDU`. Un lot se
clôture ; `VENDU` est un statut d'animal. Rejouez une sortie dessus → 422,
*« déjà sortie du cheptel »*.

### Le signalement par symptômes

`GET /api/signalements/symptomes?filiere=VOLAILLE` → 10 symptômes, dans
l'ordre de la maquette (les plus fréquents d'abord).

Notez les `id` de **Toux**, **Diarrhée verdâtre**, **Torticolis**, puis :

`POST /api/signalements`

```json
{ "uniteId": <id de LOT-009>, "symptomeIds": [1, 2, 4],
  "trancheEffectif": "DE_10_A_50",
  "precision": "Surtout dans le fond du poulailler" }
```

→ `diagnostic.probable` = **Maladie de Newcastle**
→ `diagnostic.alternative` = **Bronchite infectieuse**
→ `gravite` = `ELEVE`, `orientationRequise` = `true`
→ `veterinaire` = Dr Aïssata Traoré

**`probable.traitement` doit être `null`.** Newcastle est marquée
« oriente vers vétérinaire » : le protocole ne quitte pas le serveur. Si vous
le voyez dans la réponse JSON, c'est un défaut à me signaler immédiatement.

Essayez maintenant **Diarrhée avec sang + Abattement** → Coccidiose, et cette
fois `traitement` est renseigné (l'éleveur peut traiter seul).

Et un `symptomeIds` avec un identifiant inexistant (`[9999]`) → 422,
*« Un des symptômes sélectionnés est inconnu »*.

### Le simulateur

`POST /api/investissement/simulation`

```json
{ "budget": 50000, "inclureInstallation": false }
```

→ 4 options triées. **Volaille : `coutCycle` = 47 593, `ecartBudget` = 2 407,
`realisable` = true.** Puis Caprins 48 000 (juste au-dessus), Ovins 80 000 et
Bovins 260 000 hors budget avec leur manque.

`POST /api/investissement/simulation/{typeElevageId}` sur la volaille, avec
`"sujetsVises": 30` :

→ `sujetsAAcheter` = **33** (30 + 3 pour la mortalité à 8 %)
→ `postesCycle` : aliment **59,4 kg × 345 FCFA = 20 493**
→ `postesInstallation` : logement **10 m² × 1 800 = 18 000**, total 26 000

Ce sont les chiffres de l'écran « Investir — Détail volaille », au franc près.

### Côté vétérinaire

Reconnectez-vous en `76000000 / veto2026`, réautorisez Swagger avec le
nouveau jeton.

`GET /api/signalements/file` → le signalement sur LOT-014, et celui que vous
venez de créer. Les plus graves d'abord.

> Ce tri est passé par un défaut que j'ai corrigé : `gravite` est stockée en
> clair (`ELEVE`, `MOYEN`, `FAIBLE`), et un tri alphabétique décroissant
> donne MOYEN, FAIBLE, ELEVE — les cas les plus graves arrivaient **en
> dernier**, sans aucune erreur. Vérifiez que `ELEVE` est bien en tête.

`POST /api/signalements/{id}/prise-en-charge` → statut `EN_COURS`
`POST /api/signalements/{id}/cloture` avec un compte rendu → `TRAITE`, et
l'éleveur reçoit une notification.

`POST /api/conseils` pour rédiger, puis `POST /api/conseils/{id}/soumission`.
Un vétérinaire ne se publie pas lui-même.

### Côté administration

Reconnectez-vous en `65000000 / admin2026`.

`GET /api/admin/moderation` → le conseil ovins en attente, plus le vôtre.

`POST /api/admin/moderation/{id}` avec `{ "publier": false }` **sans motif**
→ 422. Un retrait sans motif ne dit pas au vétérinaire quoi corriger.

`GET /api/admin/alertes/apercu?maladieId=1&zoneId=2` → le nombre de
signalements récents et d'éleveurs ciblés. **Ces deux nombres ne déclenchent
rien** : c'est vous qui décidez.

`POST /api/admin/alertes` sur Newcastle + Koutiala → **409**, une alerte est
déjà active. Essayez Coccidiose : `201`, et tous les éleveurs de volaille de
Koutiala reçoivent une notification.

`GET /api/admin/types-elevage` → le référentiel, décomposé poste par poste.

### Ce qui doit être refusé

Reconnectez-vous en **éleveur** et appelez `GET /api/admin/moderation`
→ **403**. Puis `GET /api/cheptel/{id}` avec l'identifiant d'une unité qui
n'est pas à vous → **403**, *« Cette ressource ne vous appartient pas »*.
Sans jeton, ou avec un jeton expiré → **401**, *« Session expirée. Reconnectez-vous. »*.
Onze connexions en une minute depuis la même adresse → **429**, avec un en-tête `Retry-After`.

Ce second test est le plus important de la liste. Un éleveur qui incrémente
l'identifiant dans l'URL et lit le cheptel de son voisin, c'est la faille la
plus banale et la plus répandue des API REST. Le contrôle est dans le
service, pas dans le contrôleur — `@PreAuthorize("hasRole('ELEVEUR')")` dit
« c'est un éleveur », pas « c'est son lot ».

---

## 7. Remettre le décor à l'endroit

Les dates du jeu de démonstration sont calculées **au moment où la migration
s'exécute**. LOT-014 est à J+16 le jour de l'installation ; deux semaines plus
tard il sera à J+30 et son cycle sera presque fini.

**La veille de la soutenance**, relancez à blanc :

```sql
DROP DATABASE moncheptel_dev;
CREATE DATABASE moncheptel_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

puis relancez l'application. Le décor est remis : J+16, rappel dans 5 jours,
solde à +74 000.

---

## 8. Ce que ce test ne couvre pas

- **Les notifications push.** Sans fichier de clé Firebase, elles sont
  enregistrées en base sans être envoyées. C'est voulu : l'application
  démarre sans Firebase, et l'éleveur les voit quand même dans l'app.
- **Le courriel du mot de passe provisoire.** `POST /api/admin/veterinaires`
  échouera sans configuration SMTP — et fera échouer la création du compte
  avec, ce qui est le bon comportement : un compte dont personne ne connaît
  le mot de passe ne sert à rien.
- **La concurrence.** Le verrou pessimiste sur les sorties ne se voit pas en
  cliquant dans Swagger. Il faudrait deux appels simultanés.
