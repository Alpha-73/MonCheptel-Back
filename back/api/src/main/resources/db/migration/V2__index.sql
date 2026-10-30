-- =====================================================================
-- Index
--
-- Separes du schema pour une raison de lecture : V1 dit ce que les donnees
-- SONT, V2 dit comment on y accede. Les deux evoluent a des rythmes
-- differents — on ajoute un index parce qu'un ecran rame, pas parce que le
-- metier a change.
--
-- Chaque index ci-dessous correspond a une requete precise du code. Un
-- index qu'aucune requete n'emprunte ralentit toutes les ecritures sans
-- accelerer aucune lecture ; il n'y en a donc aucun « au cas ou ».
--
-- Rappel : MySQL cree automatiquement un index sur chaque cle etrangere.
-- Ceux-ci sont ceux qu'il ne cree pas.
-- =====================================================================

-- Cheptel d'un eleveur, filtre par statut — ecran « Mes animaux »,
-- compteurs du tableau de bord.
CREATE INDEX idx_unite_proprietaire_statut ON unite_elevage (proprietaire_id, statut);

-- Cycles arrivant a terme — tache de prevenance.
CREATE INDEX idx_lot_cloture ON lot (date_cloture_prevue);

-- Journal d'une unite, dans l'ordre. L'index porte la date pour eviter un
-- tri apres lecture.
CREATE INDEX idx_mouvement_unite_date ON mouvement_cheptel (unite_id, date_mouvement);

-- Journal, soldes et rapports d'un eleveur sur une periode. C'est l'index
-- le plus sollicite de la base : le tableau de bord l'emprunte a chaque
-- ouverture.
CREATE INDEX idx_operation_eleveur_date ON operation_financiere (eleveur_id, date_operation);

-- Repartition des depenses par categorie — ecran « Rapports ».
CREATE INDEX idx_operation_eleveur_type ON operation_financiere (eleveur_id, type, date_operation);

-- Rentabilite d'un lot.
CREATE INDEX idx_operation_unite ON operation_financiere (unite_id);

-- Carnet sanitaire d'une unite.
CREATE INDEX idx_evenement_unite_date ON evenement_sanitaire (unite_id, date_evenement);

-- Rappels a afficher et rappels echus a reevaluer.
CREATE INDEX idx_rappel_statut_echeance ON rappel_sanitaire (statut, date_echeance);
CREATE INDEX idx_rappel_unite ON rappel_sanitaire (unite_id, date_echeance);

-- Comptage epidemiologique : combien de signalements de cette maladie
-- depuis telle date.
CREATE INDEX idx_signalement_maladie_date ON signalement_maladie (maladie_probable_id, date_signalement);

-- File d'attente du veterinaire.
CREATE INDEX idx_signalement_veto_statut ON signalement_maladie (veterinaire_id, statut);

-- Historique de l'eleveur.
CREATE INDEX idx_signalement_eleveur_date ON signalement_maladie (eleveur_id, date_signalement);

-- Moteur de diagnostic : « quelles maladies expliquent ces symptomes ».
-- L'index part du SYMPTOME, parce que c'est par la que la requete entre :
-- elle recoit une liste de symptomes coches et cherche les maladies. Un
-- index (maladie_id, symptome_id) existe deja via la contrainte d'unicite,
-- mais il ne sert pas ce sens de lecture.
CREATE INDEX idx_maladie_symptome_inverse ON maladie_symptome (symptome_id, maladie_id);

-- Fil des conseils publies, du plus recent au plus ancien.
CREATE INDEX idx_conseil_statut_date ON conseil (statut, date_publication);

-- File de moderation.
CREATE INDEX idx_conseil_auteur ON conseil (auteur_id, statut);

-- Alertes actives d'une zone.
CREATE INDEX idx_alerte_zone_statut ON alerte (zone_id, statut);

-- Centre de notifications, et pastille des non-lues.
CREATE INDEX idx_notification_destinataire ON notification (destinataire_id, date_creation);
CREATE INDEX idx_notification_non_lues ON notification (destinataire_id, lue);

-- File de reprise des envois Firebase.
CREATE INDEX idx_notification_envoi ON notification (statut_envoi, tentatives_envoi);

-- Jetons actifs d'un utilisateur, a chaque notification poussee.
CREATE INDEX idx_jeton_utilisateur ON jeton_appareil (utilisateur_id, actif);

-- Purge des sessions expirees.
CREATE INDEX idx_session_expiration ON session (expire_le);

-- Annuaire veterinaire d'une zone.
CREATE INDEX idx_veterinaire_zone ON veterinaire (zone_intervention_id, disponible);
