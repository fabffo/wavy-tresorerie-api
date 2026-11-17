package fr.wavy.tresorerie.dto;

import java.util.Set;

public record TiersTresorerieDto(
        Long id,
        Set<String> roles,
        String raisonSociale,
        String nom,
        String prenom,
        String nomNormalise,
        Boolean actif,
        Boolean pieceRapprochementAutorisee,
        String nomCommercial, String enseigne, String siren, String siret, String tva
) {
    public TiersTresorerieDto(Long id, Set<String> roles, String raisonSociale, String nom, String prenom,
            String nomNormalise, Boolean actif, Boolean autorisee) {
        this(id, roles, raisonSociale, nom, prenom, nomNormalise, actif, autorisee, null, null, null, null, null);
    }
    public String libelle() {
        if (prenom != null && !prenom.isBlank() && nom != null && !nom.isBlank()) {
            return prenom.trim() + " " + nom.trim();
        }
        if (raisonSociale != null && !raisonSociale.isBlank()) {
            return raisonSociale.trim();
        }
        return nomNormalise;
    }
}
