package fr.wavy.tresorerie.dto;

public record RegleRapprochementFournisseurFacturesDto(
        Long id,
        Long tenantId,
        Long societeCouranteId,
        Long tiersId,
        String libelle,
        String motif,
        String typeRegle,
        String typeMotif,
        Integer scorePriorite,
        Boolean actif
) {
}
