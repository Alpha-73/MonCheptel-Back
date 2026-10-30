package ml.moncheptel.api.dto.reponse;

/**
 * « Solde du mois — toutes filieres : + 74 000 FCFA ».
 *
 * <p>Les trois nombres sortent ensemble. Le client pourrait soustraire
 * lui-meme, mais alors deux clients pourraient arrondir differemment et
 * afficher deux soldes pour les memes ecritures.
 */
public record SoldeReponse(long revenus, long depenses, long solde) {

    public static SoldeReponse vide() {
        return new SoldeReponse(0L, 0L, 0L);
    }
}
