package ml.moncheptel.api.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import ml.moncheptel.api.modele.enumeration.Filiere;
import ml.moncheptel.api.modele.enumeration.NiveauGravite;

/**
 * Ecran « Admin - Maladie : creer ».
 *
 * <p>{@code orienteVersVeterinaire} coupe l'affichage du traitement cote
 * eleveur. C'est la seule barriere entre le catalogue et l'automedication sur
 * un troupeau : une maladie qui exige un acte veterinaire ne doit pas afficher
 * son protocole dans une application grand public.
 */
public record MaladieRequete(
        @NotBlank @Size(max = 120) String nom,
        @NotNull NiveauGravite gravite,
        @NotEmpty(message = "Indiquez au moins une filière concernée") Set<Filiere> filieres,
        @Size(max = 1000) String symptomesTexte,
        @Size(max = 2000) String traitement,
        boolean orienteVersVeterinaire,
        @NotEmpty(message = "Pondérez au moins un symptôme") List<PoidsSymptomeRequete> symptomes) {
}
