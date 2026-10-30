package ml.moncheptel.api.modele.enumeration;

import java.util.Arrays;
import java.util.List;

/**
 * Categories d'operation financiere.
 *
 * <p>Les cinq depenses sont exactement celles du menu deroulant de
 * « Operation - Categorie ouverte » : Aliment, Soins et vaccination,
 * Main-d'oeuvre, Transport, Equipement. Les revenus viennent de
 * « Operation - Revenu rempli » et de l'ecran « Depenses - Liste »
 * (« Vente bovin BOV-009 », « Vente oeufs »).
 *
 * <p>Une enumeration et non une table : la maquette n'a aucun ecran
 * d'administration des categories, et une liste que personne ne peut modifier
 * n'a pas besoin d'etre en base.
 */
public enum CategorieOperation {

    ALIMENT("Aliment", TypeOperation.DEPENSE),
    SOINS_VACCINATION("Soins et vaccination", TypeOperation.DEPENSE),
    MAIN_D_OEUVRE("Main-d'œuvre", TypeOperation.DEPENSE),
    TRANSPORT("Transport", TypeOperation.DEPENSE),
    EQUIPEMENT("Équipement", TypeOperation.DEPENSE),

    VENTE_ANIMAUX("Vente d'animaux", TypeOperation.REVENU),
    VENTE_PRODUITS("Vente de produits", TypeOperation.REVENU);

    private final String libelle;
    private final TypeOperation type;

    CategorieOperation(String libelle, TypeOperation type) {
        this.libelle = libelle;
        this.type = type;
    }

    public String getLibelle() {
        return libelle;
    }

    public TypeOperation getType() {
        return type;
    }

    /** Les categories proposees pour un type donne — alimente le menu deroulant. */
    public static List<CategorieOperation> pourType(TypeOperation type) {
        return Arrays.stream(values()).filter(c -> c.type == type).toList();
    }
}
