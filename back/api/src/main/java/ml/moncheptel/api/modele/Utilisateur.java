package ml.moncheptel.api.modele;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import ml.moncheptel.api.modele.enumeration.Langue;
import ml.moncheptel.api.modele.enumeration.Role;

/**
 * Compte de la plateforme, quelle qu'en soit la nature.
 *
 * <p><b>Heritage JOINED.</b> Les trois roles partagent l'identite — nom,
 * e-mail, mot de passe, zone — mais pas leurs attributs propres :
 * l'eleveur porte ses filieres pratiquees, le veterinaire sa zone
 * d'intervention et ses maladies traitees. Une table commune laisserait la
 * moitie des colonnes nulles sur chaque ligne.
 *
 * <p>Le discriminant est la colonne {@code role}, qui sert aussi d'autorite a
 * Spring Security. Elle n'est pas remappee en champ : chaque sous-classe
 * repond a {@link #getRole()}, ce qui evite qu'un jour une ligne porte
 * {@code role = 'ELEVEUR'} dans une instance de {@code Veterinaire}.
 */
@Entity
@Table(name = "utilisateur")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING, length = 16)
public abstract class Utilisateur extends EntiteBase {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nom;

    /**
     * Identifiant de connexion, et seul canal de recuperation du compte.
     *
     * <p>Obligatoire et unique. C'est par lui qu'on se connecte ET par lui
     * que « mot de passe oublie » renvoie un code : un compte sans e-mail
     * serait un compte que personne ne peut recuperer. Toujours enregistre en
     * minuscules — voir {@link #normaliserEmail(String)}.
     */
    @NotBlank
    @Email
    @Size(max = 160)
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /**
     * Numero a joindre, PAS un identifiant : facultatif, non unique.
     *
     * <p>Il subsiste parce que l'annuaire veterinaire et le signalement ont
     * besoin d'un contact direct (« appeler le veterinaire »). Deux comptes
     * peuvent partager un numero — un cabinet, un telephone de famille —, ce
     * qui interdit d'en faire une cle.
     */
    @Size(max = 20)
    @Column(length = 20)
    private String telephone;

    /** Empreinte BCrypt. Le mot de passe en clair ne quitte jamais la couche web. */
    @NotBlank
    @Column(name = "mot_de_passe", nullable = false, length = 72)
    private String motDePasse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * Toujours STRING, jamais ORDINAL : avec ORDINAL, inserer une valeur au
     * milieu de l'enumeration decalerait silencieusement toutes les lignes
     * existantes. La regle vaut pour toutes les entites de ce projet.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Langue langue = Langue.FR;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "date_inscription", nullable = false)
    private LocalDate dateInscription = LocalDate.now();

    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;

    // ── verrouillage apres echecs (cahier des charges : 5 tentatives / 15 min) ──

    @Column(name = "tentatives_echouees", nullable = false)
    private int tentativesEchouees = 0;

    @Column(name = "verrouille_jusqu_a")
    private Instant verrouilleJusquA;

    /**
     * Mot de passe provisoire a changer a la premiere connexion.
     * Ecran « Admin - Veterinaire creer » : « Un mot de passe provisoire sera
     * envoye par e-mail. Le veterinaire devra le modifier a sa premiere
     * connexion. »
     */
    @Column(name = "doit_changer_mot_de_passe", nullable = false)
    private boolean doitChangerMotDePasse = false;

    protected Utilisateur() {
    }

    protected Utilisateur(String nom, String email, String motDePasse, Zone zone) {
        this.nom = nom;
        this.email = normaliserEmail(email);
        this.motDePasse = motDePasse;
        this.zone = zone;
    }

    /**
     * Forme canonique d'une adresse : sans espaces autour, en minuscules.
     *
     * <p>Une seule regle, appelee par TOUS les points d'entree (inscription,
     * creation de veterinaire, connexion, oubli). Sans elle, « Balla@X.ml » et
     * « balla@x.ml » seraient deux saisies differentes pour un humain et une
     * seule pour MySQL — ou l'inverse selon la collation. Qui compare des
     * adresses sans passer par ici finira par refuser un utilisateur legitime.
     */
    public static String normaliserEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    /** Role porte par la sous-classe. Sert d'autorite a Spring Security. */
    public abstract Role getRole();

    // ── comportement de verrouillage ────────────────────────────────────────

    /** Vrai si le compte est verrouille a l'instant present. */
    public boolean estVerrouille() {
        return verrouilleJusquA != null && verrouilleJusquA.isAfter(Instant.now());
    }

    /**
     * Enregistre un echec d'authentification et verrouille si le seuil est
     * atteint. C'est l'entite qui compte, pas le service : la regle est une
     * propriete du compte, et elle ne doit pas pouvoir etre contournee depuis
     * un autre appelant.
     */
    public void signalerEchecConnexion(int seuil, int dureeVerrouillageMinutes) {
        this.tentativesEchouees++;
        if (this.tentativesEchouees >= seuil) {
            this.verrouilleJusquA = Instant.now().plusSeconds(
                    dureeVerrouillage(tentativesEchouees, seuil, dureeVerrouillageMinutes) * 60L);
        }
    }

    /**
     * Duree du verrou, en minutes, PROGRESSIVE (audit global G2) : 1 minute
     * au seuil, puis 2, 4, 8... jusqu'au plafond {@code dureeMaxMinutes}.
     *
     * <p>Le verrou ne bloque plus que les nouvelles connexions par mot de
     * passe : les sessions deja ouvertes continuent (FiltreJwt et le
     * rafraichissement ne le lisent plus). Il ne sert donc plus a expulser
     * quelqu'un, seulement a ralentir qui devine un mot de passe. Un titulaire
     * qui s'est trompe cinq fois n'attend qu'une minute ; un essai acharne
     * finit au plafond.
     */
    public static long dureeVerrouillage(int tentatives, int seuil, int dureeMaxMinutes) {
        int palier = Math.max(0, tentatives - seuil);
        long minutes = 1L << Math.min(palier, 20);
        return Math.max(1, Math.min(dureeMaxMinutes, minutes));
    }

    /**
     * Leve le verrouillage sans compter une connexion.
     *
     * <p>Appele apres une reinitialisation par e-mail : celui qui prouve
     * qu'il lit la boite du compte n'a pas a attendre quinze minutes. C'est
     * meme le cas d'usage principal — on ne verrouille un compte qu'en
     * s'y trompant cinq fois de mot de passe.
     */
    public void deverrouiller() {
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
    }

    /** Remet le compteur a zero apres une connexion reussie. */
    public void signalerConnexionReussie() {
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
        this.derniereConnexion = Instant.now();
    }

    /**
     * Force le changement au prochain acces.
     *
     * <p>Pose a la creation d'un compte veterinaire : le mot de passe
     * provisoire est connu du serveur de courriel et de la boite de
     * reception. Tant qu'il n'est pas remplace, le compte est a la portee de
     * quiconque lit ce courriel.
     */
    public void exigerChangementMotDePasse() {
        this.doitChangerMotDePasse = true;
    }

    public void changerMotDePasse(String empreinte) {
        this.motDePasse = empreinte;
        this.doitChangerMotDePasse = false;
    }

    // ── accesseurs ──────────────────────────────────────────────────────────

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normaliserEmail(email); }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) {
        this.telephone = telephone == null || telephone.isBlank() ? null : telephone.strip();
    }

    public String getMotDePasse() { return motDePasse; }

    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }

    public Langue getLangue() { return langue; }
    public void setLangue(Langue langue) { this.langue = langue; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public LocalDate getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDate d) { this.dateInscription = d; }

    public Instant getDerniereConnexion() { return derniereConnexion; }

    public int getTentativesEchouees() { return tentativesEchouees; }

    public Instant getVerrouilleJusquA() { return verrouilleJusquA; }

    public boolean isDoitChangerMotDePasse() { return doitChangerMotDePasse; }
    public void setDoitChangerMotDePasse(boolean v) { this.doitChangerMotDePasse = v; }
}
