package fr.wavy.tresorerie.client;

import java.util.List;
import java.math.BigDecimal;

import fr.wavy.tresorerie.dto.PieceCarteRapprochementDto;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.BalanceAgeeReponse;
import fr.wavy.tresorerie.dto.CreationFactureAchatDepuisOperationBancaireDemande;
import fr.wavy.tresorerie.dto.EnregistrementPaiementFactureDto;
import fr.wavy.tresorerie.dto.FactureAchatRapprochementReponse;
import fr.wavy.tresorerie.dto.FactureEcheanceReponse;
import fr.wavy.tresorerie.dto.PageEcheancesReponse;
import fr.wavy.tresorerie.dto.KpiEcheancesReponse;
import fr.wavy.tresorerie.dto.FactureOuverteRapprochementDto;
import fr.wavy.tresorerie.dto.MiseAJourLibelleRapprochementDemande;
import fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;

public interface FacturesTresorerieClient {
    List<PieceCarteRapprochementDto> listerCartesOuvertes(ContexteRequete contexte);

    PageEcheancesReponse<FactureEcheanceReponse> listerEcheances(ContexteRequete contexte, String queryString);
    KpiEcheancesReponse kpisEcheances(ContexteRequete contexte);
    BalanceAgeeReponse balanceAgee(ContexteRequete contexte);
    List<RegleRapprochementFournisseurFacturesDto> listerReglesRapprochementFournisseur(ContexteRequete contexte);
    FactureAchatRapprochementReponse creerFactureAchatDepuisOperationBancaire(ContexteRequete contexte, CreationFactureAchatDepuisOperationBancaireDemande demande);
    FactureAchatRapprochementReponse mettreAJourLibelleRapprochement(ContexteRequete contexte, Long factureId, MiseAJourLibelleRapprochementDemande demande);
    List<FactureOuverteRapprochementDto> listerFacturesOuvertes(ContexteRequete contexte, TypeFactureRapprochement typeFacture, Long tiersId);
    List<FactureOuverteRapprochementDto> listerSalairesOuverts(
            ContexteRequete contexte, Long tiersId, int annee, int mois, BigDecimal montant);
    List<FactureOuverteRapprochementDto> listerFacturesAchatNotesFraisOuvertes(ContexteRequete contexte, Long tiersId, BigDecimal montant);
    FactureOuverteRapprochementDto enregistrerPaiement(ContexteRequete contexte, TypeFactureRapprochement typeFacture, Long factureId, EnregistrementPaiementFactureDto demande);
}
