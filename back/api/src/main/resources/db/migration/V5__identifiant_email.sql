-- =====================================================================
--  V5 — L'adresse e-mail devient l'identifiant de connexion
-- =====================================================================
--
--  POURQUOI CE CHANGEMENT
--
--  Jusqu'ici on se connectait avec son numero de telephone. Un eleveur
--  qui oubliait son mot de passe n'avait alors AUCUN moyen d'etre joint
--  par l'application : le SMS est reporte en V2, et l'e-mail etait
--  facultatif. La reinitialisation en libre-service etait donc
--  impossible — il fallait appeler l'administrateur.
--
--  L'e-mail devient l'identifiant. Il est obligatoire, unique, et c'est
--  par lui que part le code de reinitialisation.
--
--  CE QUE LE TELEPHONE DEVIENT
--
--  Il reste, mais comme MOYEN DE CONTACT, pas comme cle. L'annuaire
--  affiche le numero du veterinaire pour que l'eleveur l'appelle : c'est
--  le coeur de la mise en relation. Le supprimer casserait la
--  fonctionnalite principale de la plateforme.
--
--  Il devient donc facultatif et perd son unicite. Deux comptes peuvent
--  desormais porter le meme numero — le telephone familial partage, cas
--  courant en zone rurale, qui empechait jusqu'ici le deuxieme membre du
--  foyer de s'inscrire.
--
--  ORDRE DES OPERATIONS
--
--  On remplit avant de contraindre. Passer email en NOT NULL alors
--  qu'une ligne vaut NULL fait echouer la migration a mi-chemin, et
--  Flyway laisse alors la base dans un etat que personne ne sait decrire.
--
-- ---------------------------------------------------------------------
--  IDENTIFIANTS DE CONNEXION — pour les tests Swagger
-- ---------------------------------------------------------------------
--
--  ATTENTION : l'en-tete de V4 liste encore des connexions par NUMERO DE
--  TELEPHONE. Elle est perimee depuis ce fichier. Ce bloc la remplace.
--
--  V4 n'a volontairement pas ete corrige : Flyway refuse de demarrer si
--  une migration deja appliquee change, ne serait-ce que d'un
--  commentaire (`validate-on-migrate: true`). Modifier V4 obligerait
--  tout le monde a recreer sa base pour une correction de texte.
--
--    balla.kone@example.ml       eleveur2026   Balla Kone        (eleveur)
--    aissata.traore@example.ml   veto2026      Dr Aissata Traore (veterinaire)
--    admin@moncheptel.ml         admin2026     Administrateur    (administration)
--
--  Les mots de passe sont inchanges : cette migration ne touche pas aux
--  empreintes. Seul l'identifiant presente a l'ecran de connexion change.
--
--  COMME V4, CE FICHIER NE DOIT PAS PARTIR EN PRODUCTION tant qu'il
--  porte ces trois mots de passe en clair.
-- =====================================================================


-- ── 1. Aucune adresse ne doit manquer ────────────────────────────────
--
-- Les quatre comptes du jeu de demonstration (V4) ont deja leur adresse.
-- Cette mise a jour ne concerne donc que des comptes crees a la main ou
-- par l'ancienne inscription, qui n'exigeait pas d'e-mail.
--
-- L'adresse posee ici est VOLONTAIREMENT INUTILISABLE : le domaine
-- .invalid est reserve par la RFC 2606 et ne peut pas exister. Le compte
-- ne peut donc plus se connecter — ce qui est le comportement voulu,
-- parce que nous ne connaissons pas l'adresse de son titulaire. Il
-- faudra la lui demander et la corriger depuis l'administration.
--
-- L'alternative — inventer une adresse plausible — serait pire : elle
-- donnerait un identifiant de connexion a quelqu'un qui ne le connait
-- pas, et personne ne verrait le probleme.

UPDATE utilisateur
   SET email = CONCAT('compte-', id, '@adresse-a-corriger.invalid')
 WHERE email IS NULL OR email = '';

-- Ces comptes devront changer de mot de passe des que leur adresse sera
-- corrigee : on ne sait pas qui a eu acces a quoi entre-temps.
UPDATE utilisateur
   SET doit_changer_mot_de_passe = TRUE
 WHERE email LIKE '%@adresse-a-corriger.invalid';


-- ── 2. L'e-mail devient obligatoire ──────────────────────────────────
-- La contrainte d'unicite uk_utilisateur_email existe depuis V1 : rien a
-- recreer, seule la nullabilite change.

ALTER TABLE utilisateur
    MODIFY email VARCHAR(160) NOT NULL;


-- ── 3. Le telephone devient facultatif et non unique ─────────────────
-- DROP INDEX et non DROP CONSTRAINT : en MySQL une contrainte UNIQUE est
-- materialisee par un index, et c'est son nom qu'il faut donner.

ALTER TABLE utilisateur
    DROP INDEX uk_utilisateur_telephone;

ALTER TABLE utilisateur
    MODIFY telephone VARCHAR(20) NULL;

-- Un index simple le remplace : l'administration cherche un compte par
-- numero quand un eleveur appelle sans savoir dire son adresse.
CREATE INDEX ix_utilisateur_telephone ON utilisateur (telephone);


-- ── 4. Les codes de reinitialisation ─────────────────────────────────
--
--  CE QUI EST STOCKE, ET CE QUI NE L'EST PAS
--
--  La colonne porte l'EMPREINTE SHA-256 du code, jamais le code. Une
--  base lue par un tiers — sauvegarde egaree, injection SQL, acces
--  d'exploitation — ne doit pas permettre de prendre la main sur des
--  comptes. Le code en clair n'existe que dans le courriel envoye.
--
--  POURQUOI UNE TABLE ET PAS DEUX COLONNES SUR utilisateur
--
--  Parce qu'il faut pouvoir compter les demandes recentes pour limiter
--  le rythme, et garder la trace d'un code expire sans ecraser le
--  precedent. Deux colonnes ne gardent qu'un etat ; une table garde une
--  histoire.
--
--  tentatives : un code a six chiffres se devine en un million d'essais.
--  Sans plafond, une boucle automatisee y arrive en quelques minutes.
--  Cinq essais par code, et le code est grille.

CREATE TABLE code_reinitialisation (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    utilisateur_id    BIGINT      NOT NULL,
    empreinte_code    VARCHAR(64) NOT NULL,
    expire_le         DATETIME(6) NOT NULL,
    utilise           BIT         NOT NULL,
    tentatives        INT         NOT NULL,
    date_creation     DATETIME(6) NOT NULL,
    date_modification DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_code_reinit_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PAS d'unicite sur empreinte_code. Deux comptes peuvent tirer le meme
-- code a six chiffres sans que ce soit une anomalie — une chance sur un
-- million par paire, ce qui arrive. Une contrainte d'unicite ferait
-- echouer la demande du second, au hasard et sans explication.
-- La recherche se fait sur le COUPLE (empreinte, utilisateur).

CREATE INDEX ix_code_reinit_recherche
    ON code_reinitialisation (utilisateur_id, empreinte_code);

-- Sert au menage planifie, qui supprime les codes expires.
CREATE INDEX ix_code_reinit_expiration
    ON code_reinitialisation (expire_le);
