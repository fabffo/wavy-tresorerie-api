package fr.wavy.tresorerie.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import fr.wavy.tresorerie.client.FacturesTresorerieClient;
import fr.wavy.tresorerie.client.TiersTresorerieClient;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.BalanceAgeeReponse;
import fr.wavy.tresorerie.dto.ClassificationOperationDemande;
import fr.wavy.tresorerie.dto.CompteBancaireCreationDto;
import fr.wavy.tresorerie.dto.CompteBancaireDto;
import fr.wavy.tresorerie.dto.CompteBancaireModificationDto;
import fr.wavy.tresorerie.dto.CreationFactureAchatDepuisOperationBancaireDemande;
import fr.wavy.tresorerie.dto.DashboardTresorerieReponse;
import fr.wavy.tresorerie.dto.EnregistrementPaiementFactureDto;
import fr.wavy.tresorerie.dto.FactureAchatRapprochementReponse;
import fr.wavy.tresorerie.dto.FactureEcheanceReponse;
import fr.wavy.tresorerie.dto.PageEcheancesReponse;
import fr.wavy.tresorerie.dto.KpiEcheancesReponse;
import fr.wavy.tresorerie.dto.FactureOuverteRapprochementDto;
import fr.wavy.tresorerie.dto.ImportReleveDemande;
import fr.wavy.tresorerie.dto.ImportReleveReponse;
import fr.wavy.tresorerie.dto.MiseAJourLibelleRapprochementDemande;
import fr.wavy.tresorerie.dto.OperationBancaireDemande;
import fr.wavy.tresorerie.dto.OperationBancaireReponse;
import fr.wavy.tresorerie.dto.PieceRapprochementReponse;
import fr.wavy.tresorerie.dto.RapprochementBancaireDemande;
import fr.wavy.tresorerie.dto.RapprochementBancaireReponse;
import fr.wavy.tresorerie.dto.RapprochementFacturesResultat;
import fr.wavy.tresorerie.dto.RapprochementPropositionsResultat;
import fr.wavy.tresorerie.dto.RapprochementManuelDemande;
import fr.wavy.tresorerie.dto.RapprochementManuelResultat;
import fr.wavy.tresorerie.dto.LigneRapprochementManuelDemande;
import fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto;
import fr.wavy.tresorerie.dto.RegleRapprochementDemande;
import fr.wavy.tresorerie.dto.RegleRapprochementReponse;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;
import fr.wavy.tresorerie.exception.RegleMetierException;
import fr.wavy.tresorerie.exception.CreationPieceInterditeException;
import fr.wavy.tresorerie.exception.RessourceNonTrouveeException;
import fr.wavy.tresorerie.mapper.TresorerieMapper;
import fr.wavy.tresorerie.modele.CompteBancaire;
import fr.wavy.tresorerie.modele.ImportReleveBancaire;
import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.PieceRapprochement;
import fr.wavy.tresorerie.modele.TypePieceRapprochement;
import fr.wavy.tresorerie.modele.RapprochementBancaire;
import fr.wavy.tresorerie.modele.RegleRapprochementBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.StatutRapprochement;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;
import fr.wavy.tresorerie.repository.CompteBancaireRepository;
import fr.wavy.tresorerie.repository.ImportReleveBancaireRepository;
import fr.wavy.tresorerie.repository.OperationBancaireRepository;
import fr.wavy.tresorerie.repository.PieceRapprochementRepository;
import fr.wavy.tresorerie.repository.RapprochementBancaireRepository;
import fr.wavy.tresorerie.repository.RegleRapprochementBancaireRepository;
import fr.wavy.tresorerie.service.ClassificationOperationBancaireService.RegleClassificationOperation;
import fr.wavy.tresorerie.service.ClassificationOperationBancaireService.ResultatReconnaissanceTiers;
import fr.wavy.tresorerie.service.importreleve.LigneReleveBancaire;

@Service
@Transactional
public class TresorerieService {
    private static final int TAILLE_PAGE_OPERATIONS_DEFAUT = 25;
    private static final int TAILLE_PAGE_OPERATIONS_MAX = 200;
    private static final BigDecimal TOLERANCE_MONTANT = new BigDecimal("0.01");
    private static final DateTimeFormatter DATE_RAPPROCHEMENT_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, String> CHAMPS_TRI_OPERATIONS = Map.ofEntries(
            Map.entry("dateOperation", "dateOperation"),
            Map.entry("libelle", "libelle"),
            Map.entry("compte", "compteBancaire.nomCompte"),
            Map.entry("debit", "debit"),
            Map.entry("credit", "credit"),
            Map.entry("montant", "montant"),
            Map.entry("sens", "sens"),
            Map.entry("typeOperation", "typeOperation"),
            Map.entry("tiersReconnu", "tiersReconnu"),
            Map.entry("typeTiersReconnu", "typeTiersReconnu"),
            Map.entry("natureOperation", "natureOperation"),
            Map.entry("statutClassification", "statutClassification"),
            Map.entry("statutRapprochement", "statutRapprochement"),
            Map.entry("libelleRapprochement", "libelleRapprochement")
    );

    private final CompteBancaireRepository compteRepository;
    private final OperationBancaireRepository operationRepository;
    private final PieceRapprochementRepository pieceRapprochementRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ImportReleveBancaireRepository importRepository;
    private final RapprochementBancaireRepository rapprochementRepository;
    private final RegleRapprochementBancaireRepository regleRepository;
    private final FacturesTresorerieClient facturesClient;
    private final TiersTresorerieClient tiersClient;
    private final TresorerieMapper mapper;
    private final ImportReleveBancaireService importReleveService;
    private final PeriodeSalaireService periodeSalaireService = new PeriodeSalaireService();
    private final ClassificationOperationBancaireService classificationService;

    public TresorerieService(
            CompteBancaireRepository compteRepository,
            OperationBancaireRepository operationRepository,
            PieceRapprochementRepository pieceRapprochementRepository,
            JdbcTemplate jdbcTemplate,
            ImportReleveBancaireRepository importRepository,
            RapprochementBancaireRepository rapprochementRepository,
            RegleRapprochementBancaireRepository regleRepository,
            FacturesTresorerieClient facturesClient,
            TiersTresorerieClient tiersClient,
            TresorerieMapper mapper,
            ImportReleveBancaireService importReleveService,
            ClassificationOperationBancaireService classificationService
    ) {
        this.compteRepository = compteRepository;
        this.operationRepository = operationRepository;
        this.pieceRapprochementRepository = pieceRapprochementRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.importRepository = importRepository;
        this.rapprochementRepository = rapprochementRepository;
        this.regleRepository = regleRepository;
        this.facturesClient = facturesClient;
        this.tiersClient = tiersClient;
        this.mapper = mapper;
        this.importReleveService = importReleveService;
        this.classificationService = classificationService;
    }

    @Transactional(readOnly = true)
    public DashboardTresorerieReponse dashboard(ContexteRequete contexte) {
        List<CompteBancaire> comptes = compteRepository.findByTenantIdAndSocieteInterneIdAndActifTrueOrderByNomCompte(contexte.tenantId(), contexte.societeCouranteId());
        BigDecimal soldesInitiaux = comptes.stream().map(CompteBancaire::getSoldeInitial).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldeTotal = soldesInitiaux.add(operationRepository.sommeMontants(contexte.tenantId(), contexte.societeCouranteId()));
        return new DashboardTresorerieReponse(
                comptes.size(),
                operationRepository.countByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochement(contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE),
                rapprochementRepository.countByTenantIdAndSocieteInterneIdAndStatut(contexte.tenantId(), contexte.societeCouranteId(), StatutRapprochement.VALIDE),
                soldeTotal,
                operationRepository.sommeCredits(contexte.tenantId(), contexte.societeCouranteId()),
                operationRepository.sommeDebits(contexte.tenantId(), contexte.societeCouranteId())
        );
    }

    @Transactional(readOnly = true)
    public List<CompteBancaireDto> listerComptes(ContexteRequete contexte) {
        return compteRepository.findByTenantIdAndSocieteInterneIdAndActifTrueOrderByNomCompte(contexte.tenantId(), contexte.societeCouranteId())
                .stream().map(compte -> mapper.versReponse(compte, false)).toList();
    }

    @Transactional(readOnly = true)
    public CompteBancaireDto detailCompte(ContexteRequete contexte, Long id) {
        return mapper.versReponse(trouverCompteActif(contexte, id), true);
    }

    public CompteBancaireDto creerCompte(ContexteRequete contexte, CompteBancaireCreationDto demande) {
        CompteBancaire compte = new CompteBancaire();
        compte.setTenantId(contexte.tenantId());
        compte.setSocieteInterneId(contexte.societeCouranteId());
        appliquer(compte, demande);
        return mapper.versReponse(compteRepository.save(compte), true);
    }

    public CompteBancaireDto modifierCompte(ContexteRequete contexte, Long id, CompteBancaireModificationDto demande) {
        CompteBancaire compte = trouverCompte(contexte, id);
        appliquer(compte, demande);
        return mapper.versReponse(compte, true);
    }

    public void desactiverCompte(ContexteRequete contexte, Long id) {
        CompteBancaire compte = trouverCompteActif(contexte, id);
        compte.setActif(false);
        compte.setDateModification(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<OperationBancaireReponse> listerOperations(ContexteRequete contexte) {
        Map<Long, Boolean> autorisationsFactureRapprochement = autorisationsFactureRapprochement(contexte);
        return operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueOrderByDateOperationDescIdDesc(contexte.tenantId(), contexte.societeCouranteId())
                .stream()
                .map(operation -> mapper.versReponse(operation, autorisationFactureRapprochement(operation, autorisationsFactureRapprochement)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<OperationBancaireReponse> listerOperations(
            ContexteRequete contexte,
            Long compteBancaireId,
            LocalDate dateDebut,
            LocalDate dateFin,
            String libelle,
            BigDecimal montantMin,
            BigDecimal montantMax,
            SensOperation sens,
            TypeOperationBancaire typeOperation,
            String tiersReconnu,
            TypeTiersReconnu typeTiersReconnu,
            NatureOperationBancaire natureOperation,
            StatutClassificationOperation statutClassification,
            StatutOperationBancaire statutRapprochement,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        Pageable pageable = pageableOperations(page, size, sortBy, sortDirection);
        Map<Long, Boolean> autorisationsFactureRapprochement = autorisationsFactureRapprochement(contexte);
        return operationRepository.rechercher(
                        contexte.tenantId(), contexte.societeCouranteId(), compteBancaireId, dateDebut, dateFin,
                        libelle == null ? "" : libelle.trim(), montantMin, montantMax, sens, typeOperation,
                        tiersReconnu == null ? "" : tiersReconnu.trim(), typeTiersReconnu, natureOperation, statutClassification,
                        statutRapprochement, pageable)
                .map(operation -> mapper.versReponse(
                        operation,
                        autorisationFactureRapprochement(operation, autorisationsFactureRapprochement)
                ));
    }

    private Pageable pageableOperations(Integer page, Integer size, String sortBy, String sortDirection) {
        int numeroPage = Math.max(0, page == null ? 0 : page);
        int taillePage = size == null ? TAILLE_PAGE_OPERATIONS_DEFAUT : size;
        taillePage = Math.max(1, Math.min(taillePage, TAILLE_PAGE_OPERATIONS_MAX));

        String proprieteTri = CHAMPS_TRI_OPERATIONS.getOrDefault(sortBy, "dateOperation");
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort tri = Sort.by(direction, proprieteTri).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(numeroPage, taillePage, tri);
    }

    @Transactional(readOnly = true)
    public OperationBancaireReponse detailOperation(ContexteRequete contexte, Long id) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        return mapper.versReponse(operation, autorisationFactureRapprochement(operation, autorisationsFactureRapprochement(contexte)));
    }

    public OperationBancaireReponse creerOperation(ContexteRequete contexte, OperationBancaireDemande demande) {
        OperationBancaire operation = new OperationBancaire();
        operation.setTenantId(contexte.tenantId());
        operation.setSocieteInterneId(contexte.societeCouranteId());
        appliquer(contexte, operation, demande);
        return mapper.versReponse(operationRepository.save(operation));
    }

    public OperationBancaireReponse modifierOperation(ContexteRequete contexte, Long id, OperationBancaireDemande demande) {
        OperationBancaire operation = trouverOperation(contexte, id);
        appliquer(contexte, operation, demande);
        return mapper.versReponse(operation);
    }

    public void supprimerOperation(ContexteRequete contexte, Long id) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        operation.setActif(false);
        operation.setDateModification(LocalDateTime.now());
    }

    public OperationBancaireReponse modifierClassificationOperation(ContexteRequete contexte, Long id, ClassificationOperationDemande demande) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        operation.setNatureOperation(demande.natureOperation());
        operation.setStatutClassification(StatutClassificationOperation.CLASSEE_MANUELLE);
        operation.setDateModification(LocalDateTime.now());
        return mapper.versReponse(operation, autorisationFactureRapprochement(operation, autorisationsFactureRapprochement(contexte)));
    }

    public OperationBancaireReponse retraiterOperation(ContexteRequete contexte, Long id) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        classificationService.classerAutomatiquement(operation, reglesClassification(contexte));
        operation.setDateModification(LocalDateTime.now());
        return mapper.versReponse(operation, autorisationFactureRapprochement(operation, autorisationsFactureRapprochement(contexte)));
    }

    public int retraiterOperationsNonRapprochees(ContexteRequete contexte) {
        return calculerNomTiersOperationsNonRapprochees(contexte);
    }

    public int calculerNomTiersOperationsNonRapprochees(ContexteRequete contexte) {
        List<OperationBancaire> operations = operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE);
        List<RegleClassificationOperation> regles = reglesReconnaissanceTiersOperationBancaire(contexte);
        operations.forEach(operation -> {
            ResultatReconnaissanceTiers resultat = classificationService.reconnaitreTiersDepuisRegles(operation.getLibelle(), regles);
            if (resultat.trouve()) {
                operation.setTiersId(resultat.tiersId());
                operation.setTiersReconnu(resultat.nomTiers());
                operation.setTypeTiersReconnu(resultat.typeTiers());
            } else if (resultat.ambiguite()) {
                operation.setStatutClassification(StatutClassificationOperation.A_VERIFIER);
            }
            operation.setDateModification(LocalDateTime.now());
        });
        return operations.size();
    }

    public RapprochementFacturesResultat rapprocherFactures(ContexteRequete contexte) {
        List<String> messages = new ArrayList<>();
        List<OperationBancaire> operations = operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE);
        int rapprochements = 0;
        int propositions = 0;
        int sansMatch = 0;
        for (OperationBancaire operation : operations) {
            ResultatRapprochementOperation resultat = rapprocherFacture(contexte, operation, false);
            rapprochements += resultat.rapprochementsCrees();
            propositions += resultat.propositionsCreees();
            sansMatch += resultat.sansMatch();
            messages.addAll(resultat.messages());
        }
        return new RapprochementFacturesResultat(operations.size(), rapprochements, propositions, sansMatch, messages);
    }

    public RapprochementFacturesResultat rapprocherSalaires(ContexteRequete contexte) {
        List<OperationBancaire> operations = operationRepository
                .findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                        contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE)
                .stream().filter(this::operationEligibleRapprochementSalaire).toList();
        int rapproches = 0;
        int propositions = 0;
        int sansMatch = 0;
        List<String> messages = new ArrayList<>();
        for (OperationBancaire operation : operations) {
            ResultatRapprochementOperation resultat = rapprocherSalaire(contexte, operation, false);
            rapproches += resultat.rapprochementsCrees();
            propositions += resultat.propositionsCreees();
            sansMatch += resultat.sansMatch();
            messages.addAll(resultat.messages());
        }
        return new RapprochementFacturesResultat(operations.size(), rapproches, propositions, sansMatch, messages);
    }

    public RapprochementFacturesResultat rapprocherFacture(ContexteRequete contexte, Long id) {
        ResultatRapprochementOperation resultat = rapprocherFacture(contexte, trouverOperationActive(contexte, id), true);
        return new RapprochementFacturesResultat(1, resultat.rapprochementsCrees(), resultat.propositionsCreees(), resultat.sansMatch(), resultat.messages());
    }

    public RapprochementFacturesResultat rapprocherNotesFrais(ContexteRequete contexte) {
        List<OperationBancaire> operations = operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                        contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE)
                .stream()
                .filter(this::operationEligibleRapprochementNoteFrais)
                .toList();
        int propositions = 0;
        int sansMatch = 0;
        List<String> messages = new ArrayList<>();
        for (OperationBancaire operation : operations) {
            ResultatRapprochementOperation resultat = rapprocherNoteFrais(contexte, operation, false);
            propositions += resultat.propositionsCreees();
            sansMatch += resultat.sansMatch();
            messages.addAll(resultat.messages());
        }
        return new RapprochementFacturesResultat(operations.size(), 0, propositions, sansMatch, messages);
    }

    public RapprochementFacturesResultat rapprocherNoteFrais(ContexteRequete contexte, Long id) {
        ResultatRapprochementOperation resultat = rapprocherNoteFrais(contexte, trouverOperationActive(contexte, id), true);
        return new RapprochementFacturesResultat(1, resultat.rapprochementsCrees(), resultat.propositionsCreees(), resultat.sansMatch(), resultat.messages());
    }

    private ResultatRapprochementOperation rapprocherFacture(ContexteRequete contexte, OperationBancaire operation, boolean individuel) {
        if (!operationEligibleRapprochementFacture(operation)) {
            return new ResultatRapprochementOperation(0, 0, 1, List.of("Operation %s non eligible.".formatted(operation.getId())));
        }
        TypeFactureRapprochement typeFacture = typeFactureRapprochement(operation);
        if (typeFacture == TypeFactureRapprochement.SALAIRE) {
            return rapprocherSalaire(contexte, operation, individuel);
        }
        List<FactureOuverteRapprochementDto> factures = facturesClient.listerFacturesOuvertes(contexte, typeFacture, operation.getTiersId());
        BigDecimal montant = montantOperation(operation);
        MatchFacture meilleur = factures.stream()
                .map(facture -> new MatchFacture(facture, score(operation, facture, montant), differenceMontant(montant, facture.resteAPayer())))
                .filter(match -> match.difference().compareTo(TOLERANCE_MONTANT) <= 0)
                .max(Comparator.comparingInt(MatchFacture::score))
                .orElse(null);

        if (meilleur == null || meilleur.score() < 60) {
            List<FactureOuverteRapprochementDto> combinaison = trouverCombinaison(factures, montant);
            if (!combinaison.isEmpty()) {
                appliquerRapprochementFactures(contexte, operation, typeFacture, combinaison, 90, true);
                return new ResultatRapprochementOperation(combinaison.size(), 0, 0, List.of("Operation %s rapprochee avec %s factures.".formatted(operation.getId(), combinaison.size())));
            }
            return new ResultatRapprochementOperation(0, 0, 1, List.of(individuel ? "Aucune facture ouverte correspondante." : "Operation %s sans match.".formatted(operation.getId())));
        }

        if (meilleur.score() >= 90) {
            appliquerRapprochementFactures(contexte, operation, typeFacture, List.of(meilleur.facture()), meilleur.score(), false);
            return new ResultatRapprochementOperation(1, 0, 0, List.of("Operation %s rapprochee avec %s.".formatted(operation.getId(), meilleur.facture().numeroFacture())));
        }

        creerRapprochementFacture(contexte, operation, typeFacture, meilleur.facture(), meilleur.facture().resteAPayer(), meilleur.score(), StatutRapprochement.PROPOSE, libelleRapprochement(operation, meilleur.facture().numeroFacture()));
        return new ResultatRapprochementOperation(0, 1, 0, List.of("Operation %s proposee avec %s.".formatted(operation.getId(), meilleur.facture().numeroFacture())));
    }

    private ResultatRapprochementOperation rapprocherSalaire(
            ContexteRequete contexte, OperationBancaire operation, boolean individuel) {
        YearMonth periode = periodeSalaireOperation(operation.getDateOperation());
        BigDecimal montant = montantOperation(operation);
        List<FactureOuverteRapprochementDto> salaires = facturesClient.listerSalairesOuverts(
                contexte, operation.getTiersId(), periode.getYear(), periode.getMonthValue(), montant);
        if (salaires.isEmpty()) {
            return new ResultatRapprochementOperation(0, 0, 1, List.of(individuel
                    ? "Aucun salaire valide et ouvert pour %02d/%d.".formatted(periode.getMonthValue(), periode.getYear())
                    : "Operation %s sans salaire ouvert pour %02d/%d.".formatted(
                            operation.getId(), periode.getMonthValue(), periode.getYear())));
        }
        FactureOuverteRapprochementDto exact = salaires.stream()
                .filter(salaire -> differenceMontant(montant, salaire.resteAPayer()).compareTo(TOLERANCE_MONTANT) <= 0)
                .findFirst().orElse(null);
        if (exact != null) {
            String libelle = libelleRapprochementSalaire(operation, periode, exact.id());
            facturesClient.enregistrerPaiement(contexte, TypeFactureRapprochement.SALAIRE, exact.id(),
                    new EnregistrementPaiementFactureDto(montant, operation.getDateOperation(), libelle));
            creerRapprochementSalaire(contexte, operation, exact, periode, montant, StatutRapprochement.VALIDE, libelle);
            operation.setRapprochee(true);
            operation.setStatutRapprochement(StatutOperationBancaire.RAPPROCHEE);
            operation.setSalaireId(exact.id());
            operation.setFactureId(null);
            operation.setTypeFacture(TypeFactureRapprochement.SALAIRE);
            operation.setLibelleRapprochement(libelle);
            operation.setDateRapprochement(LocalDateTime.now());
            operation.setDateModification(LocalDateTime.now());
            return new ResultatRapprochementOperation(1, 0, 0,
                    List.of("Operation %s rapprochee avec le salaire %s.".formatted(operation.getId(), exact.numeroFacture())));
        }
        FactureOuverteRapprochementDto candidat = salaires.get(0);
        if (!rapprochementRepository.existsByOperationBancaireIdAndSalaireIdAndStatutIn(
                operation.getId(), candidat.id(), List.of(StatutRapprochement.PROPOSE, StatutRapprochement.VALIDE))) {
            String libelle = libelleRapprochementSalaire(operation, periode, candidat.id());
            creerRapprochementSalaire(contexte, operation, candidat, periode,
                    montant.min(candidat.resteAPayer()), StatutRapprochement.PROPOSE, libelle);
        }
        return new ResultatRapprochementOperation(0, 1, 0,
                List.of("Montant du salaire different : proposition a verifier pour l'operation %s.".formatted(operation.getId())));
    }

    private ResultatRapprochementOperation rapprocherNoteFrais(ContexteRequete contexte, OperationBancaire operation, boolean individuel) {
        if (!operationEligibleRapprochementNoteFrais(operation)) {
            return new ResultatRapprochementOperation(0, 0, 1, List.of("Operation %s non eligible au rapprochement note de frais.".formatted(operation.getId())));
        }
        BigDecimal montant = montantOperation(operation);
        FactureOuverteRapprochementDto facture = facturesClient.listerFacturesAchatNotesFraisOuvertes(contexte, operation.getTiersId(), montant)
                .stream()
                .filter(candidate -> differenceMontant(montant, candidate.resteAPayer()).compareTo(TOLERANCE_MONTANT) <= 0)
                .findFirst()
                .orElse(null);
        if (facture == null) {
            return new ResultatRapprochementOperation(0, 0, 1, List.of(individuel
                    ? "Aucune facture d'achat frais generaux ouverte ne correspond a cette note de frais."
                    : "Operation %s note de frais sans match.".formatted(operation.getId())));
        }
        boolean doublon = rapprochementRepository.existsByOperationBancaireIdAndFactureIdAndStatutIn(
                operation.getId(), facture.id(), List.of(StatutRapprochement.PROPOSE, StatutRapprochement.VALIDE));
        if (doublon) {
            return new ResultatRapprochementOperation(0, 0, 1, List.of("Operation %s deja proposee avec la facture %s.".formatted(operation.getId(), facture.numeroFacture())));
        }
        String libelle = libelleRapprochementNoteFrais(operation, facture.numeroFacture());
        creerRapprochementFacture(contexte, operation, TypeFactureRapprochement.ACHAT, facture, facture.resteAPayer(), 100, StatutRapprochement.PROPOSE, libelle);
        return new ResultatRapprochementOperation(0, 1, 0, List.of("Operation %s proposee en note de frais avec %s.".formatted(operation.getId(), facture.numeroFacture())));
    }

    public OperationBancaireReponse creerPieceRapprochement(ContexteRequete contexte, Long id) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        verifierOperationEligiblePieceRapprochement(operation);
        if (operationEligibleRapprochementSalaire(operation)) {
            YearMonth periode = periodeSalaireOperation(operation.getDateOperation());
            if (!facturesClient.listerSalairesOuverts(contexte, operation.getTiersId(),
                    periode.getYear(), periode.getMonthValue(), montantOperation(operation)).isEmpty()) {
                throw new RegleMetierException("SALAIRE_REEL_EXISTANT",
                        "Un salaire reel existe pour ce salarie et cette periode ; il doit etre rapproche en priorite.");
            }
        }
        TiersTresorerieDto tiers = tiersClient.listerTiersActifs(contexte)
                .stream()
                .filter(candidat -> operation.getTiersId().equals(candidat.id()))
                .findFirst()
                .orElseThrow(() -> new RegleMetierException("TIERS_RECONNU_INTROUVABLE",
                        "Le tiers reconnu de l'operation bancaire est introuvable dans le referentiel Tiers."));
        if (!Boolean.TRUE.equals(tiers.pieceRapprochementAutorisee())) {
            throw new CreationPieceInterditeException();
        }

        TypePieceRapprochement typePiece = determinerTypePiece(operation);
        if (typePiece != TypePieceRapprochement.FACTURE_ACHAT) {
            return creerPieceTresorerie(contexte, operation, tiers, typePiece);
        }

        BigDecimal montant = montantOperation(operation);
        FactureAchatRapprochementReponse facture = facturesClient.creerFactureAchatDepuisOperationBancaire(
                contexte,
                new CreationFactureAchatDepuisOperationBancaireDemande(
                        operation.getId(),
                        tiers.id(),
                        tiers.libelle(),
                        operation.getDateOperation(),
                        operation.getLibelle(),
                        montant
                )
        );
        if (facture == null || facture.factureId() == null) {
            throw new RegleMetierException("FACTURE_RAPPROCHEMENT_NON_CREEE",
                    "La facture de rapprochement n'a pas ete creee par le module Factures.");
        }
        String numeroFacture = facture.numeroFacture() == null || facture.numeroFacture().isBlank()
                ? String.valueOf(facture.factureId())
                : facture.numeroFacture();
        String libelleRapprochement = "RAP-%s-%s-OP-%s".formatted(
                LocalDate.now().format(DATE_RAPPROCHEMENT_FORMAT),
                numeroFacture,
                operation.getId()
        );
        facturesClient.mettreAJourLibelleRapprochement(
                contexte,
                facture.factureId(),
                new MiseAJourLibelleRapprochementDemande(libelleRapprochement)
        );

        LocalDateTime maintenant = LocalDateTime.now();
        operation.setRapprochee(true);
        operation.setStatutRapprochement(StatutOperationBancaire.RAPPROCHEE);
        operation.setFactureId(facture.factureId());
        operation.setFactureRapprochementId(facture.factureId());
        operation.setTypeFacture(TypeFactureRapprochement.ACHAT);
        operation.setLibelleRapprochement(libelleRapprochement);
        operation.setDateRapprochement(maintenant);
        operation.setDateModification(maintenant);

        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId());
        rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setOperationBancaire(operation);
        rapprochement.setFactureId(facture.factureId());
        rapprochement.setTypeFacture(TypeFactureRapprochement.ACHAT);
        rapprochement.setTypePiece(TypePieceRapprochement.FACTURE_ACHAT);
        rapprochement.setDocumentAssocieNumero(numeroFacture);
        rapprochement.setDocumentAssocieLibelle(operation.getLibelle());
        rapprochement.setMontantRapproche(montant);
        rapprochement.setEcart(BigDecimal.ZERO);
        rapprochement.setStatut(StatutRapprochement.VALIDE);
        rapprochement.setModeRapprochement("FACTURE_RAPPROCHEMENT");
        rapprochement.setLibelleRapprochement(libelleRapprochement);
        rapprochement.setDateRapprochement(maintenant);
        rapprochement.setUtilisateurId(contexte.utilisateurId());
        rapprochementRepository.save(rapprochement);

        return mapper.versReponse(operation, tiers.pieceRapprochementAutorisee());
    }

    /** Alias conservé pour les anciens consommateurs. */
    public OperationBancaireReponse creerFactureRapprochement(ContexteRequete contexte, Long id) {
        return creerPieceRapprochement(contexte, id);
    }

    private OperationBancaireReponse creerPieceTresorerie(
            ContexteRequete contexte, OperationBancaire operation, TiersTresorerieDto tiers, TypePieceRapprochement typePiece
    ) {
        if (pieceRapprochementRepository.existsByOperationBancaireId(operation.getId())) {
            throw new RegleMetierException("PIECE_RAPPROCHEMENT_DEJA_CREEE", "Une piece de rapprochement existe deja pour cette operation.");
        }
        int annee = operation.getDateOperation().getYear();
        Long sequence = jdbcTemplate.queryForObject("""
                insert into compteur_piece_rapprochement(tenant_id, societe_interne_id, annee, dernier_numero)
                values (?, ?, ?, 1)
                on conflict (tenant_id, societe_interne_id, annee)
                do update set dernier_numero = compteur_piece_rapprochement.dernier_numero + 1
                returning dernier_numero
                """, Long.class, contexte.tenantId(), contexte.societeCouranteId(), annee);
        String numeroPiece = "PR-%d-%d-%06d".formatted(annee, contexte.societeCouranteId(), sequence);
        String libelle = "RAP-%s-%s-OP-%s".formatted(
                LocalDate.now().format(DATE_RAPPROCHEMENT_FORMAT), numeroPiece, operation.getId());
        BigDecimal montant = montantOperation(operation);

        PieceRapprochement piece = new PieceRapprochement();
        piece.setTenantId(contexte.tenantId()); piece.setSocieteInterneId(contexte.societeCouranteId());
        piece.setOperationBancaireId(operation.getId()); piece.setTiersId(tiers.id()); piece.setTypePiece(typePiece);
        piece.setNumeroPiece(numeroPiece); piece.setDatePiece(operation.getDateOperation()); piece.setLibelle(operation.getLibelle());
        piece.setMontantHt(montant); piece.setMontantTva(BigDecimal.ZERO); piece.setMontantTtc(montant);
        piece.setLibelleRapprochement(libelle);
        pieceRapprochementRepository.save(piece);

        LocalDateTime maintenant = LocalDateTime.now();
        operation.setPieceRapprochementId(piece.getId()); operation.setRapprochee(true);
        operation.setStatutRapprochement(StatutOperationBancaire.RAPPROCHEE);
        operation.setLibelleRapprochement(libelle); operation.setDateRapprochement(maintenant); operation.setDateModification(maintenant);

        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId()); rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setOperationBancaire(operation); rapprochement.setPieceRapprochementId(piece.getId());
        rapprochement.setTypePiece(typePiece); rapprochement.setDocumentAssocieNumero(numeroPiece);
        rapprochement.setDocumentAssocieLibelle(operation.getLibelle());
        rapprochement.setMontantRapproche(montant); rapprochement.setEcart(BigDecimal.ZERO);
        rapprochement.setStatut(StatutRapprochement.VALIDE); rapprochement.setModeRapprochement("PIECE_TRESORERIE");
        rapprochement.setLibelleRapprochement(libelle); rapprochement.setDateRapprochement(maintenant);
        rapprochement.setDateValidation(maintenant); rapprochement.setUtilisateurId(contexte.utilisateurId());
        rapprochement.setUtilisateurValidationId(contexte.utilisateurId()); rapprochementRepository.save(rapprochement);
        return mapper.versReponse(operation, true);
    }

    private TypePieceRapprochement determinerTypePiece(OperationBancaire operation) {
        return switch (operation.getNatureOperation()) {
            case SALAIRE -> TypePieceRapprochement.SALAIRE;
            case NOTE_DE_FRAIS -> TypePieceRapprochement.NOTE_DE_FRAIS;
            case TVA, URSSAF, IMPOT -> TypePieceRapprochement.IMPOT_TAXE;
            case APPORT_CAPITAL -> TypePieceRapprochement.APPORT_CAPITAL;
            case COMPTE_COURANT -> TypePieceRapprochement.COMPTE_COURANT_ASSOCIE;
            case EMPRUNT -> TypePieceRapprochement.EMPRUNT;
            case VIREMENT_INTERNE -> TypePieceRapprochement.VIREMENT_INTERNE;
            case REMBOURSEMENT -> TypePieceRapprochement.REMBOURSEMENT;
            default -> {
                if (operation.getNatureOperation() == NatureOperationBancaire.FACTURE_FOURNISSEUR
                        && operation.getSens() == SensOperation.DEBIT
                        && (operation.getTypeTiersReconnu() == TypeTiersReconnu.FOURNISSEUR || operation.getTypeTiersReconnu() == TypeTiersReconnu.FOURNISSEUR_SERVICE)) {
                    yield TypePieceRapprochement.FACTURE_ACHAT;
                }
                if (operation.getNatureOperation() == NatureOperationBancaire.FACTURE_CLIENT
                        && operation.getSens() == SensOperation.CREDIT && operation.getTypeTiersReconnu() == TypeTiersReconnu.CLIENT) {
                    yield TypePieceRapprochement.FACTURE_VENTE;
                }
                yield TypePieceRapprochement.PIECE_TRESORERIE;
            }
        };
    }

    public int retraiterImport(ContexteRequete contexte, Long importId) {
        ImportReleveBancaire importReleve = trouverImport(contexte, importId);
        List<OperationBancaire> operations = operationRepository.findByTenantIdAndSocieteInterneIdAndImportReleveBancaireIdAndActifTrueOrderByDateOperationDescIdDesc(
                contexte.tenantId(), contexte.societeCouranteId(), importReleve.getId());
        List<RegleClassificationOperation> regles = reglesClassification(contexte);
        operations.forEach(operation -> {
            classificationService.classerAutomatiquement(operation, regles);
            operation.setDateModification(LocalDateTime.now());
        });
        return operations.size();
    }

    @Transactional(readOnly = true)
    public List<ImportReleveReponse> listerImports(ContexteRequete contexte) {
        return importRepository.findByTenantIdAndSocieteInterneIdOrderByDateImportDesc(contexte.tenantId(), contexte.societeCouranteId())
                .stream().map(mapper::versReponse).toList();
    }

    public ImportReleveReponse creerImport(ContexteRequete contexte, ImportReleveDemande demande) {
        ImportReleveBancaire importReleve = new ImportReleveBancaire();
        importReleve.setTenantId(contexte.tenantId());
        importReleve.setSocieteInterneId(contexte.societeCouranteId());
        appliquer(contexte, importReleve, demande);
        return mapper.versReponse(importRepository.save(importReleve));
    }

    @Transactional(readOnly = true)
    public ImportReleveReponse detailImport(ContexteRequete contexte, Long id) {
        return mapper.versReponse(trouverImport(contexte, id));
    }

    @Transactional(readOnly = true)
    public List<RapprochementBancaireReponse> listerRapprochements(ContexteRequete contexte) {
        return rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(contexte.tenantId(), contexte.societeCouranteId())
                .stream().map(mapper::versReponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PieceRapprochementReponse> listerPiecesRapprochement(ContexteRequete contexte) {
        Map<Long, String> tiers = tiersClient.listerTiersActifs(contexte).stream()
                .collect(Collectors.toMap(TiersTresorerieDto::id, TiersTresorerieDto::libelle, (a, b) -> a));
        return pieceRapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDatePieceDescIdDesc(
                contexte.tenantId(), contexte.societeCouranteId()).stream()
                .map(piece -> new PieceRapprochementReponse(piece.getId(), piece.getNumeroPiece(), piece.getTypePiece(),
                        piece.getTiersId(), tiers.getOrDefault(piece.getTiersId(), "Tiers " + piece.getTiersId()),
                        piece.getDatePiece(), piece.getLibelle(), piece.getMontantTtc(), piece.getStatut(),
                        piece.getOperationBancaireId(), piece.getLibelleRapprochement()))
                .toList();
    }

    public RapprochementBancaireReponse creerRapprochement(ContexteRequete contexte, RapprochementBancaireDemande demande) {
        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId());
        rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setUtilisateurId(contexte.utilisateurId());
        appliquer(contexte, rapprochement, demande);
        return mapper.versReponse(rapprochementRepository.save(rapprochement));
    }

    @Transactional(readOnly = true)
    public List<FactureOuverteRapprochementDto> listerFacturesCandidates(ContexteRequete contexte, Long operationId) {
        OperationBancaire operation = trouverOperationActive(contexte, operationId);
        TypeFactureRapprochement typeFacture = typeFactureManuel(operation);
        if (operation.getTiersId() == null) {
            throw new RegleMetierException("TIERS_OPERATION_OBLIGATOIRE", "Un tiers reconnu est obligatoire pour rechercher les factures candidates.");
        }
        Set<Long> facturesDejaUtilisees = rapprochementRepository
                .findByOperationBancaireIdAndStatut(operationId, StatutRapprochement.VALIDE).stream()
                .map(RapprochementBancaire::getFactureId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return facturesClient.listerFacturesOuvertes(contexte, typeFacture, operation.getTiersId()).stream()
                .filter(facture -> facture.resteAPayer() != null && facture.resteAPayer().compareTo(BigDecimal.ZERO) > 0)
                .filter(facture -> !facturesDejaUtilisees.contains(facture.id()))
                .toList();
    }

    public RapprochementManuelResultat rapprocherManuellement(
            ContexteRequete contexte,
            Long operationId,
            RapprochementManuelDemande demande
    ) {
        OperationBancaire operation = trouverOperationActive(contexte, operationId);
        if (operation.getStatutRapprochement() == StatutOperationBancaire.RAPPROCHEE) {
            throw new RegleMetierException("OPERATION_DEJA_RAPPROCHEE", "Cette operation bancaire est deja totalement rapprochee.");
        }
        TypeFactureRapprochement typeAttendu = typeFactureManuel(operation);
        List<LigneRapprochementManuelDemande> lignes = demande.lignes();
        if (lignes.stream().map(LigneRapprochementManuelDemande::factureId).distinct().count() != lignes.size()) {
            throw new RegleMetierException("FACTURE_DUPLIQUEE", "Une facture ne peut etre selectionnee qu'une fois.");
        }
        if (lignes.stream().anyMatch(ligne -> ligne.typeFacture() != typeAttendu)) {
            throw new RegleMetierException("TYPE_FACTURE_INVALIDE", "Le type de facture ne correspond pas au sens et au tiers de l'operation.");
        }

        Map<Long, FactureOuverteRapprochementDto> candidates = listerFacturesCandidates(contexte, operationId).stream()
                .collect(Collectors.toMap(FactureOuverteRapprochementDto::id, Function.identity()));
        BigDecimal ajout = BigDecimal.ZERO;
        for (LigneRapprochementManuelDemande ligne : lignes) {
            FactureOuverteRapprochementDto facture = candidates.get(ligne.factureId());
            if (facture == null) {
                throw new RegleMetierException("FACTURE_NON_CANDIDATE", "La facture %s n'est plus disponible pour ce rapprochement.".formatted(ligne.factureId()));
            }
            if (ligne.montantRapproche().compareTo(facture.resteAPayer().add(TOLERANCE_MONTANT)) > 0) {
                throw new RegleMetierException("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant rapproche depasse le reste a payer de la facture %s.".formatted(ligne.factureId()));
            }
            ajout = ajout.add(ligne.montantRapproche());
        }
        BigDecimal dejaRapproche = rapprochementRepository.sommeMontantParOperationEtStatut(operationId, StatutRapprochement.VALIDE);
        BigDecimal montantOperation = montantOperation(operation);
        if (dejaRapproche.add(ajout).compareTo(montantOperation.add(TOLERANCE_MONTANT)) > 0) {
            throw new RegleMetierException("MONTANT_SUPERIEUR_OPERATION", "Le total rapproche depasse le montant de l'operation.");
        }

        LocalDateTime maintenant = LocalDateTime.now();
        for (LigneRapprochementManuelDemande ligne : lignes) {
            FactureOuverteRapprochementDto facture = candidates.get(ligne.factureId());
            String libelleLigne = libelleRapprochement(operation, facture.numeroFacture());
            facturesClient.enregistrerPaiement(contexte, typeAttendu, facture.id(),
                    new EnregistrementPaiementFactureDto(ligne.montantRapproche(), operation.getDateOperation(), libelleLigne));
            creerRapprochementFacture(contexte, operation, typeAttendu, facture, ligne.montantRapproche(), 100,
                    StatutRapprochement.VALIDE, libelleLigne, "MANUEL");
        }
        BigDecimal total = dejaRapproche.add(ajout);
        BigDecimal reste = montantOperation.subtract(total);
        if (reste.abs().compareTo(TOLERANCE_MONTANT) <= 0) reste = BigDecimal.ZERO;
        boolean complet = reste.signum() == 0;
        String libelleOperation = complet
                ? "RAP-%s-GLOBAL-OP-%s".formatted(operation.getDateOperation().format(DATE_RAPPROCHEMENT_FORMAT), operation.getId())
                : "RAP-%s-PARTIEL-OP-%s".formatted(operation.getDateOperation().format(DATE_RAPPROCHEMENT_FORMAT), operation.getId());
        operation.setRapprochee(complet);
        operation.setStatutRapprochement(complet ? StatutOperationBancaire.RAPPROCHEE : StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
        operation.setLibelleRapprochement(libelleOperation);
        operation.setDateRapprochement(maintenant);
        operation.setDateModification(maintenant);
        return new RapprochementManuelResultat(operationId, montantOperation, total, reste, operation.getStatutRapprochement());
    }

    private TypeFactureRapprochement typeFactureManuel(OperationBancaire operation) {
        if (operation.getSens() == SensOperation.DEBIT
                && (operation.getTypeTiersReconnu() == TypeTiersReconnu.FOURNISSEUR
                    || operation.getTypeTiersReconnu() == TypeTiersReconnu.FOURNISSEUR_SERVICE)) {
            return TypeFactureRapprochement.ACHAT;
        }
        if (operation.getSens() == SensOperation.CREDIT && operation.getTypeTiersReconnu() == TypeTiersReconnu.CLIENT) {
            return TypeFactureRapprochement.VENTE;
        }
        throw new RegleMetierException("OPERATION_NON_ELIGIBLE", "Le rapprochement manuel exige un fournisseur pour un debit ou un client pour un credit.");
    }

    public RapprochementBancaireReponse validerRapprochementPropose(ContexteRequete contexte, Long id) {
        RapprochementBancaire rapprochement = trouverRapprochement(contexte, id);
        validerProposition(contexte, rapprochement);
        return mapper.versReponse(rapprochement);
    }

    public RapprochementBancaireReponse annulerRapprochementPropose(ContexteRequete contexte, Long id) {
        RapprochementBancaire rapprochement = trouverRapprochement(contexte, id);
        annulerProposition(contexte, rapprochement, null);
        return mapper.versReponse(rapprochement);
    }

    public RapprochementPropositionsResultat validerRapprochementsProposes(ContexteRequete contexte) {
        List<RapprochementBancaire> propositions = rapprochementRepository.findByTenantIdAndSocieteInterneIdAndStatutOrderByDateRapprochementAscIdAsc(
                contexte.tenantId(), contexte.societeCouranteId(), StatutRapprochement.PROPOSE);
        List<String> messages = new ArrayList<>();
        int validees = 0;
        int erreurs = 0;
        for (RapprochementBancaire proposition : propositions) {
            try {
                validerProposition(contexte, proposition);
                validees++;
            } catch (RuntimeException exception) {
                erreurs++;
                messages.add("Proposition %s non validee : %s".formatted(proposition.getId(), exception.getMessage()));
            }
        }
        return new RapprochementPropositionsResultat(propositions.size(), validees, 0, erreurs, messages);
    }

    public RapprochementPropositionsResultat annulerRapprochementsProposes(ContexteRequete contexte) {
        List<RapprochementBancaire> propositions = rapprochementRepository.findByTenantIdAndSocieteInterneIdAndStatutOrderByDateRapprochementAscIdAsc(
                contexte.tenantId(), contexte.societeCouranteId(), StatutRapprochement.PROPOSE);
        List<String> messages = new ArrayList<>();
        int annulees = 0;
        int erreurs = 0;
        for (RapprochementBancaire proposition : propositions) {
            try {
                annulerProposition(contexte, proposition, null);
                annulees++;
            } catch (RuntimeException exception) {
                erreurs++;
                messages.add("Proposition %s non annulee : %s".formatted(proposition.getId(), exception.getMessage()));
            }
        }
        return new RapprochementPropositionsResultat(propositions.size(), 0, annulees, erreurs, messages);
    }

    @Transactional(readOnly = true)
    public List<RegleRapprochementReponse> listerRegles(ContexteRequete contexte) {
        return regleRepository.findByTenantIdAndSocieteInterneIdOrderByPrioriteAscLibelleAsc(contexte.tenantId(), contexte.societeCouranteId())
                .stream().map(mapper::versReponse).toList();
    }

    public RegleRapprochementReponse creerRegle(ContexteRequete contexte, RegleRapprochementDemande demande) {
        RegleRapprochementBancaire regle = new RegleRapprochementBancaire();
        regle.setTenantId(contexte.tenantId());
        regle.setSocieteInterneId(contexte.societeCouranteId());
        appliquer(regle, demande);
        return mapper.versReponse(regleRepository.save(regle));
    }

    public RegleRapprochementReponse modifierRegle(ContexteRequete contexte, Long id, RegleRapprochementDemande demande) {
        RegleRapprochementBancaire regle = regleRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Regle introuvable."));
        appliquer(regle, demande);
        return mapper.versReponse(regle);
    }

    @Transactional(readOnly = true)
    public PageEcheancesReponse<FactureEcheanceReponse> listerFacturesEcheances(ContexteRequete contexte, String queryString) {
        return facturesClient.listerEcheances(contexte, queryString);
    }

    @Transactional(readOnly = true)
    public KpiEcheancesReponse kpisFacturesEcheances(ContexteRequete contexte) {
        return facturesClient.kpisEcheances(contexte);
    }

    @Transactional(readOnly = true)
    public BalanceAgeeReponse balanceAgee(ContexteRequete contexte) {
        return facturesClient.balanceAgee(contexte);
    }

    private void appliquer(CompteBancaire compte, CompteBancaireCreationDto demande) {
        compte.setNomCompte(demande.nomCompte().trim());
        compte.setBanque(demande.banque().trim());
        compte.setIban(normaliserIban(demande.iban()));
        compte.setBic(normaliserTexte(demande.bic()));
        compte.setDevise(demande.devise() == null || demande.devise().isBlank() ? "EUR" : demande.devise().trim().toUpperCase());
        compte.setSoldeInitial(nulZero(demande.soldeInitial()));
        compte.setDateSoldeInitial(demande.dateSoldeInitial());
        compte.setSoldeActuel(nulZero(demande.soldeInitial()));
        compte.setActif(demande.actif() == null || demande.actif());
        compte.setDateModification(LocalDateTime.now());
    }

    private void appliquer(CompteBancaire compte, CompteBancaireModificationDto demande) {
        compte.setNomCompte(demande.nomCompte().trim());
        compte.setBanque(demande.banque().trim());
        compte.setIban(normaliserIban(demande.iban()));
        compte.setBic(normaliserTexte(demande.bic()));
        compte.setDevise(demande.devise() == null || demande.devise().isBlank() ? "EUR" : demande.devise().trim().toUpperCase());
        compte.setSoldeInitial(nulZero(demande.soldeInitial()));
        compte.setDateSoldeInitial(demande.dateSoldeInitial());
        compte.setSoldeActuel(nulZero(demande.soldeInitial()));
        compte.setActif(demande.actif() == null || demande.actif());
        compte.setDateModification(LocalDateTime.now());
    }

    private void appliquer(ContexteRequete contexte, OperationBancaire operation, OperationBancaireDemande demande) {
        operation.setCompteBancaire(trouverCompte(contexte, demande.compteBancaireId()));
        operation.setImportReleveBancaire(demande.importReleveBancaireId() == null ? null : trouverImport(contexte, demande.importReleveBancaireId()));
        operation.setDateOperation(demande.dateOperation());
        operation.setDateValeur(demande.dateValeur());
        operation.setLibelle(demande.libelle());
        operation.setReferenceBancaire(demande.referenceBancaire());
        operation.setMontant(nulZero(demande.montant()));
        operation.setSens(demande.sens());
        operation.setDebit(demande.debit() == null ? (demande.sens() == SensOperation.DEBIT ? nulZero(demande.montant()).abs() : BigDecimal.ZERO) : demande.debit().abs());
        operation.setCredit(demande.credit() == null ? (demande.sens() == SensOperation.CREDIT ? nulZero(demande.montant()).abs() : BigDecimal.ZERO) : demande.credit().abs());
        operation.setTypeOperation(demande.typeOperation() == null ? importReleveService.deduireTypeOperation(demande.libelle(), demande.sens()) : demande.typeOperation());
        operation.setTiersReconnu(normaliserTexteLibre(demande.tiersReconnu()));
        operation.setTypeTiersReconnu(demande.typeTiersReconnu());
        if (demande.natureOperation() == null) {
            classificationService.classerAutomatiquement(operation);
        } else {
            operation.setNatureOperation(demande.natureOperation());
            operation.setStatutClassification(demande.statutClassification() == null ? StatutClassificationOperation.CLASSEE_MANUELLE : demande.statutClassification());
        }
        operation.setStatutRapprochement(demande.statutRapprochement() == null ? StatutOperationBancaire.NON_RAPPROCHEE : demande.statutRapprochement());
        operation.setRapprochee(operation.getStatutRapprochement() == StatutOperationBancaire.RAPPROCHEE);
        operation.setTiersId(demande.tiersId());
        operation.setFactureId(demande.factureId());
        operation.setTypeFacture(demande.typeFacture());
        operation.setHashOperation(importReleveService.hash(contexte, operation.getCompteBancaire().getId(), new LigneReleveBancaire(
                0,
                operation.getDateOperation(),
                operation.getDateValeur(),
                operation.getLibelle(),
                operation.getReferenceBancaire(),
                operation.getDebit(),
                operation.getCredit(),
                operation.getMontant()
        )));
        operation.setDateModification(LocalDateTime.now());
    }

    private void appliquer(ContexteRequete contexte, ImportReleveBancaire importReleve, ImportReleveDemande demande) {
        importReleve.setCompteBancaire(trouverCompte(contexte, demande.compteBancaireId()));
        importReleve.setNomFichier(demande.nomFichier());
        importReleve.setTypeFichier(demande.typeFichier() == null ? importReleve.getTypeFichier() : demande.typeFichier());
        importReleve.setStatutImport(demande.statutImport() == null ? importReleve.getStatutImport() : demande.statutImport());
        importReleve.setNombreLignes(demande.nombreLignes() == null ? 0 : demande.nombreLignes());
        importReleve.setNombreLignesImportees(demande.nombreLignesImportees() == null ? 0 : demande.nombreLignesImportees());
        importReleve.setNombreLignesRejetees(demande.nombreLignesRejetees() == null ? 0 : demande.nombreLignesRejetees());
        importReleve.setTotalDebit(nulZero(demande.totalDebit()));
        importReleve.setTotalCredit(nulZero(demande.totalCredit()));
        importReleve.setMessageErreur(demande.messageErreur());
    }

    private void appliquer(ContexteRequete contexte, RapprochementBancaire rapprochement, RapprochementBancaireDemande demande) {
        rapprochement.setOperationBancaire(trouverOperation(contexte, demande.operationBancaireId()));
        rapprochement.setFactureId(demande.factureId());
        rapprochement.setTypeFacture(demande.typeFacture());
        rapprochement.setMontantRapproche(nulZero(demande.montantRapproche()));
        rapprochement.setEcart(nulZero(demande.ecart()));
        rapprochement.setStatut(demande.statut() == null ? StatutRapprochement.PROPOSE : demande.statut());
        rapprochement.setCommentaire(demande.commentaire());
        if (rapprochement.getStatut() == StatutRapprochement.PROPOSE && rapprochement.getDateProposition() == null) {
            rapprochement.setDateProposition(LocalDateTime.now());
        }
    }

    private void appliquer(RegleRapprochementBancaire regle, RegleRapprochementDemande demande) {
        regle.setLibelle(demande.libelle());
        regle.setMotif(demande.motif());
        regle.setSens(demande.sens());
        regle.setTiersId(demande.tiersId());
        regle.setPriorite(demande.priorite() == null ? 100 : demande.priorite());
        regle.setActive(demande.active() == null || demande.active());
        regle.setDateModification(LocalDateTime.now());
    }

    private List<RegleClassificationOperation> reglesClassification(ContexteRequete contexte) {
        List<RegleClassificationOperation> reglesSalaries = tiersClient.listerSalaries(contexte)
                .stream()
                .filter(tiers -> tiers.roles() == null || tiers.roles().contains("SALARIE"))
                .map(this::versRegleClassificationSalarie)
                .toList();
        List<RegleClassificationOperation> reglesLocales = regleRepository
                .findByTenantIdAndSocieteInterneIdAndActiveTrueOrderByPrioriteDescLibelleAsc(contexte.tenantId(), contexte.societeCouranteId())
                .stream()
                .map(regle -> new RegleClassificationOperation(
                        regle.getTiersId(),
                        regle.getLibelle(),
                        TypeTiersReconnu.FOURNISSEUR,
                        regle.getMotif(),
                        "CONTIENT",
                        regle.getPriorite() == null ? 100 : regle.getPriorite(),
                        regle.isActive()
                ))
                .toList();
        List<RegleClassificationOperation> reglesFournisseurs = facturesClient.listerReglesRapprochementFournisseur(contexte)
                .stream()
                .filter(regle -> regle.actif() == null || regle.actif())
                .map(this::versRegleClassification)
                .toList();
        return java.util.stream.Stream.of(reglesSalaries, reglesLocales, reglesFournisseurs)
                .flatMap(List::stream)
                .sorted(java.util.Comparator.comparing(RegleClassificationOperation::priorite).reversed())
                .toList();
    }

    private List<RegleClassificationOperation> reglesReconnaissanceTiersOperationBancaire(ContexteRequete contexte) {
        Map<Long, TiersTresorerieDto> tiersActifsParId = tiersClient.listerTiersActifs(contexte)
                .stream()
                .filter(tiers -> tiers.id() != null)
                .filter(tiers -> tiers.actif() == null || tiers.actif())
                .collect(Collectors.toMap(TiersTresorerieDto::id, Function.identity(), (premier, second) -> premier));
        return facturesClient.listerReglesRapprochementFournisseur(contexte)
                .stream()
                .filter(regle -> regle.actif() == null || regle.actif())
                .filter(regle -> regle.typeRegle() == null || "OPERATION_BANCAIRE".equals(regle.typeRegle()))
                .filter(regle -> regle.tiersId() != null && tiersActifsParId.containsKey(regle.tiersId()))
                .map(regle -> versRegleReconnaissanceTiers(regle, tiersActifsParId.get(regle.tiersId())))
                .toList();
    }

    private RegleClassificationOperation versRegleReconnaissanceTiers(
            RegleRapprochementFournisseurFacturesDto regle,
            TiersTresorerieDto tiers
    ) {
        return new RegleClassificationOperation(
                tiers.id(),
                normaliserNomTiersReconnu(tiers.libelle(), regle.libelle()),
                typeTiersReconnu(tiers.roles()),
                regle.motif(),
                regle.typeMotif() == null ? "CONTIENT" : regle.typeMotif(),
                regle.scorePriorite() == null ? 100 : regle.scorePriorite(),
                true,
                normaliserTexteLibre(tiers.nom()),
                normaliserTexteLibre(tiers.prenom())
        );
    }

    private TypeTiersReconnu typeTiersReconnu(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return TypeTiersReconnu.AUTRE;
        }
        if (roles.contains("SALARIE")) {
            return TypeTiersReconnu.SALARIE;
        }
        if (roles.contains("CLIENT")) {
            return TypeTiersReconnu.CLIENT;
        }
        if (roles.contains("FOURNISSEUR_SERVICES")) {
            return TypeTiersReconnu.FOURNISSEUR_SERVICE;
        }
        if (roles.contains("FOURNISSEUR_GENERAL")) {
            return TypeTiersReconnu.FOURNISSEUR;
        }
        if (roles.contains("ORGANISME_SOCIAL") || roles.contains("ORGANISME_FISCAL")) {
            return TypeTiersReconnu.ADMINISTRATION;
        }
        return TypeTiersReconnu.AUTRE;
    }

    private RegleClassificationOperation versRegleClassificationSalarie(TiersTresorerieDto tiers) {
        String libelle = normaliserNomTiersReconnu(tiers.libelle(), tiers.nomNormalise());
        return new RegleClassificationOperation(
                tiers.id(),
                libelle,
                TypeTiersReconnu.SALARIE,
                libelle,
                "CONTIENT",
                1_000,
                tiers.actif() == null || tiers.actif(),
                normaliserTexteLibre(tiers.nom()),
                normaliserTexteLibre(tiers.prenom())
        );
    }

    private RegleClassificationOperation versRegleClassification(RegleRapprochementFournisseurFacturesDto regle) {
        return new RegleClassificationOperation(
                regle.tiersId(),
                normaliserNomTiersReconnu(regle.libelle(), regle.motif()),
                TypeTiersReconnu.FOURNISSEUR,
                regle.motif(),
                regle.typeMotif() == null ? "CONTIENT" : regle.typeMotif(),
                regle.scorePriorite() == null ? 100 : regle.scorePriorite(),
                regle.actif() == null || regle.actif()
        );
    }

    private String normaliserNomTiersReconnu(String libelle, String motif) {
        String valeur = normaliserTexteLibre(libelle);
        if (valeur != null) {
            return valeur;
        }
        return normaliserTexteLibre(motif);
    }

    private CompteBancaire trouverCompte(ContexteRequete contexte, Long id) {
        return compteRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Compte bancaire introuvable."));
    }

    private boolean operationEligibleRapprochementFacture(OperationBancaire operation) {
        if (operation.isRapprochee() || operation.getStatutRapprochement() != StatutOperationBancaire.NON_RAPPROCHEE || operation.getTiersId() == null) {
            return false;
        }
        return (operation.getSens() == SensOperation.CREDIT && operation.getNatureOperation() == NatureOperationBancaire.FACTURE_CLIENT)
                || (operation.getSens() == SensOperation.DEBIT && operation.getNatureOperation() == NatureOperationBancaire.FACTURE_FOURNISSEUR)
                || (operation.getSens() == SensOperation.DEBIT
                    && operation.getNatureOperation() == NatureOperationBancaire.SALAIRE
                    && operation.getTypeTiersReconnu() == TypeTiersReconnu.SALARIE);
    }

    private boolean operationEligibleRapprochementSalaire(OperationBancaire operation) {
        return !operation.isRapprochee()
                && operation.getStatutRapprochement() == StatutOperationBancaire.NON_RAPPROCHEE
                && operation.getSens() == SensOperation.DEBIT
                && operation.getNatureOperation() == NatureOperationBancaire.SALAIRE
                && operation.getTypeTiersReconnu() == TypeTiersReconnu.SALARIE
                && operation.getTiersId() != null;
    }

    private TypeFactureRapprochement typeFactureRapprochement(OperationBancaire operation) {
        if (operation.getSens() == SensOperation.CREDIT && operation.getNatureOperation() == NatureOperationBancaire.FACTURE_CLIENT) {
            return TypeFactureRapprochement.VENTE;
        }
        if (operation.getSens() == SensOperation.DEBIT && operation.getNatureOperation() == NatureOperationBancaire.SALAIRE) {
            return TypeFactureRapprochement.SALAIRE;
        }
        return TypeFactureRapprochement.ACHAT;
    }

    private YearMonth periodeSalaireOperation(LocalDate dateOperation) {
        return periodeSalaireService.determiner(dateOperation);
    }

    private boolean operationEligibleRapprochementNoteFrais(OperationBancaire operation) {
        if (operation.isRapprochee()
                || operation.getStatutRapprochement() != StatutOperationBancaire.NON_RAPPROCHEE
                || operation.getTiersId() == null
                || operation.getSens() != SensOperation.DEBIT) {
            return false;
        }
        String libelle = normaliserPourMatching(operation.getLibelle()).toUpperCase(Locale.ROOT);
        return operation.getNatureOperation() == NatureOperationBancaire.NOTE_DE_FRAIS
                || libelle.contains("NOTE FRAIS")
                || libelle.contains("NDF")
                || libelle.contains("REMBOURSEMENT");
    }

    private int score(OperationBancaire operation, FactureOuverteRapprochementDto facture, BigDecimal montantOperation) {
        int score = 0;
        if (differenceMontant(montantOperation, facture.resteAPayer()).compareTo(TOLERANCE_MONTANT) <= 0) {
            score += 50;
        }
        if (facture.dateEcheance() != null && Math.abs(ChronoUnit.DAYS.between(facture.dateEcheance(), operation.getDateOperation())) <= 7) {
            score += 30;
        }
        if (operation.getNatureOperation() == NatureOperationBancaire.SALAIRE
                && facture.dateFacture() != null
                && YearMonth.from(facture.dateFacture()).equals(periodeSalaireOperation(operation.getDateOperation()))) {
            score += 30;
        }
        if (operation.getTiersId() != null && operation.getTiersId().equals(facture.tiersId())) {
            score += 20;
        }
        String libelle = normaliserPourMatching(operation.getLibelle());
        if (facture.numeroFacture() != null && libelle.contains(normaliserPourMatching(facture.numeroFacture()))) {
            score += 10;
        }
        if (facture.nomTiers() != null && libelle.contains(normaliserPourMatching(facture.nomTiers()))) {
            score += 10;
        }
        return score;
    }

    private BigDecimal differenceMontant(BigDecimal gauche, BigDecimal droite) {
        return nulZero(gauche).subtract(nulZero(droite)).abs();
    }

    private List<FactureOuverteRapprochementDto> trouverCombinaison(List<FactureOuverteRapprochementDto> factures, BigDecimal montantOperation) {
        List<FactureOuverteRapprochementDto> candidates = factures.stream().limit(10).toList();
        return chercherCombinaison(candidates, montantOperation, 0, BigDecimal.ZERO, new ArrayList<>());
    }

    private List<FactureOuverteRapprochementDto> chercherCombinaison(
            List<FactureOuverteRapprochementDto> factures,
            BigDecimal cible,
            int index,
            BigDecimal somme,
            List<FactureOuverteRapprochementDto> selection
    ) {
        if (selection.size() > 1 && differenceMontant(cible, somme).compareTo(TOLERANCE_MONTANT) <= 0) {
            return List.copyOf(selection);
        }
        if (index >= factures.size() || somme.compareTo(cible.add(TOLERANCE_MONTANT)) > 0) {
            return List.of();
        }
        selection.add(factures.get(index));
        List<FactureOuverteRapprochementDto> avec = chercherCombinaison(factures, cible, index + 1, somme.add(nulZero(factures.get(index).resteAPayer())), selection);
        if (!avec.isEmpty()) {
            return avec;
        }
        selection.remove(selection.size() - 1);
        return chercherCombinaison(factures, cible, index + 1, somme, selection);
    }

    private void appliquerRapprochementFactures(
            ContexteRequete contexte,
            OperationBancaire operation,
            TypeFactureRapprochement typeFacture,
            List<FactureOuverteRapprochementDto> factures,
            int score,
            boolean multi
    ) {
        String libelle = libelleRapprochement(operation, multi ? "MULTI" : factures.get(0).numeroFacture());
        for (FactureOuverteRapprochementDto facture : factures) {
            facturesClient.enregistrerPaiement(
                    contexte,
                    typeFacture,
                    facture.id(),
                    new EnregistrementPaiementFactureDto(facture.resteAPayer(), operation.getDateOperation(), libelle)
            );
            creerRapprochementFacture(contexte, operation, typeFacture, facture, facture.resteAPayer(), score, StatutRapprochement.VALIDE, libelle);
        }
        LocalDateTime maintenant = LocalDateTime.now();
        operation.setRapprochee(true);
        operation.setStatutRapprochement(StatutOperationBancaire.RAPPROCHEE);
        operation.setFactureId(factures.get(0).id());
        operation.setTypeFacture(typeFacture);
        operation.setLibelleRapprochement(libelle);
        operation.setDateRapprochement(maintenant);
        operation.setDateModification(maintenant);
    }

    private void validerProposition(ContexteRequete contexte, RapprochementBancaire rapprochement) {
        verifierPropositionModifiable(rapprochement, "valider");
        Long documentId = rapprochement.getTypeFacture() == TypeFactureRapprochement.SALAIRE
                ? rapprochement.getSalaireId() : rapprochement.getFactureId();
        if (documentId == null || rapprochement.getTypeFacture() == null) {
            throw new RegleMetierException("FACTURE_RAPPROCHEMENT_OBLIGATOIRE",
                    "La proposition doit etre liee a une facture pour etre validee.");
        }
        OperationBancaire operation = rapprochement.getOperationBancaire();
        BigDecimal montantRapproche = nulZero(rapprochement.getMontantRapproche());
        String libelle = rapprochement.getLibelleRapprochement();
        if (libelle == null || libelle.isBlank()) {
            libelle = libelleRapprochement(operation, String.valueOf(documentId));
            rapprochement.setLibelleRapprochement(libelle);
        }

        facturesClient.enregistrerPaiement(
                contexte,
                rapprochement.getTypeFacture(),
                documentId,
                new EnregistrementPaiementFactureDto(montantRapproche, operation.getDateOperation(), libelle)
        );

        BigDecimal totalValide = rapprochementRepository.sommeMontantParOperationEtStatut(operation.getId(), StatutRapprochement.VALIDE)
                .add(montantRapproche);
        BigDecimal montantOperation = montantOperation(operation);
        boolean complet = totalValide.compareTo(montantOperation.subtract(TOLERANCE_MONTANT)) >= 0;

        LocalDateTime maintenant = LocalDateTime.now();
        rapprochement.setStatut(StatutRapprochement.VALIDE);
        rapprochement.setDateValidation(maintenant);
        rapprochement.setUtilisateurValidationId(contexte.utilisateurId());
        operation.setRapprochee(complet);
        operation.setStatutRapprochement(complet ? StatutOperationBancaire.RAPPROCHEE : StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
        operation.setFactureId(rapprochement.getTypeFacture() == TypeFactureRapprochement.SALAIRE
                ? null : rapprochement.getFactureId());
        operation.setSalaireId(rapprochement.getSalaireId());
        operation.setTypeFacture(rapprochement.getTypeFacture());
        operation.setLibelleRapprochement(libelle);
        operation.setDateRapprochement(maintenant);
        operation.setDateModification(maintenant);
    }

    private void annulerProposition(ContexteRequete contexte, RapprochementBancaire rapprochement, String motif) {
        verifierPropositionModifiable(rapprochement, "annuler");
        LocalDateTime maintenant = LocalDateTime.now();
        rapprochement.setStatut(StatutRapprochement.ANNULE);
        rapprochement.setDateAnnulation(maintenant);
        rapprochement.setUtilisateurAnnulationId(contexte.utilisateurId());
        rapprochement.setMotifAnnulation(motif);
    }

    private void verifierPropositionModifiable(RapprochementBancaire rapprochement, String action) {
        if (rapprochement.getStatut() != StatutRapprochement.PROPOSE) {
            String libelleAction = "valider".equals(action) ? "validee" : "annulee";
            throw new RegleMetierException("RAPPROCHEMENT_STATUT_INVALIDE",
                    "Seule une proposition de rapprochement peut etre %s.".formatted(libelleAction));
        }
    }

    private void creerRapprochementFacture(
            ContexteRequete contexte,
            OperationBancaire operation,
            TypeFactureRapprochement typeFacture,
            FactureOuverteRapprochementDto facture,
            BigDecimal montant,
            int score,
            StatutRapprochement statut,
            String libelle
    ) {
        creerRapprochementFacture(contexte, operation, typeFacture, facture, montant, score, statut, libelle, "RAPPROCHEMENT_FACTURES");
    }

    private void creerRapprochementFacture(
            ContexteRequete contexte, OperationBancaire operation, TypeFactureRapprochement typeFacture,
            FactureOuverteRapprochementDto facture, BigDecimal montant, int score,
            StatutRapprochement statut, String libelle, String mode
    ) {
        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId());
        rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setOperationBancaire(operation);
        rapprochement.setFactureId(facture.id());
        rapprochement.setTypeFacture(typeFacture);
        rapprochement.setTypePiece(typeFacture == TypeFactureRapprochement.VENTE
                ? TypePieceRapprochement.FACTURE_VENTE : TypePieceRapprochement.FACTURE_ACHAT);
        rapprochement.setDocumentAssocieNumero(facture.numeroFacture());
        rapprochement.setDocumentAssocieLibelle(facture.libelle());
        rapprochement.setMontantRapproche(montant);
        rapprochement.setEcart(differenceMontant(montantOperation(operation), montant));
        rapprochement.setStatut(statut);
        rapprochement.setModeRapprochement(mode);
        rapprochement.setLibelleRapprochement(libelle);
        rapprochement.setScoreMatching(score);
        rapprochement.setDateRapprochement(LocalDateTime.now());
        rapprochement.setUtilisateurId(contexte.utilisateurId());
        if (statut == StatutRapprochement.PROPOSE) {
            rapprochement.setDateProposition(rapprochement.getDateRapprochement());
        } else if (statut == StatutRapprochement.VALIDE) {
            rapprochement.setDateValidation(rapprochement.getDateRapprochement());
            rapprochement.setUtilisateurValidationId(contexte.utilisateurId());
        }
        rapprochementRepository.save(rapprochement);
    }

    private void creerRapprochementSalaire(
            ContexteRequete contexte, OperationBancaire operation, FactureOuverteRapprochementDto salaire,
            YearMonth periode, BigDecimal montant, StatutRapprochement statut, String libelle) {
        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId());
        rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setOperationBancaire(operation);
        rapprochement.setSalaireId(salaire.id());
        rapprochement.setTypeFacture(TypeFactureRapprochement.SALAIRE);
        rapprochement.setTypePiece(TypePieceRapprochement.SALAIRE);
        rapprochement.setPeriodeSalaire("%04d-%02d".formatted(periode.getYear(), periode.getMonthValue()));
        rapprochement.setDocumentAssocieNumero(salaire.numeroFacture());
        rapprochement.setDocumentAssocieLibelle(salaire.libelle());
        rapprochement.setMontantRapproche(montant);
        rapprochement.setEcart(differenceMontant(montantOperation(operation), salaire.resteAPayer()));
        rapprochement.setStatut(statut);
        rapprochement.setModeRapprochement("AUTOMATIQUE_SALAIRE");
        rapprochement.setLibelleRapprochement(libelle);
        rapprochement.setScoreMatching(statut == StatutRapprochement.VALIDE ? 100 : 70);
        rapprochement.setDateRapprochement(LocalDateTime.now());
        rapprochement.setUtilisateurId(contexte.utilisateurId());
        if (statut == StatutRapprochement.PROPOSE) {
            rapprochement.setDateProposition(rapprochement.getDateRapprochement());
        } else {
            rapprochement.setDateValidation(rapprochement.getDateRapprochement());
            rapprochement.setUtilisateurValidationId(contexte.utilisateurId());
        }
        rapprochementRepository.save(rapprochement);
    }

    private String libelleRapprochement(OperationBancaire operation, String numeroFacture) {
        String numero = numeroFacture == null || numeroFacture.isBlank() ? "FACTURE" : numeroFacture;
        return "RAP-%s-%s-OP-%s".formatted(operation.getDateOperation().format(DATE_RAPPROCHEMENT_FORMAT), numero, operation.getId());
    }

    private String libelleRapprochementNoteFrais(OperationBancaire operation, String numeroFacture) {
        String numero = numeroFacture == null || numeroFacture.isBlank() ? "FACTURE" : numeroFacture;
        return "RAP-%s-NDF-%s-OP-%s".formatted(operation.getDateOperation().format(DATE_RAPPROCHEMENT_FORMAT), numero, operation.getId());
    }

    private String libelleRapprochementSalaire(OperationBancaire operation, YearMonth periode, Long salaireId) {
        return "RAP-%s-SAL-%04d-%02d-%s-OP-%s".formatted(
                operation.getDateOperation().format(DATE_RAPPROCHEMENT_FORMAT),
                periode.getYear(), periode.getMonthValue(), salaireId, operation.getId());
    }

    private String normaliserPourMatching(String valeur) {
        return valeur == null ? "" : valeur.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private record MatchFacture(FactureOuverteRapprochementDto facture, int score, BigDecimal difference) {}
    private record ResultatRapprochementOperation(int rapprochementsCrees, int propositionsCreees, int sansMatch, List<String> messages) {}

    private CompteBancaire trouverCompteActif(ContexteRequete contexte, Long id) {
        return compteRepository.findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Compte bancaire introuvable."));
    }

    private OperationBancaire trouverOperation(ContexteRequete contexte, Long id) {
        return operationRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Operation bancaire introuvable."));
    }

    private OperationBancaire trouverOperationActive(ContexteRequete contexte, Long id) {
        return operationRepository.findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Operation bancaire introuvable."));
    }

    private RapprochementBancaire trouverRapprochement(ContexteRequete contexte, Long id) {
        return rapprochementRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Rapprochement bancaire introuvable."));
    }

    private void verifierOperationEligiblePieceRapprochement(OperationBancaire operation) {
        if (operation.isRapprochee() || operation.getStatutRapprochement() == StatutOperationBancaire.RAPPROCHEE) {
            throw new RegleMetierException("OPERATION_DEJA_RAPPROCHEE", "L'operation bancaire est deja rapprochee.");
        }
        if (operation.getTiersId() == null || operation.getTiersReconnu() == null || operation.getTiersReconnu().isBlank()) {
            throw new RegleMetierException("TIERS_RECONNU_OBLIGATOIRE", "Un tiers reconnu est obligatoire pour creer une piece de rapprochement.");
        }
        if (operation.getFactureRapprochementId() != null || operation.getFactureId() != null) {
            throw new RegleMetierException("FACTURE_RAPPROCHEMENT_DEJA_LIEE", "Une facture est deja liee a cette operation bancaire.");
        }
    }

    private BigDecimal montantOperation(OperationBancaire operation) {
        BigDecimal montant = operation.getSens() == SensOperation.DEBIT && operation.getDebit() != null
                ? operation.getDebit()
                : operation.getMontant();
        return nulZero(montant).abs();
    }

    private Map<Long, Boolean> autorisationsFactureRapprochement(ContexteRequete contexte) {
        return tiersClient.listerTiersActifs(contexte).stream()
                .collect(Collectors.toMap(
                        TiersTresorerieDto::id,
                        tiers -> Boolean.TRUE.equals(tiers.pieceRapprochementAutorisee()),
                        (existante, remplacement) -> existante || remplacement
                ));
    }

    private Boolean autorisationFactureRapprochement(OperationBancaire operation, Map<Long, Boolean> autorisationsFactureRapprochement) {
        return operation.getTiersId() == null ? null : autorisationsFactureRapprochement.getOrDefault(operation.getTiersId(), false);
    }

    private ImportReleveBancaire trouverImport(ContexteRequete contexte, Long id) {
        return importRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Import releve introuvable."));
    }

    private BigDecimal nulZero(BigDecimal valeur) {
        return valeur == null ? BigDecimal.ZERO : valeur;
    }

    private String normaliserIban(String valeur) {
        return valeur == null ? null : valeur.replaceAll("\\s+", "").toUpperCase();
    }

    private String normaliserTexte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim().toUpperCase();
    }

    private String normaliserTexteLibre(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
