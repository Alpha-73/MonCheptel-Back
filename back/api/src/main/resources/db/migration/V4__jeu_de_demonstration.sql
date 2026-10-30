-- =====================================================================
-- Jeu de demonstration
--
-- Reproduit exactement les chiffres que la maquette affiche :
--   3 lots actifs · 320 tetes restantes · 1 lot vendu · 150 tetes vendues
--   6 autres animaux · solde du mois + 74 000 FCFA
--
-- ---------------------------------------------------------------------
-- LES DATES SONT RELATIVES A CURDATE(), PAS FIGEES
-- ---------------------------------------------------------------------
--
-- La maquette a ete dessinee avec un « aujourd'hui » au 19 septembre 2026 :
-- LOT-014 y est a « J+16 » d'une mise en place le 3 septembre. Des dates
-- figees auraient fait vieillir la demonstration — le 13 octobre, LOT-014
-- serait a J+40, son cycle serait fini et le rappel de vaccination
-- largement depasse.
--
-- Les dates sont donc calculees depuis le jour ou cette migration
-- s'execute. RELANCER LA BASE A BLANC LA VEILLE D'UNE DEMONSTRATION remet
-- le decor a l'endroit :
--
--   DROP DATABASE moncheptel_dev; CREATE DATABASE moncheptel_dev
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- ---------------------------------------------------------------------
-- MOTS DE PASSE — pour les tests Swagger
-- ---------------------------------------------------------------------
--
--   70000000  eleveur2026   Balla Kone       (eleveur)
--   76000000  veto2026      Dr Aissata Traore (veterinaire)
--   65000000  admin2026     Administrateur   (administration)
--
-- Empreintes BCrypt en cout 12, comme ConfigurationSecurite l'exige.
-- CE FICHIER NE DOIT PAS PARTIR EN PRODUCTION : ces trois mots de passe
-- sont ecrits ci-dessus, donc publics.
-- =====================================================================

SET NAMES utf8mb4;

-- ── comptes ──────────────────────────────────────────────────────────

INSERT INTO utilisateur (role, nom, telephone, email, mot_de_passe, zone_id,
        langue, actif, date_inscription, tentatives_echouees,
        doit_changer_mot_de_passe, date_creation)
SELECT 'ELEVEUR', 'Balla Kone', '70000000', 'balla.kone@example.ml',
       '$2b$12$q5m.WbDcyknyxPYCUiv.WOSwmPg4W5WFnvUTvrK4SgfUlSRhmH1Yy',
       z.id, 'FR', TRUE, DATE_SUB(CURDATE(), INTERVAL 120 DAY), 0, FALSE, NOW(6)
FROM zone z WHERE z.nom = 'Koutiala';
INSERT INTO eleveur (utilisateur_id)
SELECT id FROM utilisateur WHERE telephone = '70000000';
INSERT INTO eleveur_filiere (eleveur_id, filiere)
SELECT u.id, f.filiere FROM utilisateur u
CROSS JOIN (SELECT 'VOLAILLE' AS filiere UNION ALL SELECT 'BOVINS'
            UNION ALL SELECT 'OVINS' UNION ALL SELECT 'CAPRINS') f
WHERE u.telephone = '70000000';

INSERT INTO utilisateur (role, nom, telephone, email, mot_de_passe, zone_id,
        langue, actif, date_inscription, tentatives_echouees,
        doit_changer_mot_de_passe, date_creation)
SELECT 'VETERINAIRE', 'Aissata Traore', '76000000', 'aissata.traore@example.ml',
       '$2b$12$sCeruE8CJylsvDAnLoCfs.3uZ3jchoKqKpHLIU3AWnyKnA4t90cvC',
       z.id, 'FR', TRUE, DATE_SUB(CURDATE(), INTERVAL 200 DAY), 0, FALSE, NOW(6)
FROM zone z WHERE z.nom = 'Koutiala';
INSERT INTO veterinaire (utilisateur_id, zone_intervention_id, titre, specialite, disponible)
SELECT u.id, z.id, 'Dr', 'Aviculture', TRUE
FROM utilisateur u JOIN zone z ON z.nom = 'Koutiala'
WHERE u.telephone = '76000000';

-- doit_changer_mot_de_passe est a FALSE pour ce compte de demonstration :
-- un veterinaire cree par l'administration l'aurait a TRUE et serait
-- redirige vers l'ecran de changement, ce qui bloquerait les tests Swagger.

INSERT INTO utilisateur (role, nom, telephone, email, mot_de_passe, zone_id,
        langue, actif, date_inscription, tentatives_echouees,
        doit_changer_mot_de_passe, date_creation)
SELECT 'ADMINISTRATEUR', 'Administrateur', '65000000', 'admin@moncheptel.ml',
       '$2b$12$WASn7/7MrBFgtttopp5Sj.e9cquD3mmDTj1pwztopyFLN51tq1GoC',
       z.id, 'FR', TRUE, DATE_SUB(CURDATE(), INTERVAL 300 DAY), 0, FALSE, NOW(6)
FROM zone z WHERE z.nom = 'Bamako';
INSERT INTO administrateur (utilisateur_id)
SELECT id FROM utilisateur WHERE telephone = '65000000';

-- Un second veterinaire : sans lui, l'annuaire n'a qu'une ligne et
-- l'orientation d'un signalement n'a pas de second candidat a departager.
INSERT INTO utilisateur (role, nom, telephone, email, mot_de_passe, zone_id,
        langue, actif, date_inscription, tentatives_echouees,
        doit_changer_mot_de_passe, date_creation)
SELECT 'VETERINAIRE', 'Moussa Diarra', '76000001', 'moussa.diarra@example.ml',
       '$2b$12$sCeruE8CJylsvDAnLoCfs.3uZ3jchoKqKpHLIU3AWnyKnA4t90cvC',
       z.id, 'FR', TRUE, DATE_SUB(CURDATE(), INTERVAL 180 DAY), 0, FALSE, NOW(6)
FROM zone z WHERE z.nom = 'Koutiala';
INSERT INTO veterinaire (utilisateur_id, zone_intervention_id, titre, specialite, disponible)
SELECT u.id, z.id, 'Dr', 'Ruminants', TRUE
FROM utilisateur u JOIN zone z ON z.nom = 'Koutiala'
WHERE u.telephone = '76000001';

-- Competences : ce sont elles qui rendent l'orientation possible.
INSERT INTO competence (veterinaire_id, maladie_id, date_declaration, date_creation)
SELECT v.utilisateur_id, m.id, DATE_SUB(CURDATE(), INTERVAL 150 DAY), NOW(6)
FROM veterinaire v JOIN utilisateur u ON u.id = v.utilisateur_id
JOIN maladie m ON m.nom IN ('Maladie de Newcastle', 'Coccidiose', 'Bronchite infectieuse')
WHERE u.telephone = '76000000';
INSERT INTO competence (veterinaire_id, maladie_id, date_declaration, date_creation)
SELECT v.utilisateur_id, m.id, DATE_SUB(CURDATE(), INTERVAL 150 DAY), NOW(6)
FROM veterinaire v JOIN utilisateur u ON u.id = v.utilisateur_id
JOIN maladie m ON m.nom IN ('Fievre aphteuse', 'Peste des petits ruminants',
                            'Diarrhee neonatale', 'Maladie de Newcastle')
WHERE u.telephone = '76000001';

-- ── cheptel ──────────────────────────────────────────────────────────
-- Les quatre lots de l'ecran « MesAnimaux - Toutes », a leurs ecarts de
-- dates d'origine : LOT-014 a J+16, LOT-009 a J+38, LOT-006 a J+77.

INSERT INTO unite_elevage (mode_suivi, code, proprietaire_id, type_elevage_id,
        statut, date_entree, date_sortie, date_creation)
SELECT 'LOT', v.code, e.utilisateur_id, t.id, v.statut,
       DATE_SUB(CURDATE(), INTERVAL v.jours DAY),
       -- La sortie de LOT-003 est calee 4 jours AVANT le 1er du mois en
       -- cours, pas « il y a 22 jours » : lance un 28, « il y a 22 jours »
       -- tombe dans le mois courant, et sa recette de 126 000 FCFA
       -- s'ajoutait au solde du mois — qui affichait 200 000 au lieu des
       -- 74 000 de la maquette.
       CASE WHEN v.statut = 'CLOTURE'
            THEN DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 4 DAY)
            ELSE NULL END,
       NOW(6)
FROM (
  SELECT 'LOT-014' AS code, 16 AS jours, 'ACTIF'   AS statut
  UNION ALL SELECT 'LOT-009', 38, 'ACTIF'
  UNION ALL SELECT 'LOT-006', 77, 'ACTIF'
  UNION ALL SELECT 'LOT-003', 98, 'CLOTURE'
) AS v
JOIN eleveur e ON e.utilisateur_id = (SELECT id FROM utilisateur WHERE telephone='70000000')
JOIN type_elevage t ON t.libelle = 'Poulet de chair';

INSERT INTO lot (unite_id, effectif_initial, effectif_restant, date_cloture_prevue)
SELECT u.id, v.initial, v.restant,
       DATE_ADD(u.date_entree, INTERVAL t.duree_cycle_jours DAY)
FROM unite_elevage u
JOIN type_elevage t ON t.id = u.type_elevage_id
JOIN (
  SELECT 'LOT-014' AS code, 180 AS initial, 180 AS restant
  UNION ALL SELECT 'LOT-009',  60,  60
  UNION ALL SELECT 'LOT-006',  80,  80
  UNION ALL SELECT 'LOT-003', 150,   0
) AS v ON v.code = u.code;

-- 180 + 60 + 80 = 320 tetes restantes, 3 lots actifs, 150 tetes sorties.
-- Ce sont les quatre nombres du tableau de bord.

-- Six animaux, comme « Autres animaux : 6 ». BOV-009 est vendu : c'est la
-- vente que l'ecran des depenses affiche a + 95 000.
INSERT INTO unite_elevage (mode_suivi, code, proprietaire_id, type_elevage_id,
        statut, date_entree, date_sortie, date_creation)
SELECT 'INDIVIDUEL', v.code, e.utilisateur_id, t.id, v.statut,
       DATE_SUB(CURDATE(), INTERVAL v.jours DAY),
       CASE WHEN v.jours_sortie IS NULL THEN NULL
            ELSE DATE_SUB(CURDATE(), INTERVAL v.jours_sortie DAY) END,
       NOW(6)
FROM (
  SELECT 'BOV-047' AS code, 'Veau d''embouche'   AS type, 60  AS jours, 'ACTIF' AS statut, NULL AS jours_sortie
  UNION ALL SELECT 'BOV-051', 'Veau d''embouche',   40,  'ACTIF', NULL
  UNION ALL SELECT 'OVI-022', 'Belier d''embouche', 75,  'ACTIF', NULL
  UNION ALL SELECT 'OVI-023', 'Belier d''embouche', 75,  'ACTIF', NULL
  UNION ALL SELECT 'CAP-011', 'Chevre sahelienne',  150, 'ACTIF', NULL
  UNION ALL SELECT 'CAP-012', 'Chevre sahelienne',  150, 'ACTIF', NULL
  UNION ALL SELECT 'BOV-009', 'Veau d''embouche',   210, 'VENDU', 12
) AS v
JOIN eleveur e ON e.utilisateur_id = (SELECT id FROM utilisateur WHERE telephone='70000000')
JOIN type_elevage t ON t.libelle = v.type;

INSERT INTO animal (unite_id, race, sexe, date_naissance, present)
SELECT u.id,
       CASE LEFT(u.code,3) WHEN 'BOV' THEN 'Zebu peul'
                           WHEN 'OVI' THEN 'Mouton Djallonke'
                           ELSE 'Chevre du Sahel' END,
       CASE WHEN u.code IN ('CAP-011','CAP-012') THEN 'FEMELLE' ELSE 'MALE' END,
       NULL,
       CASE WHEN u.statut = 'ACTIF' THEN 1 ELSE 0 END
FROM unite_elevage u
WHERE u.mode_suivi = 'INDIVIDUEL';

-- ── journal des mouvements ───────────────────────────────────────────
-- Chaque unite a son entree. Sans elles, le controle de coherence
-- LotRepository.chercherIncoherences remonterait les quatre lots a tort :
-- il compare l'effectif stocke a la somme du journal.

INSERT INTO mouvement_cheptel (unite_id, type, date_mouvement, nombre_tetes,
        observation, montant, auteur_id, date_creation)
SELECT u.id,
       CASE u.mode_suivi WHEN 'LOT' THEN 'MISE_EN_PLACE' ELSE 'ACQUISITION' END,
       u.date_entree,
       CASE u.mode_suivi WHEN 'LOT' THEN l.effectif_initial ELSE 1 END,
       'Entree au cheptel', NULL,
       (SELECT id FROM utilisateur WHERE telephone='70000000'), NOW(6)
FROM unite_elevage u LEFT JOIN lot l ON l.unite_id = u.id;

-- Les deux sorties : LOT-003 vendu en une fois, BOV-009 vendu.
INSERT INTO mouvement_cheptel (unite_id, type, date_mouvement, nombre_tetes,
        observation, montant, auteur_id, date_creation)
SELECT u.id, 'VENTE', u.date_sortie, 150, 'Vente au marche de Koutiala', 126000,
       (SELECT id FROM utilisateur WHERE telephone='70000000'), NOW(6)
FROM unite_elevage u WHERE u.code = 'LOT-003';

INSERT INTO mouvement_cheptel (unite_id, type, date_mouvement, nombre_tetes,
        observation, montant, auteur_id, date_creation)
SELECT u.id, 'VENTE', u.date_sortie, 1, 'Vente du veau', 95000,
       (SELECT id FROM utilisateur WHERE telephone='70000000'), NOW(6)
FROM unite_elevage u WHERE u.code = 'BOV-009';

-- ── operations financieres ───────────────────────────────────────────
--
-- Les cinq lignes de l'ecran « Depenses & revenus », dans son ordre :
--   Aliment volaille        - 8 500
--   Vente bovin BOV-009     + 95 000
--   Vaccination             - 4 000
--   Vente oeufs             + 6 500
--   Main-d'oeuvre           - 15 000
--                          ──────────
--   Solde du mois           + 74 000
--
-- Les dates sont BORNEES AU MOIS EN COURS par le GREATEST ci-dessous.
-- Sans lui, une migration lancee le 3 du mois placerait « il y a 14 jours »
-- dans le mois precedent, et le tableau de bord afficherait un solde du
-- mois qui ne serait plus 74 000 — le chiffre que la maquette annonce.

INSERT INTO operation_financiere (eleveur_id, type, categorie, filiere, unite_id,
        montant, date_operation, note, mouvement_id, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='70000000'),
       v.type, v.categorie, v.filiere, NULL, v.montant,
       GREATEST(DATE_SUB(CURDATE(), INTERVAL v.jours DAY),
                DATE_FORMAT(CURDATE(), '%Y-%m-01')),
       v.note, NULL, NOW(6)
FROM (
  SELECT 'DEPENSE' AS type, 'ALIMENT'           AS categorie, 'VOLAILLE' AS filiere,
          8500 AS montant, 14 AS jours, 'Aliment volaille' AS note
  UNION ALL SELECT 'DEPENSE', 'SOINS_VACCINATION', 'VOLAILLE',  4000, 10, 'Vaccination'
  UNION ALL SELECT 'REVENU',  'VENTE_PRODUITS',    'VOLAILLE',  6500,  8, 'Vente oeufs'
  UNION ALL SELECT 'DEPENSE', 'MAIN_D_OEUVRE',     'VOLAILLE', 15000,  5, 'Main-d''oeuvre'
) AS v;

-- La vente du veau est rattachee a SON mouvement, et prend SA date : c'est
-- le lien uk_operation_mouvement qui empeche de la compter deux fois, et
-- reprendre la date du mouvement evite l'ecart qu'avait la maquette — une
-- vente du 14 juin dont la recette etait ecrite au 12 septembre.
INSERT INTO operation_financiere (eleveur_id, type, categorie, filiere, unite_id,
        montant, date_operation, note, mouvement_id, date_creation)
SELECT u.proprietaire_id, 'REVENU', 'VENTE_ANIMAUX', 'BOVINS', u.id,
       m.montant, m.date_mouvement, 'Vente bovin BOV-009', m.id, NOW(6)
FROM mouvement_cheptel m
JOIN unite_elevage u ON u.id = m.unite_id
WHERE u.code = 'BOV-009' AND m.type = 'VENTE';

-- La vente de LOT-003 est datee du mois DERNIER (voir le calcul de sa
-- date de sortie plus haut) : sa recette de 126 000 FCFA n'entre donc pas
-- dans le solde du mois. C'est voulu, et c'est ce qui prouve que le filtre
-- par periode fonctionne — sans elle, le solde serait juste par hasard.
INSERT INTO operation_financiere (eleveur_id, type, categorie, filiere, unite_id,
        montant, date_operation, note, mouvement_id, date_creation)
SELECT u.proprietaire_id, 'REVENU', 'VENTE_ANIMAUX', 'VOLAILLE', u.id,
       m.montant, m.date_mouvement, 'Vente LOT-003 — 150 tetes', m.id, NOW(6)
FROM mouvement_cheptel m
JOIN unite_elevage u ON u.id = m.unite_id
WHERE u.code = 'LOT-003' AND m.type = 'VENTE';

-- Quelques depenses des mois precedents, pour que l'ecran « Rapports »
-- ait une courbe a tracer plutot qu'un seul point.
INSERT INTO operation_financiere (eleveur_id, type, categorie, filiere, unite_id,
        montant, date_operation, note, mouvement_id, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='70000000'),
       v.type, v.categorie, 'VOLAILLE', NULL, v.montant,
       DATE_SUB(CURDATE(), INTERVAL v.jours DAY), v.note, NULL, NOW(6)
FROM (
  SELECT 'DEPENSE' AS type, 'ALIMENT' AS categorie, 43500 AS montant, 35 AS jours,
         'Aliment volaille' AS note
  UNION ALL SELECT 'DEPENSE', 'SOINS_VACCINATION', 17000, 40, 'Vaccins et soins'
  UNION ALL SELECT 'DEPENSE', 'MAIN_D_OEUVRE',     35000, 45, 'Main-d''oeuvre'
  UNION ALL SELECT 'DEPENSE', 'EQUIPEMENT',        12000, 50, 'Abreuvoirs'
  UNION ALL SELECT 'DEPENSE', 'ALIMENT',           38000, 68, 'Aliment volaille'
  UNION ALL SELECT 'DEPENSE', 'TRANSPORT',          6000, 72, 'Transport poussins'
) AS v;

-- ── rappels sanitaires ───────────────────────────────────────────────
-- LOT-014 est a J+16 : sa vaccination Newcastle J21 tombe dans 5 jours.
-- Les rappels J7 des trois lots actifs sont soldes ou echus selon leur age,
-- exactement comme la tache planifiee les aurait laisses.

INSERT INTO rappel_sanitaire (unite_id, calendrier_id, libelle, date_echeance,
        statut, date_creation)
SELECT u.id, c.id, c.libelle,
       DATE_ADD(u.date_entree, INTERVAL c.jour_du_cycle DAY),
       CASE WHEN DATE_ADD(u.date_entree, INTERVAL c.jour_du_cycle DAY) < CURDATE()
            THEN 'FAIT' ELSE 'A_VENIR' END,
       NOW(6)
FROM unite_elevage u
JOIN calendrier_sanitaire c ON c.type_elevage_id = u.type_elevage_id
WHERE u.mode_suivi = 'LOT' AND u.statut = 'ACTIF';

-- ── un signalement oriente ───────────────────────────────────────────
-- Reproduit l'ecran « Signalement - Resultat » : toux, diarrhee verdatre,
-- torticolis sur LOT-014, 10 a 50 sujets touches, Newcastle probable,
-- bronchite infectieuse en alternative, oriente vers Dr Traore.
--
-- Les deux parts (0,889 et 0,111) sont celles que le moteur calcule
-- reellement a partir des ponderations de V3 : elles ont ete verifiees en
-- SQL, pas estimees.

INSERT INTO signalement_maladie (eleveur_id, unite_id, tranche_effectif,
        `precision`, date_signalement, maladie_probable_id, probabilite,
        maladie_alternative_id, probabilite_alternative, gravite, statut,
        veterinaire_id, date_orientation, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='70000000'),
       u.id, 'DE_10_A_50',
       'Les sujets touches sont surtout dans le fond du poulailler.',
       DATE_SUB(NOW(6), INTERVAL 2 DAY),
       (SELECT id FROM maladie WHERE nom = 'Maladie de Newcastle'), 0.889,
       (SELECT id FROM maladie WHERE nom = 'Bronchite infectieuse'), 0.111,
       'ELEVE', 'ORIENTE',
       (SELECT id FROM utilisateur WHERE telephone='76000000'),
       DATE_SUB(NOW(6), INTERVAL 2 DAY), NOW(6)
FROM unite_elevage u WHERE u.code = 'LOT-014';

INSERT INTO signalement_symptome (signalement_id, symptome_id)
SELECT s.id, y.id FROM signalement_maladie s
JOIN symptome y ON y.libelle IN ('Toux', 'Diarrhee verdatre', 'Torticolis');

-- ── conseils ─────────────────────────────────────────────────────────
-- Deux publies (le fil des conseils) et un en moderation (la file de
-- l'administration, « 1 conseil en attente »).

INSERT INTO conseil (titre, contenu, filiere, categorie, auteur_id, statut,
        date_soumission, date_publication, moderateur_id, nombre_lectures,
        date_creation)
SELECT v.titre, v.contenu, v.filiere, v.categorie,
       (SELECT id FROM utilisateur WHERE telephone = v.auteur),
       v.statut,
       DATE_SUB(NOW(6), INTERVAL v.jours + 1 DAY),
       CASE WHEN v.statut = 'PUBLIE' THEN DATE_SUB(NOW(6), INTERVAL v.jours DAY) END,
       CASE WHEN v.statut = 'PUBLIE'
            THEN (SELECT id FROM utilisateur WHERE telephone = '65000000') END,
       v.lectures, DATE_SUB(NOW(6), INTERVAL v.jours + 2 DAY)
FROM (
  SELECT 'Preparer le poulailler avant la mise en place' AS titre,
         'Un poulailler mal prepare coute plus cher que la mortalite qu''il provoque.\n\n'
         'Videz entierement le batiment, grattez le sol, puis desinfectez. Laissez '
         'secher au moins 48 heures avant d''etendre la litiere : une litiere posee '
         'sur un sol humide devient un foyer de coccidiose des la premiere semaine.\n\n'
         'Comptez 0,1 m² par poussin. Serrer davantage ne fait pas economiser le '
         'batiment : cela fait perdre des sujets.' AS contenu,
         'VOLAILLE' AS filiere, 'HYGIENE' AS categorie, '76000000' AS auteur,
         'PUBLIE' AS statut, 12 AS jours, 47 AS lectures
  UNION ALL SELECT
         'Reconnaitre la coccidiose a temps',
         'La coccidiose se voit d''abord dans les fientes : du sang, meme en petite '
         'quantite, doit alerter.\n\nLes sujets s''isolent, se herissent et cessent de '
         'manger. A ce stade, un anticoccidien dans l''eau de boisson pendant 5 jours '
         'suffit le plus souvent.\n\nAttendre trois jours de plus, et c''est le lot '
         'entier qui est touche.',
         'VOLAILLE', 'SANTE', '76000000', 'PUBLIE', 5, 23
  UNION ALL SELECT
         'L''embouche ovine : ce que coute vraiment un belier',
         'Un belier achete 55 000 FCFA ne revient pas a 55 000 FCFA.\n\nComptez '
         'l''aliment d''embouche sur 90 jours, le deparasitage, et le transport. '
         'Le cycle complet approche 80 000 FCFA.\n\nVendu au bon moment — avant '
         'la Tabaski, pas apres — il se negocie bien au-dela. Vendu apres, il ne '
         'rembourse parfois pas son aliment.',
         'OVINS', 'PRODUCTION', '76000001', 'EN_MODERATION', 1, 0
) AS v;

-- ── une alerte active ────────────────────────────────────────────────
-- « Alerte epidemie — Newcastle · Zone : Koutiala », telle que le tableau
-- de bord l'affiche. La portee est le nombre d'eleveurs de volaille de la
-- zone au moment du declenchement : elle est figee, pas recalculee.

INSERT INTO alerte (maladie_id, zone_id, filiere, message, portee,
        date_declenchement, statut, administrateur_id, date_creation)
SELECT m.id, z.id, 'VOLAILLE',
       'Plusieurs cas de Newcastle signales sur la zone de Koutiala. '
       'Verifiez la vaccination de vos lots, evitez les mouvements d''animaux '
       'et signalez tout symptome sans attendre.',
       (SELECT COUNT(*) FROM eleveur e
          JOIN utilisateur u2 ON u2.id = e.utilisateur_id
          JOIN eleveur_filiere ef ON ef.eleveur_id = e.utilisateur_id
         WHERE u2.zone_id = z.id AND ef.filiere = 'VOLAILLE'),
       DATE_SUB(NOW(6), INTERVAL 1 DAY), 'ACTIVE',
       (SELECT id FROM utilisateur WHERE telephone = '65000000'), NOW(6)
FROM maladie m JOIN zone z ON z.nom = 'Koutiala'
WHERE m.nom = 'Maladie de Newcastle';

-- ── notifications ────────────────────────────────────────────────────
-- Les deux que la cloche annonce sur le tableau de bord. statut_envoi a
-- ENVOYE : sans jeton d'appareil enregistre, rien n'a ete pousse, et ce
-- n'est pas un echec — l'eleveur les lit dans l'application.

INSERT INTO notification (destinataire_id, type, titre, corps, lue,
        reference_type, reference_id, statut_envoi, date_envoi,
        tentatives_envoi, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='70000000'),
       'ALERTE', 'Alerte Newcastle — Koutiala',
       'Plusieurs cas signales sur votre zone. Verifiez la vaccination de vos lots.',
       FALSE, 'ALERTE', a.id, 'ENVOYE', DATE_SUB(NOW(6), INTERVAL 1 DAY), 1,
       DATE_SUB(NOW(6), INTERVAL 1 DAY)
FROM alerte a WHERE a.statut = 'ACTIVE';

INSERT INTO notification (destinataire_id, type, titre, corps, lue,
        reference_type, reference_id, statut_envoi, date_envoi,
        tentatives_envoi, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='70000000'),
       'RAPPEL', 'Rappel vaccination',
       CONCAT('LOT-014 — Newcastle J21, echeance le ',
              DATE_FORMAT(r.date_echeance, '%d/%m/%Y')),
       FALSE, 'RAPPEL', r.id, 'ENVOYE', DATE_SUB(NOW(6), INTERVAL 1 DAY), 1,
       DATE_SUB(NOW(6), INTERVAL 1 DAY)
FROM rappel_sanitaire r
JOIN unite_elevage u ON u.id = r.unite_id
WHERE u.code = 'LOT-014' AND r.statut = 'A_VENIR'
LIMIT 1;

-- Le veterinaire a lui aussi sa notification : celle du signalement qui
-- vient de lui etre confie.
INSERT INTO notification (destinataire_id, type, titre, corps, lue,
        reference_type, reference_id, statut_envoi, date_envoi,
        tentatives_envoi, date_creation)
SELECT (SELECT id FROM utilisateur WHERE telephone='76000000'),
       'ORIENTATION', 'Nouveau signalement — Maladie de Newcastle',
       'Balla Kone a signale des symptomes sur LOT-014 (10 a 50 sujets touches)',
       FALSE, 'SIGNALEMENT', s.id, 'ENVOYE', DATE_SUB(NOW(6), INTERVAL 2 DAY), 1,
       DATE_SUB(NOW(6), INTERVAL 2 DAY)
FROM signalement_maladie s LIMIT 1;
