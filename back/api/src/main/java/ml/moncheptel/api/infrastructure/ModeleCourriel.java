package ml.moncheptel.api.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texte d'un courriel, lu dans un fichier modele que l'on peut modifier sans
 * toucher au code ni recompiler.
 *
 * <h2>Ou est cherche le modele</h2>
 *
 * <ol>
 *   <li>{@code config/courriels/<nom>.txt}, a cote de
 *       {@code config/application.properties} (dossier de lancement du back) :
 *       c'est LA que l'on personnalise ;</li>
 *   <li>sinon {@code courriels/<nom>.txt} dans les ressources de l'application :
 *       le texte livre par defaut.</li>
 * </ol>
 *
 * <p>Le fichier est relu a chaque envoi. Modifier un modele de la deuxieme
 * source demande de reconstruire l'application ; le modifier dans la
 * premiere, non.
 *
 * <h2>Format du fichier (UTF-8)</h2>
 *
 * <pre>
 * Objet : MonCheptel - votre acces
 *
 * Bonjour {{nom}},
 * ...
 * </pre>
 *
 * <p>La premiere ligne commence par {@code Objet :}. La suite, apres la
 * premiere ligne vide, est le corps. Les {@code {{variable}}} sont remplacees
 * par les valeurs de l'envoi.
 *
 * <h2>Une faute de frappe ne doit jamais bloquer un envoi</h2>
 *
 * <p>Creer un compte veterinaire envoie un courriel DANS la transaction : si
 * l'envoi echoue, le compte n'est pas cree. Un modele ou l'on a ecrit
 * {@code {{mdp}}} au lieu de {@code {{motDePasse}}} ne doit donc pas lever
 * d'exception. La variable inconnue est laissee telle quelle dans le texte et
 * signalee dans {@link Message#variablesInconnues()}, que l'appelant journalise.
 *
 * <h2>Valeurs inserees</h2>
 *
 * <p>Les sauts de ligne sont retires de chaque valeur : un nom contenant un
 * retour a la ligne ne doit ni couper l'objet du courriel ni y ajouter un
 * en-tete. Le texte est brut, jamais du HTML : rien a echapper.
 */
public class ModeleCourriel {

    /** Courriel pret a partir. {@code variablesInconnues} : noms seulement, jamais de valeurs. */
    public record Message(String objet, String corps, Set<String> variablesInconnues) {}

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
    private static final String PREFIXE_OBJET = "Objet";

    private final Path dossierExterne;

    public ModeleCourriel(Path dossierExterne) {
        this.dossierExterne = dossierExterne;
    }

    /**
     * @param nom     nom du modele, sans extension (ex. {@code acces-veterinaire})
     * @param valeurs variable &rarr; valeur
     * @throws IllegalStateException si aucun modele de ce nom n'existe ou s'il
     *         n'a pas de ligne {@code Objet :}
     */
    public Message rendre(String nom, Map<String, String> valeurs) {
        String brut = lire(nom).replace("\r\n", "\n").replace('\r', '\n');
        if (brut.startsWith("﻿")) {
            // Le Bloc-notes de Windows ajoute parfois ce marqueur en tete.
            brut = brut.substring(1);
        }

        int finPremiereLigne = brut.indexOf('\n');
        String premiere = finPremiereLigne < 0 ? brut : brut.substring(0, finPremiereLigne);
        if (!premiere.startsWith(PREFIXE_OBJET) || !premiere.contains(":")) {
            throw new IllegalStateException("Le modele de courriel '" + nom
                    + "' doit commencer par une ligne « Objet : ... ».");
        }
        String objetBrut = premiere.substring(premiere.indexOf(':') + 1).strip();
        String corpsBrut = finPremiereLigne < 0 ? "" : brut.substring(finPremiereLigne + 1);
        // On saute les lignes vides qui separent l'objet du corps.
        corpsBrut = corpsBrut.replaceFirst("^\\n+", "");

        Set<String> inconnues = new LinkedHashSet<>();
        String objet = remplacer(objetBrut, valeurs, inconnues);
        String corps = remplacer(corpsBrut, valeurs, inconnues);
        if (objet.isBlank()) {
            throw new IllegalStateException("L'objet du modele de courriel '" + nom
                    + "' est vide.");
        }
        return new Message(objet, corps, inconnues);
    }

    private String lire(String nom) {
        try {
            Path externe = dossierExterne.resolve(nom + ".txt");
            if (Files.isRegularFile(externe)) {
                return Files.readString(externe, StandardCharsets.UTF_8);
            }
            try (InputStream flux =
                         ModeleCourriel.class.getResourceAsStream("/courriels/" + nom + ".txt")) {
                if (flux == null) {
                    throw new IllegalStateException("Modele de courriel introuvable : " + nom);
                }
                return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Modele de courriel illisible : " + nom, e);
        }
    }

    private static String remplacer(String texte, Map<String, String> valeurs,
                                    Set<String> inconnues) {
        Matcher m = VARIABLE.matcher(texte);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            String cle = m.group(1);
            String valeur = valeurs.get(cle);
            if (valeur == null) {
                inconnues.add(cle);
                m.appendReplacement(sortie, Matcher.quoteReplacement(m.group()));
            } else {
                m.appendReplacement(sortie,
                        Matcher.quoteReplacement(valeur.replace('\r', ' ').replace('\n', ' ')));
            }
        }
        m.appendTail(sortie);
        return sortie.toString();
    }
}
