# Audit de démarrage du back MonCheptel

Écrit le 29 septembre 2026, après quatre échecs de démarrage corrigés un par
un. Le but est de ne plus en découvrir un cinquième par tâtonnement.

## Pourquoi cet audit existe

Maven Central est inaccessible depuis mon environnement (403 du proxy, vérifié).
Je ne peux donc ni compiler avec Maven ni démarrer Spring. Trois choses en
découlent, et ce sont exactement les trois qui ont échoué chez vous :

| ce qui a cassé | ce qui l'aurait vu |
|---|---|
| `conseil.contenu` en `tinytext` | démarrage de Hibernate |
| `@Transactional` sur un filtre de servlet | démarrage de Tomcat |
| `FirebaseApp` absent | câblage des beans |
| `checksum mismatch` Flyway | démarrage, sur une base déjà migrée |

Aucune n'est visible à la compilation. Aucune n'est visible dans les 26 tests
unitaires. L'audit ci-dessous les cherche **statiquement**, en analysant les
sources contre le modèle réel.

## Ce qui est vérifié, et comment

Lancer : `./auditer.sh`

| n° | contrôle | volume | ce qui serait attrapé |
|---|---|---|---|
| 1 | modèle JPA reconstruit depuis les sources | 25 entités, héritage résolu | sert de référentiel aux suivants |
| 2 | requêtes `@Query` | 52 | entité inconnue, alias non déclaré, chemin de propriété faux à tout niveau, constante d'énumération inexistante, `:param` sans `@Param` |
| 3 | méthodes dérivées Spring Data | 40 | segment de nom qui ne correspond à aucune propriété, y compris dans un `OrderBy` |
| 4 | câblage Spring | 42 beans, 54 routes, 5 `@Modifying`, 4 projections | cycle de dépendances, projection dont un accesseur n'a pas d'alias `as`, `@Modifying` appelé hors transaction, collision de route, `@Transactional` sur une classe proxifiée en CGLIB |
| 5 | graphes et rôles | 3 `@EntityGraph`, 20 `hasRole` | attribut inexistant dans un `attributePaths`, rôle qui n'est pas une valeur de `Role` |

## Comment je sais que l'audit détecte vraiment quelque chose

**Un audit qui ne trouve rien ne prouve rien tant qu'on n'a pas montré qu'il
mord.** Chaque contrôle a donc été validé en injectant de vraies fautes dans
une copie du code et en vérifiant qu'elles ressortent. Treize fautes,
treize détections :

| faute injectée | détectée |
|---|---|
| `l.proprietaireX.id` | oui |
| `l.typeElevage.filiereZ` (2ᵉ niveau) | oui |
| `StatutAlerte.ACTIVEE` | oui |
| `enumeration.StatutConseilX` | oui |
| `from SymptomeInconnu s` | oui |
| `@Param("filiereAutre")` sans `:filiereAutre` | oui |
| `findByProprietaireIdAndCodeBarre` | oui |
| `countByDestinataireIdAndLuFalse` | oui |
| `OrderByDateEntreeeDesc` | oui |
| `findAllByOrderByNommAsc` | oui |
| alias `as recettes` au lieu de `as revenus` | oui |
| cycle `DiagnosticService ↔ SignalementService` | oui |
| `marquerToutesLues` sans `@Transactional` | oui |
| `GET /api/zones` déclarée deux fois | oui |
| `@Transactional` sur `FiltreJwt` | oui |
| `@EntityGraph("postesCouts")` | oui |
| `hasRole('ROLE_ELEVEUR')` | oui |

Deux de ces tests ont d'abord échoué **par ma faute, pas celle de
l'analyseur** — et les corriger a rendu l'audit meilleur :

- l'analyseur comptait chaque requête deux fois et attribuait à une méthode
  les `@Param` de la suivante ;
- il lisait `@Transactional` **dans un commentaire** et signalait `FiltreJwt`
  alors que je venais d'en retirer l'annotation. Il ignore désormais les
  commentaires.

## Ce qui reste hors de portée d'une analyse statique

Je préfère le nommer que le passer sous silence.

- **La sémantique fine du JPQL.** Les chemins sont vérifiés, pas la validité
  de chaque construction. `(:filiere is null or o.filiere = :filiere)` avec un
  paramètre d'énumération nul peut demander un transtypage selon la base ;
  `member of` sur une `@ElementCollection` d'énumérations est validé par
  Hibernate seul.
- **La sérialisation Jackson** des 32 objets de réponse. Un cycle ou un
  accesseur manquant se voit à la première requête HTTP, pas au démarrage.
- **Le comportement des 54 routes.** L'audit vérifie qu'elles ne se marchent
  pas dessus, pas qu'elles répondent juste. C'est le rôle du parcours Swagger
  de `TESTER-LE-BACK.md`.
- **La concurrence.** Le verrou pessimiste sur les sorties de cheptel ne se
  vérifie qu'avec deux appels simultanés.

## À relancer quand

Après toute modification des entités, des repositories, des services ou des
contrôleurs. L'audit prend deux secondes et ne demande ni base de données ni
réseau.

Si vous touchez aux **entités**, régénérez aussi le schéma attendu par
Hibernate et comparez-le aux migrations :

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,ddl"
```

C'est ce fichier qui a révélé que Hibernate 6 traduit
`@Enumerated(STRING)` en `enum(...)` natif MySQL et `boolean` en `bit` —
39 colonnes concernées, qu'aucune relecture n'aurait trouvées.
