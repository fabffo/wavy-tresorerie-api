package fr.wavy.tresorerie.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import fr.wavy.tresorerie.dto.CompteBancaireDto;
import fr.wavy.tresorerie.dto.ImportReleveReponse;
import fr.wavy.tresorerie.dto.OperationBancaireReponse;
import fr.wavy.tresorerie.dto.RapprochementBancaireReponse;
import fr.wavy.tresorerie.dto.RegleRapprochementReponse;
import fr.wavy.tresorerie.modele.CompteBancaire;
import fr.wavy.tresorerie.modele.ImportReleveBancaire;
import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.RapprochementBancaire;
import fr.wavy.tresorerie.modele.RegleRapprochementBancaire;

@Component
public class TresorerieMapper {
    public CompteBancaireDto versReponse(CompteBancaire compte, boolean ibanComplet) {
        String ibanMasque = masquerIban(compte.getIban());
        return new CompteBancaireDto(
                compte.getId(),
                compte.getTenantId(),
                compte.getSocieteInterneId(),
                compte.getNomCompte(),
                compte.getBanque(),
                ibanComplet ? compte.getIban() : null,
                ibanMasque,
                compte.getBic(),
                compte.getDevise(),
                compte.getSoldeInitial(),
                compte.getDateSoldeInitial(),
                compte.getSoldeActuel(),
                compte.isActif(),
                compte.getDateCreation(),
                compte.getDateModification(),
                "%s - %s - %s".formatted(compte.getBanque(), compte.getNomCompte(), ibanMasque)
        );
    }

    public OperationBancaireReponse versReponse(OperationBancaire operation) {
        return versReponse(operation, null);
    }

    public OperationBancaireReponse versReponse(OperationBancaire operation, Boolean pieceRapprochementAutorisee) {
        return versReponse(operation, pieceRapprochementAutorisee, List.of());
    }

    public OperationBancaireReponse versReponse(OperationBancaire operation, Boolean pieceRapprochementAutorisee, List<String> rolesTiers) {
        return new OperationBancaireReponse(
                operation.getId(),
                operation.getCompteBancaire().getId(),
                operation.getCompteBancaire().getNomCompte(),
                operation.getImportReleveBancaire() == null ? null : operation.getImportReleveBancaire().getId(),
                operation.getDateOperation(),
                operation.getDateValeur(),
                operation.getLibelle(),
                operation.getReferenceBancaire(),
                operation.getDebit(),
                operation.getCredit(),
                operation.getMontant(),
                operation.getSens(),
                operation.getTypeOperation(),
                operation.getTiersReconnu(),
                operation.getTypeTiersReconnu(),
                operation.getNatureOperation(),
                operation.getStatutClassification(),
                operation.getStatutRapprochement(),
                operation.isRapprochee(),
                operation.getNumeroLigneImport(),
                operation.getHashOperation(),
                operation.getTiersId(),
                operation.getFactureId(),
                operation.getTypeFacture(),
                operation.getSalaireId(),
                operation.getFactureRapprochementId(),
                pieceRapprochementAutorisee,
                operation.getLibelleRapprochement(),
                operation.getDateRapprochement(),
                operation.getMontantRapproche(),
                operation.getNumeroPieceAssociee(),
                operation.getPieceRapprochementId(),
                rolesTiers
        );
    }

    public ImportReleveReponse versReponse(ImportReleveBancaire importReleve) {
        return new ImportReleveReponse(
                importReleve.getId(),
                importReleve.getCompteBancaire().getId(),
                importReleve.getCompteBancaire().getNomCompte(),
                importReleve.getNomFichier(),
                importReleve.getTypeFichier(),
                importReleve.getStatutImport(),
                importReleve.getNombreLignes(),
                importReleve.getNombreLignesImportees(),
                importReleve.getNombreLignesRejetees(),
                importReleve.getTotalDebit(),
                importReleve.getTotalCredit(),
                importReleve.getDateImport(),
                importReleve.getMessageErreur()
        );
    }

    public RapprochementBancaireReponse versReponse(RapprochementBancaire rapprochement) {
        return new RapprochementBancaireReponse(
                rapprochement.getId(),
                rapprochement.getOperationBancaire().getId(),
                rapprochement.getOperationBancaire().getLibelle(),
                rapprochement.getOperationBancaire().getTiersReconnu(),
                rapprochement.getFactureId(),
                rapprochement.getTypeFacture(),
                rapprochement.getSalaireId(),
                rapprochement.getPeriodeSalaire(),
                rapprochement.getMatriculeSalarie(),
                rapprochement.getPieceRapprochementId(),
                rapprochement.getTypePiece(),
                rapprochement.getDocumentAssocieNumero(),
                rapprochement.getDocumentAssocieLibelle(),
                rapprochement.getOperationBancaire().getSens() == fr.wavy.tresorerie.modele.SensOperation.DEBIT
                        ? rapprochement.getOperationBancaire().getDebit()
                        : rapprochement.getOperationBancaire().getCredit(),
                rapprochement.getMontantRapproche(),
                rapprochement.getEcart(),
                rapprochement.getStatut(),
                rapprochement.getModeRapprochement(),
                rapprochement.getLibelleRapprochement(),
                rapprochement.getScoreMatching(),
                rapprochement.getCommentaire(),
                rapprochement.getDateRapprochement(),
                rapprochement.getUtilisateurId(),
                rapprochement.getDateProposition(),
                rapprochement.getDateValidation(),
                rapprochement.getUtilisateurValidationId(),
                rapprochement.getDateAnnulation(),
                rapprochement.getUtilisateurAnnulationId(),
                rapprochement.getMotifAnnulation()
        );
    }

    public RegleRapprochementReponse versReponse(RegleRapprochementBancaire regle) {
        return new RegleRapprochementReponse(
                regle.getId(),
                regle.getLibelle(),
                regle.getMotif(),
                regle.getSens(),
                regle.getTiersId(),
                regle.getPriorite(),
                regle.isActive()
        );
    }

    private String masquerIban(String iban) {
        if (iban == null || iban.isBlank()) {
            return "";
        }
        String compact = iban.replaceAll("\\s+", "").toUpperCase();
        String fin = compact.length() <= 4 ? compact : compact.substring(compact.length() - 4);
        String prefixe = compact.length() >= 4 ? compact.substring(0, 4) : compact;
        return "%s **** **** %s".formatted(prefixe, fin);
    }
}
