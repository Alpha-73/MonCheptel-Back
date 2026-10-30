-- =====================================================================
-- MonCheptel — schema initial
--
-- Ce fichier fait foi. `spring.jpa.hibernate.ddl-auto` est a `validate` :
-- Hibernate VERIFIE que le schema correspond aux entites, il ne le cree
-- jamais. Une colonne oubliee ici fait echouer le demarrage avec un message
-- precis — bien preferable a un `update` qui aurait ajoute la colonne en
-- silence, differemment sur chaque environnement.
--
-- Moteur InnoDB partout : c'est le seul de MySQL qui applique les cles
-- etrangeres et les transactions. MyISAM accepte les declarations de cles
-- etrangeres et les ignore.
--
-- utf8mb4 : le francais a des accents, et utf8mb3 ne code pas tout Unicode.
--
-- ---------------------------------------------------------------------
-- POURQUOI DES TYPES ENUM ET BIT, ET NON VARCHAR ET BOOLEAN
-- ---------------------------------------------------------------------
--
-- Hibernate 6 avec le dialecte MySQL traduit `@Enumerated(EnumType.STRING)`
-- par un type ENUM natif MySQL, et `boolean` par BIT. Ce fichier a d'abord
-- ete ecrit en VARCHAR et BOOLEAN : le demarrage echouait alors sur
--
--   Schema-validation: wrong column type in column [...]
--
-- Les declarations ci-dessous sont reprises MOT POUR MOT du schema que
-- Hibernate genere lui-meme depuis les entites (profil `ddl`,
-- schema-attendu-par-hibernate.sql). Les deux cotes ne peuvent donc plus
-- diverger.
--
-- Ce que cela coute, et qu'il faut savoir : ajouter une valeur a une
-- enumeration Java exige desormais une MIGRATION qui fasse l'ALTER TABLE
-- correspondant. Ce n'est pas un surcout reel — ajouter une filiere touche
-- de toute facon au code — mais l'oublier ferait echouer l'insertion avec
-- « Data truncated for column ». Flyway est exactement l'outil pour ca.
--
-- En echange, la base refuse une valeur inconnue au lieu de la stocker : un
-- statut mal orthographie par un script est rejete a l'ecriture, pas
-- decouvert six mois plus tard a la lecture.
-- =====================================================================
--
-- Regenerer le schema attendu par Hibernate, apres toute modification des
-- entites :
--
--     mvn spring-boot:run "-Dspring-boot.run.profiles=dev,ddl"

SET NAMES utf8mb4;

-- ── referentiel geographique ─────────────────────────────────────────

CREATE TABLE zone (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    nom               VARCHAR(80)  NOT NULL,
    region            VARCHAR(80),
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_zone_nom UNIQUE (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── utilisateurs : heritage JOINED ───────────────────────────────────
-- Une table par sous-classe, liee par la cle primaire. La colonne
-- `mode_suivi`... pardon, `role` ici, est le discriminant.
--
-- Pourquoi JOINED et pas SINGLE_TABLE : un eleveur n'a pas de zone
-- d'intervention, un veterinaire n'a pas de filieres pratiquees. En table
-- unique, toutes ces colonnes seraient nullables et plus rien ne pourrait
-- etre declare NOT NULL — la base cesserait de garantir quoi que ce soit.

CREATE TABLE utilisateur (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    role                      VARCHAR(16)  NOT NULL,
    nom                       VARCHAR(120) NOT NULL,
    telephone                 VARCHAR(20)  NOT NULL,
    email                     VARCHAR(160),
    mot_de_passe              VARCHAR(72)  NOT NULL,
    zone_id                   BIGINT,
    langue                    ENUM('BM','FR')   NOT NULL,
    actif                     BIT      NOT NULL,
    date_inscription          DATE         NOT NULL,
    derniere_connexion        DATETIME(6),
    tentatives_echouees       INT          NOT NULL,
    verrouille_jusqu_a        DATETIME(6),
    doit_changer_mot_de_passe BIT      NOT NULL,
    date_creation             DATETIME(6)  NOT NULL,
    date_modification         DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_utilisateur_telephone UNIQUE (telephone),
    CONSTRAINT uk_utilisateur_email     UNIQUE (email),
    CONSTRAINT fk_utilisateur_zone FOREIGN KEY (zone_id) REFERENCES zone (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 72 caracteres pour l'empreinte : une empreinte BCrypt en fait exactement
-- 60. La marge couvre un changement d'algorithme sans migration.

CREATE TABLE eleveur (
    utilisateur_id BIGINT NOT NULL,
    PRIMARY KEY (utilisateur_id),
    CONSTRAINT fk_eleveur_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE eleveur_filiere (
    eleveur_id BIGINT      NOT NULL,
    filiere    ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE') NOT NULL,
    PRIMARY KEY (eleveur_id, filiere),
    CONSTRAINT fk_eleveur_filiere FOREIGN KEY (eleveur_id)
        REFERENCES eleveur (utilisateur_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE veterinaire (
    utilisateur_id       BIGINT       NOT NULL,
    zone_intervention_id BIGINT,
    titre                VARCHAR(60),
    specialite           VARCHAR(160),
    disponible           BIT      NOT NULL,
    PRIMARY KEY (utilisateur_id),
    CONSTRAINT fk_veterinaire_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE,
    CONSTRAINT fk_veterinaire_zone FOREIGN KEY (zone_intervention_id)
        REFERENCES zone (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE administrateur (
    utilisateur_id BIGINT NOT NULL,
    PRIMARY KEY (utilisateur_id),
    CONSTRAINT fk_administrateur_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── sessions et appareils ────────────────────────────────────────────

CREATE TABLE session (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    utilisateur_id    BIGINT      NOT NULL,
    empreinte_jeton   VARCHAR(64) NOT NULL,
    expire_le         DATETIME(6) NOT NULL,
    revoquee          BIT     NOT NULL,
    appareil          VARCHAR(200),
    adresse_ip        VARCHAR(45),
    date_creation     DATETIME(6) NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_session_empreinte UNIQUE (empreinte_jeton),
    CONSTRAINT fk_session_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- adresse_ip en 45 : c'est la longueur d'une adresse IPv6 en notation
-- textuelle, forme mixte comprise. 15 aurait suffi pour IPv4 et casse
-- le jour ou l'operateur passe en IPv6.

CREATE TABLE jeton_appareil (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    utilisateur_id       BIGINT       NOT NULL,
    jeton                VARCHAR(255) NOT NULL,
    plateforme           VARCHAR(12),
    actif                BIT      NOT NULL,
    derniere_utilisation DATETIME(6)  NOT NULL,
    date_creation        DATETIME(6)  NOT NULL,
    date_modification    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_jeton_appareil UNIQUE (jeton),
    CONSTRAINT fk_jeton_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── referentiel technique ────────────────────────────────────────────

CREATE TABLE type_elevage (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    filiere              ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE')  NOT NULL,
    libelle              VARCHAR(80)  NOT NULL,
    duree_cycle_jours    INT,
    mode_evaluation      ENUM('PAR_ENTREE','PAR_PERIODE')  NOT NULL,
    capacite_reference   INT          NOT NULL,
    espace_par_sujet_m2  DECIMAL(6,3),
    temperature_min      INT,
    temperature_max      INT,
    aliment_par_sujet_kg DECIMAL(8,3),
    eau_par_sujet_jour_l DECIMAL(6,3),
    vaccins_obligatoires VARCHAR(500),
    taux_mortalite       DECIMAL(5,4) NOT NULL,
    difficulte           ENUM('ACCESSIBLE','EXIGEANT','MOYEN')  NOT NULL,
    actif                BIT      NOT NULL,
    date_creation        DATETIME(6)  NOT NULL,
    date_modification    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_type_elevage UNIQUE (filiere, libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- taux_mortalite en DECIMAL(5,4) : une FRACTION (0.0800 pour 8 %), pas un
-- pourcentage. L'ecran d'administration doit le dire, sans quoi une saisie
-- a « 8 » multiplierait par cent le nombre de sujets a acheter.

CREATE TABLE poste_cout (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    type_elevage_id   BIGINT        NOT NULL,
    libelle           VARCHAR(80)   NOT NULL,
    montant           BIGINT        NOT NULL,
    unite             VARCHAR(20),
    phase             ENUM('CYCLE','INSTALLATION')   NOT NULL,
    mode_quantite     ENUM('FORFAIT','PAR_CAPACITE','PAR_SUJET')   NOT NULL,
    coefficient       DECIMAL(10,4) NOT NULL,
    ordre             INT           NOT NULL,
    date_creation     DATETIME(6)   NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_poste_type_elevage FOREIGN KEY (type_elevage_id)
        REFERENCES type_elevage (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- montant en BIGINT : le franc CFA n'a pas de subdivision en circulation.
-- Un DECIMAL introduirait des centimes a arrondir, et un solde qui ne
-- tombe pas juste.

CREATE TABLE calendrier_sanitaire (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    type_elevage_id   BIGINT       NOT NULL,
    libelle           VARCHAR(120) NOT NULL,
    type_evenement    ENUM('CONSULTATION','MALADIE','TRAITEMENT','VACCINATION')  NOT NULL,
    jour_du_cycle     INT          NOT NULL,
    obligatoire       BIT      NOT NULL,
    ordre             INT          NOT NULL,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_calendrier_type_elevage FOREIGN KEY (type_elevage_id)
        REFERENCES type_elevage (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── cheptel : heritage JOINED a nouveau ──────────────────────────────

CREATE TABLE unite_elevage (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    mode_suivi        VARCHAR(12)  NOT NULL,
    code              VARCHAR(20)  NOT NULL,
    proprietaire_id   BIGINT       NOT NULL,
    type_elevage_id   BIGINT       NOT NULL,
    statut            ENUM('ACTIF','CLOTURE','MORT','TRANSFERE','VENDU')  NOT NULL,
    date_entree       DATE         NOT NULL,
    date_sortie       DATE,
    note              VARCHAR(500),
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_unite_proprietaire_code UNIQUE (proprietaire_id, code),
    CONSTRAINT fk_unite_proprietaire FOREIGN KEY (proprietaire_id)
        REFERENCES eleveur (utilisateur_id),
    CONSTRAINT fk_unite_type_elevage FOREIGN KEY (type_elevage_id)
        REFERENCES type_elevage (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Le code est unique CHEZ SON PROPRIETAIRE, pas sur la plateforme. Une
-- unicite globale ferait refuser « LOT-001 » au deuxieme eleveur qui ouvre
-- un compte, pour un conflit avec un lot qu'il ne voit pas.
--
-- Pas de ON DELETE CASCADE sur le proprietaire : supprimer un eleveur
-- emporterait son cheptel, son journal et sa comptabilite. Un compte se
-- desactive (`utilisateur.actif`), il ne se supprime pas.

CREATE TABLE lot (
    unite_id            BIGINT NOT NULL,
    effectif_initial    INT    NOT NULL,
    effectif_restant    INT    NOT NULL,
    date_cloture_prevue DATE,
    PRIMARY KEY (unite_id),
    CONSTRAINT fk_lot_unite FOREIGN KEY (unite_id)
        REFERENCES unite_elevage (id) ON DELETE CASCADE,
    CONSTRAINT ck_lot_effectif CHECK (effectif_restant >= 0
                                  AND effectif_restant <= effectif_initial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ck_lot_effectif : le dernier rempart. La regle est dans
-- UniteElevage.retirer et le verrou pessimiste la protege des acces
-- concurrents ; cette contrainte couvre ce que le code ne voit pas — une
-- correction faite a la main en SQL, un script d'import.

CREATE TABLE animal (
    unite_id       BIGINT NOT NULL,
    race           VARCHAR(60),
    sexe           ENUM('FEMELLE','MALE'),
    date_naissance DATE,
    present        INT    NOT NULL,
    PRIMARY KEY (unite_id),
    CONSTRAINT fk_animal_unite FOREIGN KEY (unite_id)
        REFERENCES unite_elevage (id) ON DELETE CASCADE,
    CONSTRAINT ck_animal_present CHECK (present IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mouvement_cheptel (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    unite_id          BIGINT       NOT NULL,
    type              ENUM('ACQUISITION','MISE_EN_PLACE','MORT','NAISSANCE','TRANSFERT','VENTE')  NOT NULL,
    date_mouvement    DATE         NOT NULL,
    nombre_tetes      INT          NOT NULL,
    observation       VARCHAR(500),
    montant           BIGINT,
    auteur_id         BIGINT       NOT NULL,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_mouvement_unite  FOREIGN KEY (unite_id) REFERENCES unite_elevage (id),
    CONSTRAINT fk_mouvement_auteur FOREIGN KEY (auteur_id) REFERENCES utilisateur (id),
    CONSTRAINT ck_mouvement_tetes  CHECK (nombre_tetes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Le journal est un registre : aucune ligne ne se modifie ni ne se
-- supprime. Pas de cascade sur l'unite — la trace d'une vente survit a
-- tout le reste, c'est ce qui permet de recalculer un effectif.

-- ── sante ────────────────────────────────────────────────────────────

CREATE TABLE evenement_sanitaire (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    unite_id            BIGINT       NOT NULL,
    type                ENUM('CONSULTATION','MALADIE','TRAITEMENT','VACCINATION')  NOT NULL,
    date_evenement      DATE         NOT NULL,
    produit_observation VARCHAR(500),
    auteur_id           BIGINT       NOT NULL,
    veterinaire_id      BIGINT,
    date_creation       DATETIME(6)  NOT NULL,
    date_modification   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_evenement_unite  FOREIGN KEY (unite_id) REFERENCES unite_elevage (id),
    CONSTRAINT fk_evenement_auteur FOREIGN KEY (auteur_id) REFERENCES utilisateur (id),
    CONSTRAINT fk_evenement_veto   FOREIGN KEY (veterinaire_id)
        REFERENCES veterinaire (utilisateur_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rappel_sanitaire (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    unite_id          BIGINT       NOT NULL,
    calendrier_id     BIGINT,
    libelle           VARCHAR(120) NOT NULL,
    date_echeance     DATE         NOT NULL,
    statut            ENUM('A_VENIR','ECHU','FAIT','IGNORE')  NOT NULL,
    evenement_id      BIGINT,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_rappel_unite      FOREIGN KEY (unite_id)
        REFERENCES unite_elevage (id) ON DELETE CASCADE,
    CONSTRAINT fk_rappel_calendrier FOREIGN KEY (calendrier_id)
        REFERENCES calendrier_sanitaire (id) ON DELETE SET NULL,
    CONSTRAINT fk_rappel_evenement  FOREIGN KEY (evenement_id)
        REFERENCES evenement_sanitaire (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ON DELETE SET NULL sur le calendrier : un rappel deja engendre reste
-- valable meme si l'administration retire la ligne de protocole qui lui a
-- donne naissance. Le vaccin a faire jeudi ne disparait pas parce que le
-- referentiel a change mardi.

CREATE TABLE symptome (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    libelle           VARCHAR(80) NOT NULL,
    ordre             INT         NOT NULL,
    date_creation     DATETIME(6) NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_symptome_libelle UNIQUE (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE maladie (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    nom                      VARCHAR(120)  NOT NULL,
    gravite                  ENUM('ELEVE','FAIBLE','MOYEN')    NOT NULL,
    symptomes_texte          VARCHAR(1000),
    traitement               VARCHAR(2000),
    oriente_vers_veterinaire BIT       NOT NULL,
    active                   BIT       NOT NULL,
    date_creation            DATETIME(6)   NOT NULL,
    date_modification        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_maladie_nom UNIQUE (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE maladie_filiere (
    maladie_id BIGINT      NOT NULL,
    filiere    ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE') NOT NULL,
    PRIMARY KEY (maladie_id, filiere),
    CONSTRAINT fk_maladie_filiere FOREIGN KEY (maladie_id)
        REFERENCES maladie (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE maladie_symptome (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    maladie_id        BIGINT       NOT NULL,
    symptome_id       BIGINT       NOT NULL,
    poids             DECIMAL(3,2) NOT NULL,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_maladie_symptome UNIQUE (maladie_id, symptome_id),
    CONSTRAINT fk_ms_maladie  FOREIGN KEY (maladie_id)
        REFERENCES maladie (id) ON DELETE CASCADE,
    CONSTRAINT fk_ms_symptome FOREIGN KEY (symptome_id)
        REFERENCES symptome (id) ON DELETE CASCADE,
    CONSTRAINT ck_ms_poids CHECK (poids >= 0 AND poids <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- uk_maladie_symptome : un symptome pondere deux fois sur la meme maladie
-- doublerait son poids dans le calcul, sans que rien ne l'indique a
-- l'ecran. Le moteur rendrait un classement faux en silence.
--
-- ck_ms_poids : le poids est une force d'association, bornee a [0,1]. Sans
-- borne haute, une seule ligne mal saisie ferait gagner sa maladie a tous
-- les coups.

CREATE TABLE competence (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    veterinaire_id    BIGINT      NOT NULL,
    maladie_id        BIGINT      NOT NULL,
    date_declaration  DATE        NOT NULL,
    date_creation     DATETIME(6) NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_competence_veto_maladie UNIQUE (veterinaire_id, maladie_id),
    CONSTRAINT fk_competence_veto    FOREIGN KEY (veterinaire_id)
        REFERENCES veterinaire (utilisateur_id) ON DELETE CASCADE,
    CONSTRAINT fk_competence_maladie FOREIGN KEY (maladie_id)
        REFERENCES maladie (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE signalement_maladie (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    eleveur_id              BIGINT        NOT NULL,
    unite_id                BIGINT        NOT NULL,
    tranche_effectif        ENUM('DE_10_A_50','MOINS_DE_10','PLUS_DE_50')   NOT NULL,
    `precision`             VARCHAR(1000),
    date_signalement        DATETIME(6)   NOT NULL,
    maladie_probable_id     BIGINT,
    probabilite             DECIMAL(4,3),
    maladie_alternative_id  BIGINT,
    probabilite_alternative DECIMAL(4,3),
    gravite                 ENUM('ELEVE','FAIBLE','MOYEN'),
    statut                  ENUM('EN_COURS','NOUVEAU','ORIENTE','TRAITE')   NOT NULL,
    veterinaire_id          BIGINT,
    date_orientation        DATETIME(6),
    date_traitement         DATETIME(6),
    compte_rendu            VARCHAR(2000),
    date_creation           DATETIME(6)   NOT NULL,
    date_modification       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_signalement_eleveur FOREIGN KEY (eleveur_id)
        REFERENCES eleveur (utilisateur_id),
    CONSTRAINT fk_signalement_unite   FOREIGN KEY (unite_id)
        REFERENCES unite_elevage (id),
    CONSTRAINT fk_signalement_probable    FOREIGN KEY (maladie_probable_id)
        REFERENCES maladie (id),
    CONSTRAINT fk_signalement_alternative FOREIGN KEY (maladie_alternative_id)
        REFERENCES maladie (id),
    CONSTRAINT fk_signalement_veto FOREIGN KEY (veterinaire_id)
        REFERENCES veterinaire (utilisateur_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- `precision` entre accents graves : c'est un MOT RESERVE de MySQL (il fait
-- partie de la syntaxe des types numeriques). Sans les accents, la creation
-- de la table echoue avec une erreur de syntaxe qui pointe la ligne
-- suivante.
--
-- probabilite en DECIMAL(4,3) : une FRACTION de 0 a 1, jamais un
-- pourcentage. 65,5 y deborderait — DECIMAL(4,3) plafonne a 9,999. Ce
-- chiffre ne sort pas de l'API : il sert a classer les candidates.

CREATE TABLE signalement_symptome (
    signalement_id BIGINT NOT NULL,
    symptome_id    BIGINT NOT NULL,
    PRIMARY KEY (signalement_id, symptome_id),
    CONSTRAINT fk_sig_symptome_signalement FOREIGN KEY (signalement_id)
        REFERENCES signalement_maladie (id) ON DELETE CASCADE,
    CONSTRAINT fk_sig_symptome_symptome FOREIGN KEY (symptome_id)
        REFERENCES symptome (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── finances ─────────────────────────────────────────────────────────

CREATE TABLE operation_financiere (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    eleveur_id        BIGINT       NOT NULL,
    type              ENUM('DEPENSE','REVENU')   NOT NULL,
    categorie         ENUM('ALIMENT','EQUIPEMENT','MAIN_D_OEUVRE','SOINS_VACCINATION','TRANSPORT','VENTE_ANIMAUX','VENTE_PRODUITS')  NOT NULL,
    filiere           ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE')  NOT NULL,
    unite_id          BIGINT,
    montant           BIGINT       NOT NULL,
    date_operation    DATE         NOT NULL,
    note              VARCHAR(500),
    mouvement_id      BIGINT,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_operation_mouvement UNIQUE (mouvement_id),
    CONSTRAINT fk_operation_eleveur   FOREIGN KEY (eleveur_id)
        REFERENCES eleveur (utilisateur_id),
    CONSTRAINT fk_operation_unite     FOREIGN KEY (unite_id)
        REFERENCES unite_elevage (id) ON DELETE SET NULL,
    CONSTRAINT fk_operation_mouvement FOREIGN KEY (mouvement_id)
        REFERENCES mouvement_cheptel (id),
    CONSTRAINT ck_operation_montant CHECK (montant > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ck_operation_montant : le montant est TOUJOURS positif. Le sens vient du
-- type, et de lui seul. Accepter un montant negatif ouvrirait deux facons
-- d'ecrire la meme depense, et le solde du mois deviendrait indefendable.
--
-- uk_operation_mouvement : une vente ne peut etre comptabilisee qu'une
-- fois. Sans cette contrainte, un double appel a l'API — un reseau
-- capricieux, un bouton presse deux fois — creerait deux recettes pour une
-- seule vente.
--
-- ON DELETE SET NULL sur l'unite : une operation rattachee a un lot
-- supprime redevient une operation generale. Elle compte toujours dans le
-- solde du mois — l'argent a bien ete depense.

-- ── communication ────────────────────────────────────────────────────

CREATE TABLE conseil (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    titre             VARCHAR(160) NOT NULL,
    contenu           LONGTEXT     NOT NULL,
    filiere           ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE'),
    categorie         ENUM('HYGIENE','PRODUCTION','SANTE')  NOT NULL,
    auteur_id         BIGINT       NOT NULL,
    statut            ENUM('BROUILLON','EN_MODERATION','PUBLIE','RETIRE')  NOT NULL,
    date_soumission   DATETIME(6),
    date_publication  DATETIME(6),
    moderateur_id     BIGINT,
    motif_retrait     VARCHAR(500),
    nombre_lectures   INT          NOT NULL,
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_conseil_auteur     FOREIGN KEY (auteur_id)
        REFERENCES veterinaire (utilisateur_id),
    CONSTRAINT fk_conseil_moderateur FOREIGN KEY (moderateur_id)
        REFERENCES administrateur (utilisateur_id),
    CONSTRAINT ck_conseil_retrait CHECK (
        statut <> 'RETIRE' OR motif_retrait IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ck_conseil_retrait : un retrait sans motif ne dit pas au veterinaire quoi
-- corriger. La regle est dans Conseil.retirer ; elle est aussi ici, parce
-- qu'une correction faite a la main en SQL ne passe pas par le code.

CREATE TABLE alerte (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    maladie_id         BIGINT       NOT NULL,
    zone_id            BIGINT       NOT NULL,
    filiere            ENUM('BOVINS','CAPRINS','OVINS','VOLAILLE'),
    message            VARCHAR(500) NOT NULL,
    portee             INT          NOT NULL,
    date_declenchement DATETIME(6)  NOT NULL,
    date_cloture       DATETIME(6),
    statut             ENUM('ACTIVE','TERMINEE')  NOT NULL,
    administrateur_id  BIGINT       NOT NULL,
    date_creation      DATETIME(6)  NOT NULL,
    date_modification  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_alerte_maladie FOREIGN KEY (maladie_id) REFERENCES maladie (id),
    CONSTRAINT fk_alerte_zone    FOREIGN KEY (zone_id)    REFERENCES zone (id),
    CONSTRAINT fk_alerte_admin   FOREIGN KEY (administrateur_id)
        REFERENCES administrateur (utilisateur_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- `portee` est figee au declenchement, pas recalculee a l'affichage : un
-- eleveur inscrit apres coup changerait la portee d'une alerte passee,
-- alors qu'il n'a rien recu.

CREATE TABLE notification (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    destinataire_id   BIGINT       NOT NULL,
    type              ENUM('ALERTE','MODERATION','ORIENTATION','RAPPEL','SYSTEME')  NOT NULL,
    titre             VARCHAR(160) NOT NULL,
    corps             VARCHAR(500),
    lue               BIT      NOT NULL,
    reference_type    VARCHAR(20),
    reference_id      BIGINT,
    statut_envoi      ENUM('ABANDONNE','ECHEC','ENVOYE','EN_ATTENTE')  NOT NULL,
    date_envoi        DATETIME(6),
    tentatives_envoi  INT          NOT NULL,
    erreur_envoi      VARCHAR(300),
    date_creation     DATETIME(6)  NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_destinataire FOREIGN KEY (destinataire_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- reference_type / reference_id ne sont PAS une cle etrangere : ils
-- designent tantot un signalement, tantot un lot, tantot une alerte. Une
-- cle etrangere polymorphe n'existe pas en SQL. C'est le seul endroit du
-- schema ou l'integrite n'est pas garantie par la base, et c'est assume :
-- une notification qui pointe vers un objet supprime ouvre l'accueil au
-- lieu de l'ecran attendu. Le cout d'une table de liaison par type — cinq
-- tables, cinq jointures — serait sans commune mesure.
