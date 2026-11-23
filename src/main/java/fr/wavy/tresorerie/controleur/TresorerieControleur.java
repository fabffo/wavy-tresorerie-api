package fr.wavy.tresorerie.controleur;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.wavy.tresorerie.dto.CreationPieceLocaleDemande;
import fr.wavy.tresorerie.dto.ConfirmationCarteDemande;
import fr.wavy.tresorerie.dto.AssistantRapprochementDto.*;
import fr.wavy.tresorerie.dto.ConfirmationCartesDemande;
import fr.wavy.tresorerie.dto.ConfirmationCartesResultat;
import fr.wavy.tresorerie.dto.PropositionsCarteResultat;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.configuration.ContexteRequeteResolver;
import fr.wavy.tresorerie.dto.BalanceAgeeReponse;
import fr.wavy.tresorerie.dto.ClassificationOperationDemande;
import fr.wavy.tresorerie.dto.CompteBancaireCreationDto;
import fr.wavy.tresorerie.dto.CompteBancaireDto;
import fr.wavy.tresorerie.dto.CompteBancaireModificationDto;
import fr.wavy.tresorerie.dto.DashboardTresorerieReponse;
import fr.wavy.tresorerie.dto.FactureEcheanceReponse;
import fr.wavy.tresorerie.dto.PageEcheancesReponse;
import fr.wavy.tresorerie.dto.KpiEcheancesReponse;
import fr.wavy.tresorerie.dto.ImportReleveDemande;
import fr.wavy.tresorerie.dto.ImportReleveReponse;
import fr.wavy.tresorerie.dto.ImportReleveResultatDto;
import fr.wavy.tresorerie.dto.OperationBancaireDemande;
import fr.wavy.tresorerie.dto.OperationBancaireReponse;
import fr.wavy.tresorerie.dto.PieceRapprochementReponse;
import fr.wavy.tresorerie.dto.RapprochementExerciceAnterieurDemande;
import fr.wavy.tresorerie.dto.RapprochementBancaireDemande;
import fr.wavy.tresorerie.dto.RapprochementBancaireReponse;
import fr.wavy.tresorerie.dto.RapprochementFacturesResultat;
import fr.wavy.tresorerie.dto.FactureOuverteRapprochementDto;
import fr.wavy.tresorerie.dto.RapprochementManuelDemande;
import fr.wavy.tresorerie.dto.RapprochementManuelResultat;
import fr.wavy.tresorerie.dto.RapprochementPropositionsResultat;
import fr.wavy.tresorerie.dto.RegleRapprochementDemande;
import fr.wavy.tresorerie.dto.RegleRapprochementReponse;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;
import fr.wavy.tresorerie.service.ImportReleveBancaireService;
import fr.wavy.tresorerie.service.TresorerieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tresorerie")
public class TresorerieControleur {
    private final TresorerieService tresorerieService;
    private final ImportReleveBancaireService importReleveService;
    private final ContexteRequeteResolver contexteResolver;

    public TresorerieControleur(
            TresorerieService tresorerieService,
            ImportReleveBancaireService importReleveService,
            ContexteRequeteResolver contexteResolver
    ) {
        this.tresorerieService = tresorerieService;
        this.importReleveService = importReleveService;
        this.contexteResolver = contexteResolver;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "tresorerie");
    }

    @GetMapping("/dashboard")
    public DashboardTresorerieReponse dashboard(HttpServletRequest request) {
        return tresorerieService.dashboard(contexte(request));
    }

    @GetMapping("/comptes-bancaires")
    public List<CompteBancaireDto> listerComptes(HttpServletRequest request) {
        return tresorerieService.listerComptes(contexte(request));
    }

    @GetMapping("/comptes-bancaires/{id}")
    public CompteBancaireDto detailCompte(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.detailCompte(contexte(request), id);
    }

    @PostMapping("/comptes-bancaires")
    @ResponseStatus(HttpStatus.CREATED)
    public CompteBancaireDto creerCompte(HttpServletRequest request, @Valid @RequestBody CompteBancaireCreationDto demande) {
        return tresorerieService.creerCompte(contexte(request), demande);
    }

    @PutMapping("/comptes-bancaires/{id}")
    public CompteBancaireDto modifierCompte(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody CompteBancaireModificationDto demande) {
        return tresorerieService.modifierCompte(contexte(request), id, demande);
    }

    @DeleteMapping("/comptes-bancaires/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactiverCompte(HttpServletRequest request, @PathVariable Long id) {
        tresorerieService.desactiverCompte(contexte(request), id);
    }

    @GetMapping("/operations-bancaires")
    public Page<OperationBancaireReponse> listerOperations(
            HttpServletRequest request,
            @RequestParam(required = false) Long compteBancaireId,
            @RequestParam(required = false) LocalDate dateDebut,
            @RequestParam(required = false) LocalDate dateFin,
            @RequestParam(required = false) String libelle,
            @RequestParam(required = false) BigDecimal montantMin,
            @RequestParam(required = false) BigDecimal montantMax,
            @RequestParam(required = false) SensOperation sens,
            @RequestParam(required = false) TypeOperationBancaire typeOperation,
            @RequestParam(required = false) String tiersReconnu,
            @RequestParam(required = false) TypeTiersReconnu typeTiersReconnu,
            @RequestParam(required = false) NatureOperationBancaire natureOperation,
            @RequestParam(required = false) StatutClassificationOperation statutClassification,
            @RequestParam(required = false) StatutOperationBancaire statutRapprochement,
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String statutConsultation,
            @RequestParam(required = false) List<String> rolesTiers,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size,
            @RequestParam(defaultValue = "dateOperation") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return tresorerieService.listerOperations(
                contexte(request), compteBancaireId, dateDebut, dateFin, libelle, montantMin, montantMax,
                sens, typeOperation, tiersReconnu, typeTiersReconnu, natureOperation, statutClassification, statutRapprochement,
                importId, statutConsultation, page, size, sortBy, sortDirection, rolesTiers);
    }

    @GetMapping("/operations-bancaires/compteurs")
    public Compteurs compterOperations(HttpServletRequest request, @org.springframework.web.bind.annotation.ModelAttribute Perimetre perimetre) {
        return tresorerieService.compterOperations(contexte(request), perimetre);
    }

    @GetMapping("/operations-bancaires/{id}/detail")
    public Detail consulterOperation(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.consulterOperation(contexte(request), id);
    }

    @GetMapping("/operations-bancaires/{id}")
    public OperationBancaireReponse detailOperation(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.detailOperation(contexte(request), id);
    }

    @PostMapping("/operations-bancaires")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationBancaireReponse creerOperation(HttpServletRequest request, @Valid @RequestBody OperationBancaireDemande demande) {
        return tresorerieService.creerOperation(contexte(request), demande);
    }

    @PutMapping("/operations-bancaires/{id}")
    public OperationBancaireReponse modifierOperation(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody OperationBancaireDemande demande) {
        return tresorerieService.modifierOperation(contexte(request), id, demande);
    }

    @DeleteMapping("/operations-bancaires/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerOperation(HttpServletRequest request, @PathVariable Long id) {
        tresorerieService.supprimerOperation(contexte(request), id);
    }

    @PatchMapping("/operations-bancaires/{id}")
    public OperationBancaireReponse patchOperation(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody OperationBancaireDemande demande) {
        return tresorerieService.modifierOperation(contexte(request), id, demande);
    }

    @PatchMapping("/operations-bancaires/{id}/classification")
    public OperationBancaireReponse modifierClassificationOperation(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody ClassificationOperationDemande demande) {
        return tresorerieService.modifierClassificationOperation(contexte(request), id, demande);
    }

    @PostMapping("/operations-bancaires/{id}/retraiter")
    public OperationBancaireReponse retraiterOperation(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.retraiterOperation(contexte(request), id);
    }

    @PostMapping("/operations-bancaires/retraiter-non-rapprochees")
    public Map<String, Integer> retraiterOperationsNonRapprochees(HttpServletRequest request) {
        return Map.of("nombreOperations", tresorerieService.retraiterOperationsNonRapprochees(contexte(request)));
    }

    @PostMapping("/operations-bancaires/calcul-nom-tiers")
    public Map<String, Integer> calculerNomTiersOperationsNonRapprochees(HttpServletRequest request) {
        return Map.of("nombreOperations", tresorerieService.calculerNomTiersOperationsNonRapprochees(contexte(request)));
    }

    @PostMapping("/operations-bancaires/rapprochement-factures")
    public RapprochementFacturesResultat rapprocherFactures(HttpServletRequest request) {
        return tresorerieService.rapprocherFactures(contexte(request));
    }

    @PostMapping("/operations-bancaires/rapprochement-salaires")
    public RapprochementFacturesResultat rapprocherSalaires(HttpServletRequest request) {
        return tresorerieService.rapprocherSalaires(contexte(request));
    }

    @GetMapping("/operations-bancaires/assistant")
    public Apercu previsualiserAssistant(HttpServletRequest request, @RequestParam Etape etape,
            @RequestParam(required=false) String categorie, @Valid @org.springframework.web.bind.annotation.ModelAttribute Perimetre perimetre) {
        return tresorerieService.previsualiserAssistant(contexte(request),etape,categorie,perimetre);
    }

    @PostMapping("/operations-bancaires/assistant/confirmer")
    public Bilan confirmerAssistant(HttpServletRequest request, @Valid @RequestBody Confirmation demande) {
        return tresorerieService.confirmerAssistant(contexte(request),demande);
    }

    @GetMapping("/operations-bancaires/rapprochement-cartes")
    public PropositionsCarteResultat proposerCartes(HttpServletRequest request) {
        return tresorerieService.proposerCartes(contexte(request));
    }

    @PostMapping("/operations-bancaires/{id}/rapprochement-carte")
    public RapprochementFacturesResultat confirmerCarte(HttpServletRequest request, @PathVariable Long id,
            @Valid @RequestBody ConfirmationCarteDemande demande) {
        return tresorerieService.confirmerCarte(contexte(request), id, demande);
    }

    @PostMapping("/operations-bancaires/rapprochement-cartes/confirmer")
    public ConfirmationCartesResultat confirmerCartes(HttpServletRequest request,
            @Valid @RequestBody ConfirmationCartesDemande demande) {
        return tresorerieService.confirmerCartes(contexte(request), demande);
    }

    @PostMapping("/operations-bancaires/rapprochement-notes-frais")
    public RapprochementFacturesResultat rapprocherNotesFrais(HttpServletRequest request) {
        return tresorerieService.rapprocherNotesFrais(contexte(request));
    }

    @PostMapping("/operations-bancaires/{id}/rapprochement-factures")
    public RapprochementFacturesResultat rapprocherFacture(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.rapprocherFacture(contexte(request), id);
    }

    @GetMapping("/operations-bancaires/{id}/factures-candidates")
    public List<FactureOuverteRapprochementDto> listerFacturesCandidates(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.listerFacturesCandidates(contexte(request), id);
    }

    @PostMapping({"/operations-bancaires/{id}/rapprochements-manuels", "/operations-bancaires/{id}/ajouter-factures"})
    public RapprochementManuelResultat rapprocherManuellement(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody RapprochementManuelDemande demande
    ) {
        return tresorerieService.rapprocherManuellement(contexte(request), id, demande);
    }

    @PostMapping("/operations-bancaires/{id}/rapprochement-note-frais")
    public RapprochementFacturesResultat rapprocherNoteFrais(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.rapprocherNoteFrais(contexte(request), id);
    }

    @PostMapping({"/operations-bancaires/{id}/piece-rapprochement", "/operations-bancaires/{id}/facture-rapprochement"})
    public OperationBancaireReponse creerFactureRapprochement(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.creerPieceRapprochement(contexte(request), id);
    }

    @PostMapping("/operations-bancaires/{id}/rapprochement-par-defaut")
    public OperationBancaireReponse rapprocherParDefaut(HttpServletRequest request, @PathVariable Long id,
            @Valid @RequestBody CreationPieceLocaleDemande demande) {
        return tresorerieService.rapprocherParDefaut(contexte(request), id, demande);
    }

    @PostMapping("/operations-bancaires/{id}/rapprochement-exercice-anterieur")
    public RapprochementBancaireReponse rapprocherExerciceAnterieur(HttpServletRequest request, @PathVariable Long id,
            @Valid @RequestBody RapprochementExerciceAnterieurDemande demande) {
        return tresorerieService.rapprocherExerciceAnterieur(contexte(request), id, demande);
    }

    @PostMapping("/import-releves/{id}/retraiter")
    public Map<String, Integer> retraiterImport(HttpServletRequest request, @PathVariable Long id) {
        return Map.of("nombreOperations", tresorerieService.retraiterImport(contexte(request), id));
    }

    @GetMapping("/imports-releves")
    public List<ImportReleveReponse> listerImports(HttpServletRequest request) {
        return tresorerieService.listerImports(contexte(request));
    }

    @GetMapping("/import-releves")
    public List<ImportReleveReponse> listerImportsAlias(HttpServletRequest request) {
        return tresorerieService.listerImports(contexte(request));
    }

    @GetMapping("/import-releves/{id}")
    public ImportReleveReponse detailImport(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.detailImport(contexte(request), id);
    }

    @PostMapping("/imports-releves")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportReleveReponse creerImport(HttpServletRequest request, @Valid @RequestBody ImportReleveDemande demande) {
        return tresorerieService.creerImport(contexte(request), demande);
    }

    @PostMapping("/import-releves")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportReleveResultatDto importerReleve(
            HttpServletRequest request,
            @RequestParam Long compteBancaireId,
            @RequestParam MultipartFile fichier
    ) {
        return importReleveService.importer(contexte(request), compteBancaireId, fichier);
    }

    @GetMapping("/rapprochements-bancaires")
    public List<RapprochementBancaireReponse> listerRapprochements(HttpServletRequest request) {
        return tresorerieService.listerRapprochements(contexte(request));
    }

    @GetMapping("/pieces-rapprochement")
    public List<PieceRapprochementReponse> listerPiecesRapprochement(HttpServletRequest request) {
        return tresorerieService.listerPiecesRapprochement(contexte(request));
    }

    @PostMapping("/rapprochements-bancaires")
    @ResponseStatus(HttpStatus.CREATED)
    public RapprochementBancaireReponse creerRapprochement(HttpServletRequest request, @Valid @RequestBody RapprochementBancaireDemande demande) {
        return tresorerieService.creerRapprochement(contexte(request), demande);
    }

    @PostMapping({"/rapprochements-bancaires/{id}/valider", "/rapprochements/{id}/valider"})
    public RapprochementBancaireReponse validerRapprochement(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.validerRapprochementPropose(contexte(request), id);
    }

    @PostMapping({"/rapprochements-bancaires/{id}/annuler", "/rapprochements/{id}/annuler"})
    public RapprochementBancaireReponse annulerRapprochement(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.annulerRapprochementPropose(contexte(request), id);
    }

    @PostMapping("/rapprochements-bancaires/{id}/annuler-valide")
    public RapprochementBancaireReponse annulerRapprochementValide(HttpServletRequest request, @PathVariable Long id) {
        return tresorerieService.annulerRapprochementValide(contexte(request), id);
    }

    @PostMapping({"/rapprochements-bancaires/valider-proposes", "/rapprochements/valider-proposes"})
    public RapprochementPropositionsResultat validerRapprochementsProposes(HttpServletRequest request) {
        return tresorerieService.validerRapprochementsProposes(contexte(request));
    }

    @PostMapping({"/rapprochements-bancaires/annuler-proposes", "/rapprochements/annuler-proposes"})
    public RapprochementPropositionsResultat annulerRapprochementsProposes(HttpServletRequest request) {
        return tresorerieService.annulerRapprochementsProposes(contexte(request));
    }

    @GetMapping("/regles-rapprochement")
    public List<RegleRapprochementReponse> listerRegles(HttpServletRequest request) {
        return tresorerieService.listerRegles(contexte(request));
    }

    @PostMapping("/regles-rapprochement")
    @ResponseStatus(HttpStatus.CREATED)
    public RegleRapprochementReponse creerRegle(HttpServletRequest request, @Valid @RequestBody RegleRapprochementDemande demande) {
        return tresorerieService.creerRegle(contexte(request), demande);
    }

    @PutMapping("/regles-rapprochement/{id}")
    public RegleRapprochementReponse modifierRegle(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody RegleRapprochementDemande demande) {
        return tresorerieService.modifierRegle(contexte(request), id, demande);
    }

    @GetMapping("/factures-echeances")
    public PageEcheancesReponse<FactureEcheanceReponse> listerFacturesEcheances(HttpServletRequest request) {
        return tresorerieService.listerFacturesEcheances(contexte(request), request.getQueryString());
    }

    @GetMapping("/factures-echeances/kpis")
    public KpiEcheancesReponse kpisFacturesEcheances(HttpServletRequest request) {
        return tresorerieService.kpisFacturesEcheances(contexte(request));
    }

    @GetMapping("/balance-agee")
    public BalanceAgeeReponse balanceAgee(HttpServletRequest request) {
        return tresorerieService.balanceAgee(contexte(request));
    }

    private ContexteRequete contexte(HttpServletRequest request) {
        return contexteResolver.resoudre(request);
    }
}
