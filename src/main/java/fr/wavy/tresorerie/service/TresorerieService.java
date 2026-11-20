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
import java.util.HashMap;
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

import fr.wavy.tresorerie.dto.ConfirmationCarteDemande;
import fr.wavy.tresorerie.dto.AnnulationPaiementRapprochementDto;
import fr.wavy.tresorerie.dto.AssistantRapprochementDto.*;
import fr.wavy.tresorerie.dto.ConfirmationCartesDemande;
import fr.wavy.tresorerie.dto.ConfirmationCartesResultat;
import fr.wavy.tresorerie.dto.PieceCarteRapprochementDto;
import fr.wavy.tresorerie.dto.PropositionCarteDto;
import fr.wavy.tresorerie.dto.PropositionsCarteResultat;
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
    private static final String MOTIF_ANNULATION_VALIDE = "Annulation du rapprochement validé";
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
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final PaiementAchatRapprochementService paiementsAchats;
    private final org.springframework.transaction.support.TransactionTemplate transactionAssistant;
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
            ClassificationOperationBancaireService classificationService,
            PaiementAchatRapprochementService paiementsAchats,
            org.springframework.transaction.PlatformTransactionManager transactionManager
    ) {
        this.transactionAssistant=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.transactionAssistant.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.paiementsAchats = paiementsAchats;
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
            Long importId,
            String statutConsultation,
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
                        statutRapprochement, importId, statutConsultation, false, List.of(0L), pageable)
                .map(operation -> mapper.versReponse(
                        operation,
                        autorisationFactureRapprochement(operation, autorisationsFactureRapprochement)
                ));
    }

    @Transactional(readOnly = true)
    public Compteurs compterOperations(ContexteRequete c, Perimetre p) {
        if (p == null) p = new Perimetre(null, null, null, null, null, null);
        var resultats = operationRepository.compter(c.tenantId(), c.societeCouranteId(), null, null, null, "", null, null,
                null, null, p.tiersReconnu() == null ? "" : p.tiersReconnu().trim(), p.typeTiersReconnu(),
                p.natureOperation(), p.statutClassification(), p.statutRapprochement(), p.importId(), p.statutConsultation());
        Map<String, Long> comptes = new HashMap<>();
        long sansTiers = 0;
        for (var resultat : resultats) {
            comptes.put((String) resultat[0], ((Number) resultat[1]).longValue());
            sansTiers += ((Number) resultat[2]).longValue();
        }
        return new Compteurs(comptes.values().stream().mapToLong(Long::longValue).sum(),
                comptes.getOrDefault("RAPPROCHEE", 0L), comptes.getOrDefault("PARTIELLE", 0L),
                comptes.getOrDefault("TIERS_IDENTIFIE", 0L), comptes.getOrDefault("NON_RAPPROCHEE", 0L), sansTiers);
    }

    @Transactional(readOnly = true)
    public Detail consulterOperation(ContexteRequete c, Long id) {
        var operation = trouverOperationActive(c, id);
        var historique = rapprochementRepository.findByTenantIdAndSocieteInterneIdAndOperationBancaireIdOrderByDateRapprochementDescIdDesc(
                c.tenantId(), c.societeCouranteId(), id).stream().map(mapper::versReponse).toList();
        return new Detail(mapper.versReponse(operation), historique,
                montantOperation(operation).subtract(nulZero(operation.getMontantRapproche())));
    }

    private Pageable pageableOperations(Integer page, Integer size, String sortBy, String sortDirection) {
        int numeroPage = Math.max(0, page == null ? 0 : page);
        int taillePage = size == null ? TAILLE_PAGE_OPERATIONS_DEFAUT : size;
        taillePage = Math.max(1, Math.min(taillePage, TAILLE_PAGE_OPERATIONS_MAX));

        String proprieteTri = CHAMPS_TRI_OPERATIONS.getOrDefault(sortBy, "dateOperation");
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort tri = switch (sortBy) {
            case "statutConsultation" -> org.springframework.data.jpa.domain.JpaSort.unsafe(direction, "(" + OperationBancaireRepository.STATUT_CONSULTATION + ")");
            case "montantSigne" -> org.springframework.data.jpa.domain.JpaSort.unsafe(direction,
                    "(case when o.sens = fr.wavy.tresorerie.modele.SensOperation.DEBIT then -abs(o.debit) else abs(o.montant) end)");
            case "numeroPieceAssociee" -> Sort.by(direction, "numeroPieceAssociee");
            default -> Sort.by(direction, proprieteTri);
        };
        tri = tri.and(Sort.by(Sort.Direction.DESC, "id"));
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
        verifierOperationModifiable(operation);
        verifierOperationModifiable(operation);
        operation.setActif(false);
        operation.setDateModification(LocalDateTime.now());
    }

    public OperationBancaireReponse modifierClassificationOperation(ContexteRequete contexte, Long id, ClassificationOperationDemande demande) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        verifierOperationModifiable(operation);
        operation.setNatureOperation(demande.natureOperation());
        operation.setStatutClassification(StatutClassificationOperation.CLASSEE_MANUELLE);
        operation.setDateModification(LocalDateTime.now());
        return mapper.versReponse(operation, autorisationFactureRapprochement(operation, autorisationsFactureRapprochement(contexte)));
    }

    public OperationBancaireReponse retraiterOperation(ContexteRequete contexte, Long id) {
        OperationBancaire operation = trouverOperationActive(contexte, id);
        verifierOperationModifiable(operation);
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
            verifierOperationModifiable(operation);
            if (operation.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE) return;
            ResultatReconnaissanceTiers resultat = classificationService.reconnaitreTiersDepuisRegles(operation.getLibelle(), regles);
            if (resultat.trouve() && operation.getStatutClassification()!=StatutClassificationOperation.CLASSEE_MANUELLE) {
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

    @Transactional(readOnly = true)
    public PropositionsCarteResultat proposerCartes(ContexteRequete contexte) {
        var operations = operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                contexte.tenantId(), contexte.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE)
                .stream().filter(o -> !o.isRapprochee() && o.getSens() == SensOperation.DEBIT).toList();
        var pieces = facturesClient.listerCartesOuvertes(contexte);
        var enCours = rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(contexte.tenantId(), contexte.societeCouranteId())
                .stream().filter(r -> r.getStatut() == StatutRapprochement.PROPOSE || r.getStatut() == StatutRapprochement.VALIDE).toList();
        List<PropositionCarteDto> candidats = new ArrayList<>();
        for (var operation : operations) for (var piece : pieces)
            RapprochementCarteMatcher.comparer(operation, piece).ifPresent(candidats::add);
        var resultat = candidats.stream().map(p -> p.avecConflits(
                candidats.stream().anyMatch(a -> a != p && (a.operationId().equals(p.operationId()) || a.factureId().equals(p.factureId()))),
                p.conflit() || enCours.stream().anyMatch(r ->
                        (r.getOperationBancaire().getId().equals(p.operationId())
                                || (r.getTypeFacture() == TypeFactureRapprochement.ACHAT && p.factureId().equals(r.getFactureId())))
                        && !(r.getOperationBancaire().getId().equals(p.operationId()) && p.factureId().equals(r.getFactureId())
                            && "CARTE_ENTREPRISE".equals(r.getModeRapprochement())))))
                .sorted(Comparator.comparing(PropositionCarteDto::dateAtypique)
                        .thenComparing(p -> !p.commercantReconnu()).thenComparing(PropositionCarteDto::operationId))
                .toList();
        return new PropositionsCarteResultat(operations.size(), pieces.size(),
                (int) operations.stream().filter(o -> candidats.stream().noneMatch(p -> p.operationId().equals(o.getId()))).count(), resultat);
    }

    public RapprochementFacturesResultat confirmerCarte(ContexteRequete contexte, Long operationId,
            ConfirmationCarteDemande demande) {
        paiementsAchats.confirmerCarte(contexte, operationId, demande);
        return new RapprochementFacturesResultat(1, 1, 0, 0, List.of("Dépense carte confirmée par l'utilisateur."));
    }

    public ConfirmationCartesResultat confirmerCartes(ContexteRequete contexte, ConfirmationCartesDemande demande) {
        return paiementsAchats.confirmerCartes(contexte, demande);
    }

    private List<OperationBancaire> operationsAssistant(ContexteRequete c, Perimetre p) {
        if (p==null) p=new Perimetre(null,null,null,null,null,null);
        var page=operationRepository.rechercher(c.tenantId(),c.societeCouranteId(),null,null,null,"",null,null,null,null,
                p.tiersReconnu()==null ? "" : p.tiersReconnu().trim(),p.typeTiersReconnu(),p.natureOperation(),p.statutClassification(),
                p.statutRapprochement(), p.importId(), p.statutConsultation(), p.operationIds() != null,
                p.operationIds() == null || p.operationIds().isEmpty() ? List.of(0L) : p.operationIds(),
                Pageable.unpaged(Sort.by("id")));
        return page.getContent();
    }

    private boolean concerneAssistant(Etape e,String categorie,OperationBancaire o) {
        boolean debit=o.getSens()==SensOperation.DEBIT;
        return switch(e) {
            case TIERS,PIECES -> true;
            case NOTES_FRAIS -> debit && ("REMBOURSEMENT".equals(categorie) ? PaiementAchatRapprochementService.remboursement(o)
                    : o.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB);
            case SALAIRES -> debit && (o.getTypeTiersReconnu()==TypeTiersReconnu.SALARIE || o.getNatureOperation()==NatureOperationBancaire.SALAIRE)
                    && o.getTypeOperation()!=TypeOperationBancaire.PAIEMENT_CB && !PaiementAchatRapprochementService.remboursement(o);
            case CLIENTS -> o.getSens()==SensOperation.CREDIT;
            case FOURNISSEURS -> debit && !PaiementAchatRapprochementService.remboursement(o)
                    && !List.of(NatureOperationBancaire.FRAIS_BANCAIRES,NatureOperationBancaire.TVA,NatureOperationBancaire.IMPOT,NatureOperationBancaire.URSSAF).contains(o.getNatureOperation())
                    && (o.getTypeTiersReconnu()!=TypeTiersReconnu.SALARIE || o.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB);
        };
    }

    private boolean concerneDomaine(Etape e, OperationBancaire o, Set<String> roles) {
        boolean debit=o.getSens()==SensOperation.DEBIT;
        return switch (e) {
            case TIERS -> true;
            case SALAIRES -> debit && roles.contains("SALARIE")
                    && o.getTypeOperation()!=TypeOperationBancaire.PAIEMENT_CB
                    && (o.getNatureOperation()==NatureOperationBancaire.SALAIRE
                        || PaiementAchatRapprochementService.remboursement(o)
                        || o.getTypeTiersReconnu()==TypeTiersReconnu.SALARIE && !ClassificationOperationBancaireService.roleFournisseur(roles));
            case CLIENTS -> !debit && roles.contains("CLIENT");
            case FOURNISSEURS -> debit && ClassificationOperationBancaireService.roleFournisseur(roles)
                    && (o.getNatureOperation()!=NatureOperationBancaire.SALAIRE
                        || o.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB
                            && o.getStatutClassification()!=StatutClassificationOperation.CLASSEE_MANUELLE)
                    && !PaiementAchatRapprochementService.remboursement(o);
            default -> false;
        };
    }

    private boolean natureFournisseurSansFacture(NatureOperationBancaire nature) {
        return Set.of(NatureOperationBancaire.TVA, NatureOperationBancaire.IMPOT, NatureOperationBancaire.URSSAF,
                NatureOperationBancaire.FRAIS_BANCAIRES).contains(nature);
    }

    /** Lecture seule : ni rapprochement PROPOSE ni tiers/règle ne sont écrits ici. */
    @Transactional(readOnly=true)
    public Apercu previsualiserAssistant(ContexteRequete c,Etape e,String categorie,Perimetre p) {
        return previsualiserAssistant(c, e, categorie, p, true);
    }

    private Apercu previsualiserAssistant(ContexteRequete c, Etape e, String categorie, Perimetre p, boolean verifierTiers) {
        if (e==null) throw new RegleMetierException("ETAPE_REQUISE","Choisissez une étape.");
        if (e==Etape.NOTES_FRAIS && categorie!=null && !List.of("CARTE","REMBOURSEMENT").contains(categorie))
            throw new RegleMetierException("CATEGORIE_INVALIDE","Choisissez carte ou remboursement.");
        if (verifierTiers) verifierTiersIdentifies(c, e, p);
        var scope=operationsAssistant(c,p);
        boolean consultation = p != null && Boolean.TRUE.equals(p.consultation());
        var tiers=tiersClient.listerTiersActifs(c).stream().filter(t -> !Boolean.FALSE.equals(t.actif())).toList();
        Map<Long,TiersTresorerieDto> parTiers=tiers.stream().collect(Collectors.toMap(TiersTresorerieDto::id,Function.identity(),(a,b)->a));
        var regles=e==Etape.TIERS ? classificationService.chargerReglesBancaires(c,tiers) : List.<RegleClassificationOperation>of();
        var cartes=e==Etape.TIERS ? List.<PieceCarteRapprochementDto>of() : facturesClient.listerCartesOuvertes(c);
        var autresCartes=e==Etape.NOTES_FRAIS || consultation && e==Etape.FOURNISSEURS ? operationRepository.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(c.tenantId(),c.societeCouranteId(),StatutOperationBancaire.NON_RAPPROCHEE) : List.<OperationBancaire>of();
        var idsCartes=cartes.stream().map(PieceCarteRapprochementDto::id).collect(Collectors.toSet());
        var traces=rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId());
        Map<String,List<FactureOuverteRapprochementDto>> cache=new HashMap<>();
        var lignes=new ArrayList<Ligne>();int traitees=0;
        for (var o:scope) {
            var t=parTiers.get(o.getTiersId());
            var roles=t==null || t.roles()==null ? Set.<String>of() : t.roles();
            if (consultation && !concerneDomaine(e, o, roles)) continue;
            Etape etape=e;
            String famille=categorie;
            if (consultation && e==Etape.SALAIRES && PaiementAchatRapprochementService.remboursement(o)) {
                etape=Etape.NOTES_FRAIS;
                famille="REMBOURSEMENT";
            }
            boolean carteEntreprise = consultation && e==Etape.FOURNISSEURS && o.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB;
            if (carteEntreprise && (cartes.stream().anyMatch(piece -> RapprochementCarteMatcher.comparer(o,piece)
                    .filter(a -> !a.conflit()).isPresent()) || traces.stream().anyMatch(r -> r.getOperationBancaire().getId().equals(o.getId())
                    && r.getStatut()!=StatutRapprochement.ANNULE && "CARTE_ENTREPRISE".equals(r.getModeRapprochement())))) {
                etape=Etape.NOTES_FRAIS;
                famille="CARTE";
            }
            final Etape parcours=etape;
            if (!consultation && !concerneAssistant(etape,famille,o)) continue;
            if (consultation && e==Etape.TIERS && o.getTiersId()!=null) { traitees++; continue; }
            if (consultation && e!=Etape.TIERS && (o.getStatutRapprochement()==StatutOperationBancaire.IGNOREE
                    || nulZero(o.getMontantRapproche()).signum()>0 && nulZero(o.getMontantRapproche()).compareTo(montantOperation(o))>=0)) continue;
            if (etape==Etape.TIERS && o.getTiersId()!=null) traitees++;
            if (o.isRapprochee() && !(consultation && e==Etape.TIERS)) {
                if (etape!=Etape.TIERS && traces.stream().anyMatch(r -> r.getOperationBancaire().getId().equals(o.getId()) && r.getStatut()==StatutRapprochement.VALIDE && etapeTrace(r)==parcours)) traitees++;
                continue;
            }
            var anomalies=new ArrayList<String>();var candidats=new ArrayList<Candidat>();boolean bloque=false;
            boolean engage=traces.stream().anyMatch(r -> r.getOperationBancaire().getId().equals(o.getId()) && r.getStatut()==StatutRapprochement.PROPOSE && r.isPaiementEngage());
            if (engage && !(consultation && etape==Etape.TIERS) && !(etape==Etape.PIECES && traces.stream().anyMatch(r -> r.getOperationBancaire().getId().equals(o.getId()) && "CREATION_PIECE".equals(r.getModeRapprochement())))) { anomalies.add("Traitement engagé : reprendre les mêmes références, ne pas créer un autre paiement.");bloque=true; }
            if (consultation && e!=Etape.TIERS && engage && traces.stream().anyMatch(r ->
                    r.getOperationBancaire().getId().equals(o.getId()) && r.getStatut()==StatutRapprochement.PROPOSE
                    && r.isPaiementEngage() && "CREATION_PIECE".equals(r.getModeRapprochement()))) {
                var reprise=intentionCreation(c,o);
                if (t!=null && reprise.getTypePiece()!=null) {
                    candidats.add(new Candidat("CREATION",t.id(),t.libelle(),o.getTypeTiersReconnu(),null,"Reprendre la création",
                            reprise.getTypePiece().name(),reprise.getMontantRapproche(),reprise.getMontantRapproche(),null,null,false,
                            List.of("Reprise avec la référence de création existante"),List.of(),null,null));
                    lignes.add(new Ligne(mapper.versReponse(o,Boolean.TRUE.equals(t.pieceRapprochementAutorisee())),List.copyOf(candidats),List.of(),false));
                    continue;
                }
            }
            var enReprise=traces.stream().filter(r -> r.getOperationBancaire().getId().equals(o.getId()) && r.getStatut()==StatutRapprochement.PROPOSE
                    && r.isPaiementEngage() && etapeTrace(r)==parcours).findFirst();
            if (engage && etape!=Etape.TIERS && etape!=Etape.PIECES && enReprise.isPresent()) {
                var r=enReprise.get();Long piece=r.getTypeFacture()==TypeFactureRapprochement.SALAIRE ? r.getSalaireId() : r.getFactureId();
                String cle="CARTE_ENTREPRISE".equals(r.getModeRapprochement()) ? "C:"+piece : "P:"+r.getTypeFacture()+":"+piece;
                candidats.add(new Candidat(cle,o.getTiersId(),o.getTiersReconnu(),o.getTypeTiersReconnu(),piece,r.getDocumentAssocieNumero(),r.getTypeFacture().name(),
                    r.getMontantRapproche(),r.getMontantRapproche(),null,null,false,List.of("Intention de paiement durable "+r.getId()),List.of("Résultat à reprendre avec les mêmes références"),null,null));
                lignes.add(new Ligne(mapper.versReponse(o,t!=null && Boolean.TRUE.equals(t.pieceRapprochementAutorisee())),List.copyOf(candidats),List.copyOf(anomalies),false));
                continue;
            }
            if (etape==Etape.TIERS) {
                var unique=classificationService.reconnaitreRegleUnique(o.getLibelle(),regles);
                List<TiersTresorerieDto> trouves;
                if (o.getTiersId()==null && unique.trouve()) {
                    trouves = List.of(parTiers.get(unique.tiersId()));
                } else if (o.getTiersId()==null && unique.ambiguite()) {
                    var ids = classificationService.tiersDesReglesCorrespondantes(o.getLibelle(), regles);
                    trouves = tiers.stream().filter(tiersCandidat -> ids.contains(tiersCandidat.id())).toList();
                } else {
                    trouves = classificationService.proposerTiers(o,regles,tiers);
                }
                for (var candidat:trouves) {
                    var natureOp=new OperationBancaire();natureOp.setLibelle(o.getLibelle());natureOp.setSens(o.getSens());
                    natureOp.setTypeTiersReconnu(ClassificationOperationBancaireService.typeTiers(candidat.roles()));
                    NatureOperationBancaire proposee=classificationService.determinerNature(natureOp);
                    boolean manuelle=o.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE;
                    boolean certaine=o.getTiersId()==null && unique.trouve() && java.util.Objects.equals(unique.tiersId(),candidat.id());
                    candidats.add(new Candidat((certaine?"REGLE_BANCAIRE:":"T:")+candidat.id(),candidat.id(),candidat.libelle(),natureOp.getTypeTiersReconnu(),null,null,"TIERS",
                            BigDecimal.ZERO,BigDecimal.ZERO,null,null,certaine,
                            List.of(certaine?"Règle OPERATION_BANCAIRE active, unique et sans conflit":"Identité plausible : choix manuel"),
                            !certaine && trouves.size()>1 ? List.of("Plusieurs tiers possibles : choix explicite requis") : List.of(),
                            !manuelle && proposee!=o.getNatureOperation() ? proposee : null,null));
                }
                if (trouves.isEmpty()) anomalies.add("Aucun tiers proposé : rechercher dans Tiers avant une création explicite.");
                if (o.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE) anomalies.add("Classification manuelle conservée.");
            } else if (etape==Etape.NOTES_FRAIS && !"REMBOURSEMENT".equals(famille)) {
                for (var piece:cartes) RapprochementCarteMatcher.comparer(o,piece).ifPresent(a -> {
                    boolean ambigu=autresCartes.stream().anyMatch(autre -> !autre.getId().equals(o.getId()) && RapprochementCarteMatcher.comparer(autre,piece).isPresent())
                        || cartes.stream().anyMatch(autre -> !autre.id().equals(piece.id()) && RapprochementCarteMatcher.comparer(o,autre).isPresent());
                    boolean conflit=a.conflit() || traces.stream().anyMatch(r -> (r.getStatut()==StatutRapprochement.PROPOSE || r.getStatut()==StatutRapprochement.VALIDE)
                        && (r.getOperationBancaire().getId().equals(o.getId()) || (r.getTypeFacture()==TypeFactureRapprochement.ACHAT && piece.id().equals(r.getFactureId())))
                        && !(r.getOperationBancaire().getId().equals(o.getId()) && piece.id().equals(r.getFactureId()) && "CARTE_ENTREPRISE".equals(r.getModeRapprochement())));
                    boolean natureManuelleIncompatible = consultation && o.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE
                            && o.getNatureOperation()!=NatureOperationBancaire.FACTURE_FOURNISSEUR;
                    conflit = conflit || natureManuelleIncompatible;
                    var carte=a.avecConflits(ambigu,conflit);var avert=new ArrayList<String>();
                    if (ambigu) avert.add("Opération ou pièce partagée avec une autre proposition");
                    if (natureManuelleIncompatible) avert.add("Vérifiez la classification manuelle dans le détail de l’opération.");
                    else if (conflit) avert.add("Conflit de tiers ou de réservation");
                    if (a.dateAtypique()) avert.add("Date atypique : vérifier explicitement le justificatif");
                    if (!a.commercantReconnu()) avert.add("Commerçant non concordant : vérifier le justificatif");
                    candidats.add(new Candidat("C:"+piece.id(),piece.tiersId(),piece.nomTiers(),TypeTiersReconnu.FOURNISSEUR,piece.id(),piece.numeroPiece(),"ACHAT",
                        piece.montant(),a.montantRapproche(),piece.dateDepense(),null,!ambigu && !conflit && !a.dateAtypique() && a.commercantReconnu() && a.ecart().signum()==0,
                        a.criteres(),List.copyOf(avert),null,carte));
                });
            } else if (etape==Etape.PIECES) {
                var raisons=raisonsPieceAutomatique(c,o,t,traces,cache);
                if (raisons.isEmpty()) {
                    var type=determinerTypePiece(o);
                    candidats.add(new Candidat("CREATION",t.id(),t.libelle(),ClassificationOperationBancaireService.typeTiers(t.roles()),null,"À créer",type.name(),
                        montantOperation(o),montantOperation(o),o.getDateOperation(),null,true,
                        List.of("Tiers actif et explicitement autorisé","Aucune pièce ouverte pertinente","HT = TTC, TVA zéro ; aucune donnée déduite d'un justificatif"),
                        List.of("Création validée sans annulation coordonnée automatique"),null,null));
                } else { anomalies.addAll(raisons);bloque=true; }
            } else {
                if (t==null) { anomalies.add("Tiers actif à identifier à l'étape 1");bloque=true; }
                else {
                    TypeFactureRapprochement type=etape==Etape.CLIENTS ? TypeFactureRapprochement.VENTE : etape==Etape.SALAIRES ? TypeFactureRapprochement.SALAIRE : TypeFactureRapprochement.ACHAT;
                    boolean role=type==TypeFactureRapprochement.VENTE ? o.getTypeTiersReconnu()==TypeTiersReconnu.CLIENT : type==TypeFactureRapprochement.SALAIRE ? o.getTypeTiersReconnu()==TypeTiersReconnu.SALARIE
                        : etape==Etape.NOTES_FRAIS || List.of(TypeTiersReconnu.FOURNISSEUR,TypeTiersReconnu.FOURNISSEUR_SERVICE).contains(o.getTypeTiersReconnu()==null ? TypeTiersReconnu.AUTRE : o.getTypeTiersReconnu());
                    if (!(consultation || role)) { anomalies.add("Rôle du tiers bancaire incompatible avec cette étape");bloque=true; }
                    if (etape==Etape.FOURNISSEURS && o.getNatureOperation()!=NatureOperationBancaire.FACTURE_FOURNISSEUR
                            && (!consultation || !natureFournisseurSansFacture(o.getNatureOperation()))) { anomalies.add("Nature à vérifier séparément à l'étape 1");bloque=true; }
                    if (consultation && etape==Etape.SALAIRES && o.getNatureOperation()!=NatureOperationBancaire.SALAIRE
                            && o.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE) {
                        anomalies.add("La classification manuelle ne permet pas de proposer un salaire."); bloque=true;
                    }
                    if (consultation && etape==Etape.CLIENTS && o.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE
                            && o.getNatureOperation()!=NatureOperationBancaire.FACTURE_CLIENT) {
                        anomalies.add("La classification manuelle ne permet pas de proposer une facture client."); bloque=true;
                    }
                    if ((etape==Etape.SALAIRES || etape==Etape.NOTES_FRAIS) && o.getTypeOperation()!=TypeOperationBancaire.VIREMENT_EMIS) { anomalies.add("Virement émis attendu");bloque=true; }
                    String cle=type+":"+t.id()+":"+etape+":"+periodeSalaireOperation(o.getDateOperation());
                    var factures=cache.computeIfAbsent(cle,k -> {
                        if (parcours==Etape.SALAIRES) { var periode=periodeSalaireOperation(o.getDateOperation());return facturesClient.listerSalairesOuverts(c,t.id(),periode.getYear(),periode.getMonthValue(),null); }
                        if (parcours==Etape.NOTES_FRAIS) return facturesClient.listerFacturesAchatNotesFraisOuvertes(c,t.id(),null);
                        return facturesClient.listerFacturesOuvertes(c,type,t.id()).stream().filter(f -> type!=TypeFactureRapprochement.ACHAT || !idsCartes.contains(f.id())).toList();
                    });
                    BigDecimal disponible=montantOperation(o).subtract(nulZero(o.getMontantRapproche())).max(BigDecimal.ZERO);
                    Set<Long> dejaAssociees = traces.stream().filter(r -> r.getOperationBancaire().getId().equals(o.getId())
                            && r.getTypeFacture()==type && r.getStatut()==StatutRapprochement.VALIDE)
                            .map(r -> type==TypeFactureRapprochement.SALAIRE ? r.getSalaireId() : r.getFactureId()).collect(Collectors.toSet());
                    for (var f:factures) if (f.tiersId().equals(t.id()) && f.resteAPayer().signum()>0 && !dejaAssociees.contains(f.id())) {
                        var criteres=new ArrayList<String>();var alertes=new ArrayList<String>();
                        criteres.add("Même tiers, tenant et société ; pièce validée et ouverte");
                        boolean exact=disponible.compareTo(f.resteAPayer())==0;
                        criteres.add(exact ? "Montant restant exact" : "Affectation partielle ou plusieurs pièces à vérifier");
                        LocalDate date=RapprochementCarteMatcher.dateAchat(o.getLibelle(),o.getDateOperation());
                        LocalDate reference=date==null ? o.getDateOperation() : date;
                        boolean proches=f.dateFacture()!=null && Math.abs(ChronoUnit.DAYS.between(reference,f.dateFacture()))<=7;
                        criteres.add("Date d'achat : "+reference+" ; facture : "+f.dateFacture()+" ; échéance : "+f.dateEcheance());
                        boolean numero=f.numeroFacture()!=null && normaliserPourMatching(o.getLibelle()).contains(normaliserPourMatching(f.numeroFacture()));
                        if (numero) criteres.add("Référence de pièce présente dans le libellé");
                        if (!proches && type!=TypeFactureRapprochement.SALAIRE) alertes.add("Dates éloignées : consulter le justificatif");
                        boolean ambigu=factures.stream().filter(a -> a.resteAPayer().compareTo(disponible)==0).count()>1;
                        if (ambigu) alertes.add("Plusieurs pièces de même montant : aucun choix automatique");
                        candidats.add(new Candidat("P:"+type+":"+f.id(),t.id(),t.libelle(),o.getTypeTiersReconnu(),f.id(),f.numeroFacture(),type.name(),f.resteAPayer(),
                            disponible.min(f.resteAPayer()),f.dateFacture(),f.dateEcheance(),exact && !ambigu && (proches || numero || type==TypeFactureRapprochement.SALAIRE),List.copyOf(criteres),List.copyOf(alertes),null,null));
                    }
                    if (candidats.isEmpty()) anomalies.add("Aucune pièce ouverte pour ce tiers et cette étape");
                }
            }
            if (consultation && e!=Etape.TIERS && t!=null && !engage && candidats.isEmpty()) {
                var raisons=raisonsPieceAutomatique(c,o,t,traces,cache);
                if (e==Etape.SALAIRES && (o.getNatureOperation()==NatureOperationBancaire.SALAIRE
                        || PaiementAchatRapprochementService.remboursement(o))) {
                    raisons=List.of("Une pièce métier réelle doit être disponible pour ce paiement.");
                }
                if (o.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB) {
                    raisons=List.of("Une pièce carte ou une facture existante doit être disponible.");
                }
                if (raisons.isEmpty() && !bloque) {
                    var type=determinerTypePiece(o);
                    candidats.add(new Candidat("CREATION",t.id(),t.libelle(),o.getTypeTiersReconnu(),null,"À créer",type.name(),
                            montantOperation(o),montantOperation(o),o.getDateOperation(),null,false,
                            List.of("Création après confirmation", "HT = TTC, TVA zéro"),List.of(),null,null));
                    anomalies.clear();
                } else anomalies.addAll(raisons);
            }
            lignes.add(new Ligne(mapper.versReponse(o,t!=null && Boolean.TRUE.equals(t.pieceRapprochementAutorisee())),List.copyOf(candidats),List.copyOf(anomalies),bloque));
        }
        // Une sélection sûre ne doit partager aucune pièce avec une autre opération du périmètre.
        var toutes=lignes.stream().flatMap(l -> l.candidats().stream()).toList();
        var occurrencesPieces = toutes.stream().filter(a -> a.pieceId()!=null)
                .collect(Collectors.groupingBy(a -> a.typePiece()+":"+a.pieceId(), Collectors.counting()));
        lignes.replaceAll(l -> new Ligne(l.operation(),l.candidats().stream().map(a -> {
            boolean partage=a.pieceId()!=null && occurrencesPieces.getOrDefault(a.typePiece()+":"+a.pieceId(), 0L)>1;
            if (!partage) return a;
            var alertes=new ArrayList<>(a.avertissements());alertes.add("Pièce proposée sur plusieurs opérations : choix individuel requis");
            return new Candidat(a.cle(),a.tiersId(),a.nomTiers(),a.typeTiers(),a.pieceId(),a.numeroPiece(),a.typePiece(),a.montantDisponible(),a.montantPropose(),a.datePiece(),a.dateEcheance(),false,a.criteres(),List.copyOf(alertes),a.natureProposee(),a.carte());
        }).toList(),l.anomalies(),l.bloquee()));
        int eligibles=(int)lignes.stream().filter(l -> !l.bloquee() && !l.candidats().isEmpty()).count();
        BigDecimal montant=lignes.stream().filter(l -> !l.bloquee() && !l.candidats().isEmpty()).map(l -> l.operation().debit()==null ? l.operation().montant().abs() : (l.operation().sens()==SensOperation.DEBIT ? l.operation().debit() : l.operation().credit())).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Apercu(e,categorie,scope.size(),eligibles,(int)toutes.stream().filter(a -> a.pieceId()!=null).map(a -> a.typePiece()+":"+a.pieceId()).distinct().count(),
            toutes.size(),(int)lignes.stream().filter(l -> !l.anomalies().isEmpty() || l.candidats().stream().anyMatch(a -> !a.avertissements().isEmpty())).count(),traitees,lignes.size(),montant,List.copyOf(lignes));
    }

    private void verifierTiersIdentifies(ContexteRequete c, Etape e, Perimetre p) {
        if (e != Etape.SALAIRES && e != Etape.CLIENTS && e != Etape.FOURNISSEURS) return;
        long sansTiers = compterOperations(c, p).sansTiers();
        if (sansTiers > 0) throw new RegleMetierException("TIERS_A_IDENTIFIER",
                sansTiers + " opérations restent à identifier. Terminez l’identification des tiers avant de lancer les rapprochements.");
    }

    /** Chaque ligne conserve ses transactions propres ; pas de transaction globale de lot. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Bilan confirmerAssistant(ContexteRequete c,Confirmation demande) {
        if (demande==null || !Boolean.TRUE.equals(demande.confirmation()) || demande.selections()==null || demande.selections().isEmpty() || demande.selections().size()>200)
            throw new RegleMetierException("CONFIRMATION_REQUISE","Confirmez une sélection de 1 à 200 lignes.");
        long nombreOperations=demande.selections().stream().map(Choix::operationId).distinct().count();
        if ((demande.perimetre()==null || !Boolean.TRUE.equals(demande.perimetre().consultation()))
                && (demande.etape()==Etape.CLIENTS || demande.etape()==Etape.SALAIRES) && nombreOperations>1)
            throw new RegleMetierException("CONFIRMATION_INDIVIDUELLE","Ce parcours conserve une confirmation par opération.");
        verifierTiersIdentifies(c, demande.etape(), demande.perimetre());
        var operations=operationsAssistant(c,demande.perimetre()).stream().map(OperationBancaire::getId).collect(Collectors.toSet());
        Map<Long, Set<String>> certainesDuPerimetre = null;
        if (nombreOperations>1 && demande.etape()!=Etape.TIERS && demande.perimetre()!=null
                && Boolean.TRUE.equals(demande.perimetre().consultation())) {
            certainesDuPerimetre = previsualiserAssistant(c, demande.etape(), demande.categorie(), demande.perimetre(), false)
                    .lignes().stream().filter(l -> !l.bloquee()).collect(Collectors.toMap(l -> l.operation().id(),
                            l -> l.candidats().stream().filter(Candidat::sur).map(Candidat::cle).collect(Collectors.toSet())));
        }
        Map<String,Long> occurrences=demande.selections().stream().collect(Collectors.groupingBy(a -> a.operationId()+":"+a.candidatCle(),Collectors.counting()));
        var resultat=new ArrayList<Resultat>();
        for (var choix:demande.selections()) {
            try {
                if (!operations.contains(choix.operationId())) {
                    var deja=rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId()).stream()
                        .filter(r -> r.getOperationBancaire().getId().equals(choix.operationId()) && r.getStatut()==StatutRapprochement.VALIDE && traceCorrespondAuChoix(r, demande.etape(), choix)
                            && java.util.Objects.equals(r.getOperationBancaire().getTiersId(),choix.tiersId()) && r.getMontantRapproche().compareTo(choix.montant())==0
                            && (demande.etape()==Etape.PIECES || "CREATION".equals(choix.candidatCle()) || java.util.Objects.equals(choix.pieceId(),r.getTypeFacture()==TypeFactureRapprochement.SALAIRE ? r.getSalaireId() : r.getFactureId())))
                        .findFirst();
                    if (deja.isPresent() && (demande.perimetre()==null || demande.perimetre().operationIds()==null || demande.perimetre().operationIds().contains(choix.operationId()))) { resultat.add(new Resultat(choix.operationId(),"SUCCES",null,"Traitement déjà confirmé, aucun nouvel enregistrement.",deja.get().getId()));continue; }
                    throw new RegleMetierException("HORS_PERIMETRE","Cette opération ne correspond plus aux filtres ou à la société.");
                }
                if (demande.selections().stream().anyMatch(a -> a != choix && a.operationId().equals(choix.operationId())
                        && (choix.candidatCle().startsWith("C:") || "CREATION".equals(choix.candidatCle())
                            || a.candidatCle().startsWith("C:") || "CREATION".equals(a.candidatCle()))))
                    throw new RegleMetierException("CONFLIT_NATURE","Confirmez une seule affectation pour une pièce carte ou une création.");
                if (occurrences.get(choix.operationId()+":"+choix.candidatCle())>1
                    || ((demande.etape()==Etape.TIERS || demande.etape()==Etape.PIECES || (demande.etape()==Etape.NOTES_FRAIS && !"REMBOURSEMENT".equals(demande.categorie())))
                       && demande.selections().stream().filter(a -> a.operationId().equals(choix.operationId())).count()>1))
                    throw new RegleMetierException("DOUBLON","Une même opération ou proposition apparaît plusieurs fois.");
                if (choix.pieceId()!=null && demande.selections().stream().anyMatch(a -> !a.operationId().equals(choix.operationId()) && choix.pieceId().equals(a.pieceId())))
                    throw new RegleMetierException("CONFLIT_PIECE","Une pièce est sélectionnée sur plusieurs opérations : traiter individuellement.");
                Long id=confirmerChoixAssistant(c,demande,choix,nombreOperations>1,certainesDuPerimetre);
                resultat.add(new Resultat(choix.operationId(),"SUCCES",null,"Traitement confirmé.",id));
            } catch (RegleMetierException ex) {
                journaliserAnomalieAssistant(c,choix.operationId(),ex.getCode());
                boolean conflit=ex.getCode().contains("CONFLIT") || ex.getCode().contains("DOUBLON") || ex.getCode().contains("AMBIG");
                resultat.add(new Resultat(choix.operationId(),conflit ? "CONFLIT" : "REFUSE",ex.getCode(),ex.getMessage(),null));
            } catch (RuntimeException ex) {
                journaliserAnomalieAssistant(c,choix.operationId(),"RESULTAT_INCERTAIN");
                resultat.add(new Resultat(choix.operationId(),"A_REPRENDRE","RESULTAT_INCERTAIN","Résultat non confirmé : reprendre exactement cette sélection, sans créer une nouvelle pièce.",null));
            }
        }
        return new Bilan((int)resultat.stream().filter(r -> r.statut().equals("SUCCES")).count(),(int)resultat.stream().filter(r -> r.statut().equals("REFUSE")).count(),
            (int)resultat.stream().filter(r -> r.statut().equals("CONFLIT")).count(),(int)resultat.stream().filter(r -> r.statut().equals("A_REPRENDRE")).count(),List.copyOf(resultat));
    }

    private Long confirmerChoixAssistant(ContexteRequete c,Confirmation demande,Choix choix,boolean groupe, Map<Long, Set<String>> certainesDuPerimetre) {
        // Une reprise garde les références et montants d'origine, même si la pièce a été soldée à distance.
        var traces=rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId());
        for (var r:traces) if (r.getOperationBancaire().getId().equals(choix.operationId()) && r.getStatut()!=StatutRapprochement.ANNULE && traceCorrespondAuChoix(r, demande.etape(), choix)
                && (demande.etape()==Etape.PIECES || "CREATION".equals(choix.candidatCle()) || java.util.Objects.equals(choix.pieceId(),r.getFactureId()) || java.util.Objects.equals(choix.pieceId(),r.getSalaireId()))) {
            if (!java.util.Objects.equals(choix.tiersId(),r.getOperationBancaire().getTiersId()) || choix.montant().compareTo(r.getMontantRapproche())!=0)
                throw new RegleMetierException("REPRISE_DIFFERENTE","La reprise doit conserver le tiers, la pièce et le montant.");
            if (r.getStatut()==StatutRapprochement.VALIDE) return r.getId();
            if (demande.etape()!=Etape.PIECES && !"CREATION".equals(choix.candidatCle()) && r.isPaiementEngage()) {
                if (r.getTypeFacture()==TypeFactureRapprochement.ACHAT) paiementsAchats.valider(c,r.getId());
                else transactionAssistant.execute(st -> { validerProposition(c,trouverRapprochement(c,r.getId()));return null; });
                return r.getId();
            }
        }
        var seul=new Perimetre(demande.perimetre()==null ? null : demande.perimetre().tiersReconnu(),demande.perimetre()==null ? null : demande.perimetre().typeTiersReconnu(),
            demande.perimetre()==null ? null : demande.perimetre().natureOperation(),demande.perimetre()==null ? null : demande.perimetre().statutClassification(),
            demande.perimetre()==null ? null : demande.perimetre().statutRapprochement(),List.of(choix.operationId()),
            demande.perimetre()==null ? null : demande.perimetre().importId(),
            demande.perimetre()==null ? null : demande.perimetre().statutConsultation(), demande.perimetre()==null ? null : demande.perimetre().consultation());
        var apercu=previsualiserAssistant(c,demande.etape(),demande.categorie(),seul,false);
        var ligne=apercu.lignes().stream().filter(l -> l.operation().id().equals(choix.operationId())).findFirst()
            .orElseThrow(() -> new RegleMetierException("OPERATION_INDISPONIBLE","Opération déjà traitée ou non éligible."));
        if (ligne.bloquee()) throw new RegleMetierException("OPERATION_BLOQUEE",String.join(" ; ",ligne.anomalies()));
        Candidat candidat=ligne.candidats().stream().filter(a -> a.cle().equals(choix.candidatCle()) && a.tiersId().equals(choix.tiersId()) && java.util.Objects.equals(a.pieceId(),choix.pieceId())).findFirst().orElse(null);
        if (candidat==null && demande.etape()==Etape.TIERS && choix.candidatCle().equals("MANUEL:"+choix.tiersId())) {
            var t=tiersClient.listerTiersActifs(c).stream().filter(a -> choix.tiersId().equals(a.id()) && !Boolean.FALSE.equals(a.actif())).findFirst()
                .orElseThrow(() -> new RegleMetierException("TIERS_INDISPONIBLE","Tiers actif de cette société obligatoire."));
            candidat=new Candidat(choix.candidatCle(),t.id(),t.libelle(),ClassificationOperationBancaireService.typeTiers(t.roles()),null,null,"TIERS",BigDecimal.ZERO,BigDecimal.ZERO,null,null,false,List.of("Choix manuel confirmé"),List.of(),null,null);
        }
        boolean identificationAutomatique=demande.etape()==Etape.TIERS && choix.candidatCle().startsWith("REGLE_BANCAIRE:");
        if(identificationAutomatique && (candidat==null || !candidat.sur() || choix.confirmerNature() || choix.pieceId()!=null || choix.montant().signum()!=0))
            throw new RegleMetierException("REGLE_BANCAIRE_OBSOLETE","La règle n’est plus unique ou l’opération est déjà identifiée. Actualisez les propositions.");
        if (candidat==null) throw new RegleMetierException("CANDIDAT_OBSOLETE","La proposition n'est plus disponible : actualisez.");
        if (demande.etape()!=Etape.TIERS && (choix.montant().signum()<=0 || choix.montant().scale()>2 || choix.montant().compareTo(candidat.montantPropose())>0))
            throw new RegleMetierException("MONTANT_INVALIDE","Montant supérieur au solde disponible ou précision invalide.");
        if (groupe && (!candidat.sur() || certainesDuPerimetre!=null
                && !certainesDuPerimetre.getOrDefault(choix.operationId(), Set.of()).contains(choix.candidatCle())))
            throw new RegleMetierException("VERIFICATION_INDIVIDUELLE","Cette proposition exige une confirmation individuelle.");
        if ((demande.etape()==Etape.PIECES || candidat.carte()!=null) && choix.montant().compareTo(candidat.montantPropose())!=0)
            throw new RegleMetierException("MONTANT_INVALIDE","Ce parcours exige le montant exact de la proposition.");
        if (!candidat.avertissements().isEmpty() && demande.etape()!=Etape.PIECES && !choix.verifierJustificatif())
            throw new RegleMetierException("JUSTIFICATIF_REQUIS","Vérifiez les avertissements et le justificatif avant confirmation.");
        final Candidat retenu=candidat;
        boolean creation = "CREATION".equals(retenu.cle());
        if (demande.etape()==Etape.TIERS) return transactionAssistant.execute(st -> {
            var op=trouverOperationActive(c,choix.operationId());
            entityManager.refresh(op,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (op.getTiersId()!=null && (identificationAutomatique || !op.getTiersId().equals(retenu.tiersId()))) {
                throw new RegleMetierException("TIERS_DEJA_IDENTIFIE","Un tiers est déjà identifié.");
            }
            if (op.getTiersId()!=null && !choix.confirmerNature()) return null;
            if (choix.confirmerNature() && demande.perimetre()!=null && Boolean.TRUE.equals(demande.perimetre().consultation()))
                throw new RegleMetierException("NATURE_SEPAREE","Modifiez la classification depuis le détail de l’opération.");
            if (identificationAutomatique) {
                var regle=classificationService.reconnaitreRegleUnique(op.getLibelle(),
                        classificationService.chargerReglesBancaires(c, tiersClient.listerTiersActifs(c)));
                if (!regle.trouve() || !java.util.Objects.equals(regle.tiersId(), retenu.tiersId()))
                    throw new RegleMetierException("REGLE_BANCAIRE_OBSOLETE","Actualisez les propositions.");
            }
            if (choix.confirmerNature()) verifierOperationModifiable(op);
            op.setTiersId(retenu.tiersId());op.setTiersReconnu(retenu.nomTiers());op.setTypeTiersReconnu(retenu.typeTiers());
            if (choix.confirmerNature()) {
                if (op.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE)
                    throw new RegleMetierException("NATURE_MANUELLE","La nature manuelle doit être corrigée explicitement dans le tableau.");
                var copie=new OperationBancaire();copie.setLibelle(op.getLibelle());copie.setSens(op.getSens());copie.setTypeTiersReconnu(retenu.typeTiers());
                op.setNatureOperation(classificationService.determinerNature(copie));op.setStatutClassification(StatutClassificationOperation.CLASSEE_MANUELLE);
            }
            op.setDateModification(LocalDateTime.now());return null;
        });
        if (creation) {
            if (choix.montant().compareTo(retenu.montantPropose())!=0)
                throw new RegleMetierException("MONTANT_INVALIDE","Confirmez le montant exact de la pièce à créer.");
            // Intention durable avant l'appel interservice ; les champs et tables existants suffisent.
            transactionAssistant.execute(st -> {
                var op=trouverOperationActive(c,choix.operationId());entityManager.refresh(op,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                var existante=intentionCreation(c,op);if (existante.getId()!=null) return null;
                var tiersCourant=tiersClient.listerTiersActifs(c).stream().filter(t -> java.util.Objects.equals(op.getTiersId(),t.id()) && !Boolean.FALSE.equals(t.actif())).findFirst().orElse(null);
                var raisons=raisonsPieceAutomatique(c,op,tiersCourant,rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId()),new HashMap<>());
                if (!raisons.isEmpty()) throw new RegleMetierException("PIECE_NON_ELIGIBLE",String.join(" ; ",raisons));
                existante.setTenantId(c.tenantId());existante.setSocieteInterneId(c.societeCouranteId());existante.setOperationBancaire(op);
                existante.setTypePiece(determinerTypePiece(op));existante.setTypeFacture(determinerTypePiece(op)==TypePieceRapprochement.FACTURE_ACHAT ? TypeFactureRapprochement.ACHAT : null);
                existante.setMontantRapproche(choix.montant());existante.setEcart(BigDecimal.ZERO);existante.setModeRapprochement("CREATION_PIECE");
                existante.setLibelleRapprochement("RAP-PIECE-OP-"+op.getId());existante.setStatut(StatutRapprochement.PROPOSE);existante.setPaiementEngage(true);
                existante.setDateRapprochement(LocalDateTime.now());existante.setUtilisateurId(c.utilisateurId());rapprochementRepository.saveAndFlush(existante);return null;
            });
            return transactionAssistant.execute(st -> {
                var op=trouverOperationActive(c,choix.operationId());entityManager.refresh(op,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                if (op.isRapprochee()) {
                    var deja=rapprochementRepository.findByOperationBancaireIdAndStatut(op.getId(),StatutRapprochement.VALIDE).stream()
                        .filter(r -> etapeTrace(r)==Etape.PIECES && r.getMontantRapproche().compareTo(choix.montant())==0 && java.util.Objects.equals(op.getTiersId(),choix.tiersId())).findFirst();
                    if (deja.isPresent()) return deja.get().getId();
                }
                creerPieceRapprochement(c,choix.operationId());
                return rapprochementRepository.findByOperationBancaireIdAndStatut(choix.operationId(),StatutRapprochement.VALIDE).stream().findFirst().orElseThrow().getId();
            });
        }
        if (retenu.carte()!=null) {
            if ((retenu.carte().dateAtypique() || !retenu.carte().commercantReconnu()) && !choix.verifierJustificatif())
                throw new RegleMetierException("JUSTIFICATIF_REQUIS","Vérifiez explicitement le justificatif pour cette date ou ce commerçant.");
            if (retenu.carte().conflit()) throw new RegleMetierException("CONFLIT_CARTE","Tiers incompatible ou pièce déjà réservée.");
            return paiementsAchats.confirmerCarte(c,choix.operationId(),new ConfirmationCarteDemande(retenu.pieceId(),retenu.tiersId(),true,choix.confirmerAffectation(),choix.verifierJustificatif()));
        }
        var type=TypeFactureRapprochement.valueOf(retenu.typePiece());
        if (type==TypeFactureRapprochement.ACHAT) {
            Long id=paiementsAchats.preparer(c,choix.operationId(),retenu.pieceId(),choix.montant(),100,"ASSISTANT-OP-"+choix.operationId()+"-"+type+"-"+retenu.pieceId(),"ASSISTANT");
            paiementsAchats.valider(c,id);return id;
        }
        return confirmerDocumentAssistant(c,choix.operationId(),retenu.pieceId(),retenu.numeroPiece(),type,choix.montant());
    }

    private Long confirmerDocumentAssistant(ContexteRequete c,Long operationId,Long pieceId,String numeroPiece,TypeFactureRapprochement type,BigDecimal montant) {
        return confirmerDocument(c, operationId, pieceId, numeroPiece, type, montant, "ASSISTANT");
    }

    private Long confirmerDocument(ContexteRequete c, Long operationId, Long pieceId, String numeroPiece,
            TypeFactureRapprochement type, BigDecimal montant, String mode) {
        Long id=transactionAssistant.execute(st -> {
            var op=trouverOperationActive(c,operationId);entityManager.refresh(op,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            var existante=rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId()).stream()
                .filter(r -> r.getOperationBancaire().getId().equals(operationId) && r.getTypeFacture()==type && r.getStatut()!=StatutRapprochement.ANNULE
                    && pieceId.equals(type==TypeFactureRapprochement.SALAIRE ? r.getSalaireId() : r.getFactureId())).findFirst();
            if (existante.isPresent()) {
                if (existante.get().getMontantRapproche().compareTo(montant)!=0) throw new RegleMetierException("REPRISE_DIFFERENTE","Montant de reprise différent.");
                return existante.get().getId();
            }
            verifierOperationModifiable(op);
            if ("MANUEL".equals(mode) && (op.getStatutRapprochement() == StatutOperationBancaire.IGNOREE
                    || java.util.stream.Stream.of(StatutRapprochement.PROPOSE, StatutRapprochement.VALIDE)
                        .flatMap(statut -> rapprochementRepository.findByOperationBancaireIdAndStatut(operationId, statut).stream())
                        .anyMatch(r -> "CARTE_ENTREPRISE".equals(r.getModeRapprochement())
                            || PaiementAchatRapprochementService.MANUEL_CARTE.equals(r.getModeRapprochement()))))
                throw new RegleMetierException("OPERATION_NON_ELIGIBLE", "Une opération ignorée ou réservée pour une pièce carte n’est pas disponible.");
            if (montant.signum()<=0 || montant.add(rapprochementRepository.sommeMontantParOperationEtStatut(op.getId(),StatutRapprochement.VALIDE)).compareTo(montantOperation(op))>0)
                throw new RegleMetierException("MONTANT_SUPERIEUR_OPERATION","Le montant dépasse le disponible bancaire.");
            var r=new RapprochementBancaire();r.setTenantId(c.tenantId());r.setSocieteInterneId(c.societeCouranteId());r.setOperationBancaire(op);
            r.setTypeFacture(type);if (type==TypeFactureRapprochement.SALAIRE) r.setSalaireId(pieceId);else r.setFactureId(pieceId);
            r.setTypePiece(type==TypeFactureRapprochement.SALAIRE ? TypePieceRapprochement.SALAIRE : TypePieceRapprochement.FACTURE_VENTE);
            r.setDocumentAssocieNumero(numeroPiece);
            r.setMontantRapproche(montant);r.setEcart(BigDecimal.ZERO);r.setModeRapprochement(mode);r.setStatut(StatutRapprochement.PROPOSE);
            r.setLibelleRapprochement(mode+"-OP-"+op.getId()+"-"+type+"-"+pieceId);r.setDateRapprochement(LocalDateTime.now());r.setUtilisateurId(c.utilisateurId());
            return rapprochementRepository.saveAndFlush(r).getId();
        });
        if (transactionAssistant.execute(st -> trouverRapprochement(c,id).getStatut()==StatutRapprochement.VALIDE)) return id;
        transactionAssistant.execute(st -> { trouverRapprochement(c,id).setPaiementEngage(true);return null; });
        transactionAssistant.execute(st -> { validerProposition(c,trouverRapprochement(c,id));return null; });return id;
    }

    private void journaliserAnomalieAssistant(ContexteRequete c,Long operationId,String code) {
        try { transactionAssistant.execute(st -> {
            for (var r:rapprochementRepository.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(),c.societeCouranteId()))
                if (r.getOperationBancaire().getId().equals(operationId) && r.getStatut()==StatutRapprochement.PROPOSE && r.isPaiementEngage())
                    r.setCommentaire("Assistant : "+code+". Reprendre les mêmes références ; ne pas recréer de pièce.");
            return null;
        }); } catch (RuntimeException indisponible) {
            org.slf4j.LoggerFactory.getLogger(TresorerieService.class).warn("Journal assistant indisponible : opération {}, code {}",operationId,code);
        }
    }

    private RapprochementBancaire intentionCreation(ContexteRequete c,OperationBancaire op) {
        return rapprochementRepository.findByOperationBancaireIdAndStatut(op.getId(),StatutRapprochement.PROPOSE).stream()
            .filter(r -> r.getTenantId().equals(c.tenantId()) && r.getSocieteInterneId().equals(c.societeCouranteId()) && "CREATION_PIECE".equals(r.getModeRapprochement())).findFirst().orElseGet(RapprochementBancaire::new);
    }

    private boolean traceCorrespondAuChoix(RapprochementBancaire trace, Etape etape, Choix choix) {
        Etape etapeOrigine=etapeTrace(trace);
        if ("CREATION".equals(choix.candidatCle())) return etapeOrigine==Etape.PIECES;
        if (etapeOrigine==Etape.NOTES_FRAIS && etape!=Etape.NOTES_FRAIS) {
            return etape==Etape.FOURNISSEURS && choix.candidatCle().startsWith("C:")
                    || etape==Etape.SALAIRES && PaiementAchatRapprochementService.remboursement(trace.getOperationBancaire());
        }
        return etapeOrigine==etape;
    }

    private Etape etapeTrace(RapprochementBancaire r) {
        if ("CREATION_PIECE".equals(r.getModeRapprochement()) || "PIECE_TRESORERIE".equals(r.getModeRapprochement()) || "FACTURE_RAPPROCHEMENT".equals(r.getModeRapprochement())) return Etape.PIECES;
        if ("CARTE_ENTREPRISE".equals(r.getModeRapprochement()) || PaiementAchatRapprochementService.remboursement(r.getOperationBancaire())) return Etape.NOTES_FRAIS;
        return r.getTypeFacture()==TypeFactureRapprochement.SALAIRE ? Etape.SALAIRES : r.getTypeFacture()==TypeFactureRapprochement.VENTE ? Etape.CLIENTS : Etape.FOURNISSEURS;
    }

    private List<String> raisonsPieceAutomatique(ContexteRequete c,OperationBancaire o,TiersTresorerieDto t,
            List<RapprochementBancaire> traces,Map<String,List<FactureOuverteRapprochementDto>> cache) {
        var raisons=new ArrayList<String>();
        if (t==null) return List.of("Tiers actif à identifier à l'étape 1");
        if (!Boolean.TRUE.equals(t.pieceRapprochementAutorisee())) raisons.add("Création non autorisée dans la fiche Tiers");
        if (o.isRapprochee() || o.getStatutRapprochement()!=StatutOperationBancaire.NON_RAPPROCHEE) raisons.add("Opération déjà rapprochée ou partiellement rapprochée");
        if (!"EUR".equals(o.getCompteBancaire().getDevise())) raisons.add("Devise incompatible avec les pièces générées en EUR");
        if (!List.of(StatutClassificationOperation.CLASSEE_AUTO,StatutClassificationOperation.CLASSEE_MANUELLE).contains(o.getStatutClassification()) || o.getNatureOperation()==NatureOperationBancaire.AUTRE) raisons.add("Nature bancaire ambiguë");
        if (montantOperation(o).signum()<=0) raisons.add("Montant positif obligatoire");
        if (o.getTypeTiersReconnu()!=ClassificationOperationBancaireService.typeTiers(t.roles())) raisons.add("Rôle du tiers incohérent : vérifier l'identification");
        if (traces.stream().anyMatch(r -> r.getOperationBancaire().getId().equals(o.getId()) && r.getStatut()!=StatutRapprochement.ANNULE
                && !"CREATION_PIECE".equals(r.getModeRapprochement()))) raisons.add("Pièce, rapprochement ou paiement déjà réservé");
        boolean fiscal=List.of(NatureOperationBancaire.IMPOT,NatureOperationBancaire.TVA,NatureOperationBancaire.URSSAF).contains(o.getNatureOperation());
        boolean sansTva=fiscal || List.of(NatureOperationBancaire.APPORT_CAPITAL,NatureOperationBancaire.COMPTE_COURANT,NatureOperationBancaire.EMPRUNT,NatureOperationBancaire.VIREMENT_INTERNE).contains(o.getNatureOperation());
        if (fiscal && ClassificationOperationBancaireService.typeTiers(t.roles())!=TypeTiersReconnu.ADMINISTRATION) raisons.add("Organisme fiscal ou social attendu pour cette nature");
        if (!sansTva) {
            boolean exonere=t.tva()!=null && List.of("EXONERATION","EXONERE","NON_ASSUJETTI").contains(t.tva().trim().toUpperCase(java.util.Locale.ROOT));
            boolean fraisExoneres=o.getNatureOperation()==NatureOperationBancaire.FRAIS_BANCAIRES && exonere;
            var typePiece = determinerTypePiece(o);
            boolean achatExonere = typePiece==TypePieceRapprochement.FACTURE_ACHAT && exonere
                    && o.getTypeOperation()==TypeOperationBancaire.VIREMENT_EMIS;
            boolean venteExoneree = typePiece==TypePieceRapprochement.FACTURE_VENTE && exonere
                    && o.getTypeOperation()==TypeOperationBancaire.VIREMENT_RECU;
            if (!fraisExoneres && !achatExonere && !venteExoneree)
                raisons.add("TVA zéro et mode virement non justifiés par les informations disponibles : vérification requise");
        }
        var type=o.getSens()==SensOperation.CREDIT ? TypeFactureRapprochement.VENTE : TypeFactureRapprochement.ACHAT;
        var ouvertes=cache.computeIfAbsent("PIECE:"+type+":"+t.id(),k -> facturesClient.listerFacturesOuvertes(c,type,t.id()));
        if (!ouvertes.isEmpty()) raisons.add("Pièce comptable ouverte existante : examiner les étapes précédentes avant de créer");
        if (PaiementAchatRapprochementService.remboursement(o) && !facturesClient.listerFacturesAchatNotesFraisOuvertes(c,t.id(),null).isEmpty()) raisons.add("Remboursement existant à rapprocher");
        if (o.getTypeTiersReconnu()==TypeTiersReconnu.SALARIE) {
            var periode=periodeSalaireOperation(o.getDateOperation());
            if (!facturesClient.listerSalairesOuverts(c,t.id(),periode.getYear(),periode.getMonthValue(),null).isEmpty()) raisons.add("Pièce de paie existante");
        }
        return List.copyOf(raisons);
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
        if (typeFacture == TypeFactureRapprochement.ACHAT && reprendrePaiementsEngages(contexte, operation))
            return new ResultatRapprochementOperation(1, 0, 0, List.of("Paiement repris sans doublon."));
        // Les pièces carte passent toujours par le choix explicite du parcours carte, jamais par le score automatique.
        Set<Long> cartes = typeFacture == TypeFactureRapprochement.ACHAT
                ? facturesClient.listerCartesOuvertes(contexte).stream().map(PieceCarteRapprochementDto::id).collect(Collectors.toSet())
                : Set.of();
        List<FactureOuverteRapprochementDto> factures = facturesClient.listerFacturesOuvertes(contexte, typeFacture, operation.getTiersId())
                .stream().filter(f -> typeFacture != TypeFactureRapprochement.ACHAT || !cartes.contains(f.id())).toList();
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

        long equivalents=factures.stream().filter(f -> differenceMontant(montant,f.resteAPayer()).compareTo(TOLERANCE_MONTANT)<=0)
                .filter(f -> score(operation,f,montant)==meilleur.score()).count();
        if (equivalents>1) return new ResultatRapprochementOperation(0,0,1,List.of("Plusieurs factures équivalentes : consultez l'assistant avant confirmation."));
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
            confirmerDocumentAssistant(contexte,operation.getId(),exact.id(),exact.numeroFacture(),TypeFactureRapprochement.SALAIRE,montant);
            entityManager.refresh(operation);
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
        if (reprendrePaiementsEngages(contexte, operation))
            return new ResultatRapprochementOperation(1, 0, 0, List.of("Remboursement repris sans doublon."));
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
        entityManager.refresh(operation, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verifierOperationEligiblePieceRapprochement(operation);
        if (rapprochementRepository.findByOperationBancaireIdAndStatut(id, StatutRapprochement.PROPOSE).stream()
                .anyMatch(r -> r.getTypeFacture() == TypeFactureRapprochement.ACHAT && !"CREATION_PIECE".equals(r.getModeRapprochement())))
            throw new RegleMetierException("RAPPROCHEMENT_EXISTANT", "Une proposition achat existe : la valider ou l'annuler avant de créer une autre pièce.");
        if (PaiementAchatRapprochementService.remboursement(operation)
                && !paiementsAchats.candidates(contexte, operation).isEmpty())
            throw new RegleMetierException("REMBOURSEMENT_REEL_EXISTANT", "Une facture de remboursement existe : la rapprocher en priorité.");
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
        String libelleRapprochement = intentionCreation(contexte,operation).getId()!=null ? intentionCreation(contexte,operation).getLibelleRapprochement() : "RAP-%s-%s-OP-%s".formatted(
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

        RapprochementBancaire rapprochement = intentionCreation(contexte, operation);
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
        rapprochement.setDateValidation(maintenant);rapprochement.setUtilisateurValidationId(contexte.utilisateurId());
        rapprochement.setCommentaire("Pièce autorisée créée ou reprise ; rapprochement confirmé.");
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

        RapprochementBancaire rapprochement = intentionCreation(contexte, operation);
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
            verifierOperationModifiable(operation);
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
        OperationBancaire operation = trouverOperationActive(contexte, demande.operationBancaireId());
        if (demande.typeFacture() == TypeFactureRapprochement.ACHAT
                || (operation.getSens() == SensOperation.DEBIT && !operationEligibleRapprochementSalaire(operation))) {
            if (demande.typeFacture() != TypeFactureRapprochement.ACHAT || demande.factureId() == null
                    || (demande.statut() != null && demande.statut() != StatutRapprochement.PROPOSE && demande.statut() != StatutRapprochement.VALIDE))
                throw new RegleMetierException("RAPPROCHEMENT_ACHAT_INVALIDE", "Une facture d'achat et un statut proposé ou validé sont obligatoires.");
            Long id = paiementsAchats.preparer(contexte, operation.getId(), demande.factureId(), demande.montantRapproche(),
                    100, libelleRapprochement(operation, String.valueOf(demande.factureId())), "MANUEL");
            if (demande.statut() == StatutRapprochement.VALIDE) paiementsAchats.valider(contexte, id);
            entityManager.refresh(operation);
            return mapper.versReponse(trouverRapprochement(contexte, id));
        }
        RapprochementBancaire rapprochement = new RapprochementBancaire();
        rapprochement.setTenantId(contexte.tenantId());
        rapprochement.setSocieteInterneId(contexte.societeCouranteId());
        rapprochement.setUtilisateurId(contexte.utilisateurId());
        appliquer(contexte, rapprochement, demande);
        return mapper.versReponse(rapprochementRepository.save(rapprochement));
    }

    @Transactional(readOnly = true)
    public List<FactureOuverteRapprochementDto> listerFacturesCandidates(ContexteRequete contexte, Long operationId) {
        return piecesManuelles(contexte, trouverOperationActive(contexte, operationId));
    }

    private List<FactureOuverteRapprochementDto> piecesManuelles(ContexteRequete contexte, OperationBancaire operation) {
        if (operation.isRapprochee() || operation.getStatutRapprochement() == StatutOperationBancaire.IGNOREE
                || operation.getTiersId() == null) return List.of();
        Long tiersId = operation.getTiersId();
        Set<String> roles = tiersClient.listerTiersActifs(contexte).stream()
                .filter(t -> tiersId.equals(t.id()) && !Boolean.FALSE.equals(t.actif()) && t.roles() != null)
                .map(t -> t.roles()).findFirst().orElse(Set.of());
        List<FactureOuverteRapprochementDto> pieces = new ArrayList<>();
        if (operation.getSens() == SensOperation.CREDIT && roles.contains("CLIENT"))
            pieces.addAll(facturesClient.listerFacturesOuvertes(contexte, TypeFactureRapprochement.VENTE, tiersId));
        if (operation.getSens() == SensOperation.DEBIT && roles.contains("SALARIE")) {
            pieces.addAll(facturesClient.listerFacturesOuvertes(contexte, TypeFactureRapprochement.SALAIRE, tiersId));
            for (var piece : facturesClient.listerFacturesAchatNotesFraisOuvertes(contexte, tiersId, null))
                pieces.add(new FactureOuverteRapprochementDto(piece.id(), piece.typeFacture(), piece.tiersId(),
                        piece.numeroFacture(), piece.dateFacture(), piece.dateEcheance(), piece.resteAPayer(),
                        piece.libelle(), piece.nomTiers(), piece.montantTotal(), piece.statutPaiement(), "REMBOURSEMENT_BENEFICIAIRE"));
        }
        if (operation.getSens() == SensOperation.DEBIT && ClassificationOperationBancaireService.roleFournisseur(roles))
            pieces.addAll(facturesClient.listerFacturesOuvertes(contexte, TypeFactureRapprochement.ACHAT, tiersId));
        var valides = rapprochementRepository.findByOperationBancaireIdAndStatut(operation.getId(), StatutRapprochement.VALIDE);
        return pieces.stream().filter(piece -> tiersId.equals(piece.tiersId()) && piece.resteAPayer() != null
                && piece.resteAPayer().signum() > 0 && valides.stream().noneMatch(r ->
                    r.getTypeFacture() != null && r.getTypeFacture().name().equals(piece.typeFacture())
                    && piece.id().equals(r.getTypeFacture() == TypeFactureRapprochement.SALAIRE ? r.getSalaireId() : r.getFactureId())))
                .toList();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public RapprochementManuelResultat rapprocherManuellement(
            ContexteRequete contexte, Long operationId, RapprochementManuelDemande demande) {
        var operation = trouverOperationActive(contexte, operationId);
        var lignes = demande.lignes();
        if (lignes.stream().map(l -> l.typeFacture() + ":" + l.factureId()).distinct().count() != lignes.size())
            throw new RegleMetierException("FACTURE_DUPLIQUEE", "Une pièce ne peut être sélectionnée qu’une fois.");
        var traces = rapprochementRepository.findByOperationBancaireIdAndStatut(operationId, StatutRapprochement.PROPOSE);
        var reprise = traces.stream().filter(RapprochementBancaire::isPaiementEngage).findFirst();
        if (reprise.isPresent()) {
            var trace = reprise.get();
            var ligne = lignes.get(0);
            Long pieceId = trace.getTypeFacture() == TypeFactureRapprochement.SALAIRE ? trace.getSalaireId() : trace.getFactureId();
            if (lignes.size() != 1 || !java.util.Objects.toString(trace.getModeRapprochement(), "").startsWith("MANUEL")
                    || !ligne.factureId().equals(pieceId) || ligne.typeFacture() != trace.getTypeFacture()
                    || trace.getMontantRapproche().compareTo(ligne.montantRapproche()) != 0)
                throw new RegleMetierException("REPRISE_DIFFERENTE", "Reprenez uniquement la pièce et le montant du paiement engagé.");
            if (ligne.typeFacture() == TypeFactureRapprochement.ACHAT) paiementsAchats.valider(contexte, trace.getId());
            else confirmerDocument(contexte, operationId, pieceId, trace.getDocumentAssocieNumero(),
                    ligne.typeFacture(), ligne.montantRapproche(), "MANUEL");
            return resultatManuel(contexte, operationId);
        }

        var candidates = piecesManuelles(contexte, operation);
        var actions = new ArrayList<Runnable>();
        BigDecimal ajout = BigDecimal.ZERO;
        for (var ligne : lignes) {
            BigDecimal montant = ligne.montantRapproche();
            if (montant.signum() <= 0 || montant.scale() > 2)
                throw new RegleMetierException("MONTANT_INVALIDE", "Un montant positif à deux décimales au plus est obligatoire.");
            var piece = candidates.stream().filter(f -> f.id().equals(ligne.factureId())
                    && ligne.typeFacture().name().equals(f.typeFacture())).findFirst()
                    .orElseThrow(() -> new RegleMetierException("FACTURE_NON_CANDIDATE", "La pièce n’est plus ouverte ou le tiers ne correspond pas."));
            if (montant.compareTo(piece.resteAPayer()) > 0)
                throw new RegleMetierException("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant dépasse le solde de la pièce.");
            ajout = ajout.add(montant);
            if ("DEPENSE_CARTE_ENTREPRISE".equals(piece.typePiece())) {
                if (lignes.size() != 1) throw new RegleMetierException("CONFLIT_CARTE", "Sélectionnez la pièce carte seule.");
                actions.add(() -> paiementsAchats.confirmerCarteManuellement(contexte, operationId, piece.id(), montant));
            } else if (ligne.typeFacture() == TypeFactureRapprochement.ACHAT) {
                String mode = "REMBOURSEMENT_BENEFICIAIRE".equals(piece.typePiece())
                        ? PaiementAchatRapprochementService.MANUEL_REMBOURSEMENT : PaiementAchatRapprochementService.MANUEL_FOURNISSEUR;
                actions.add(() -> paiementsAchats.valider(contexte, paiementsAchats.preparer(contexte, operationId, piece.id(),
                        montant, 0, libelleRapprochement(operation, String.valueOf(piece.id())), mode)));
            } else {
                actions.add(() -> confirmerDocument(contexte, operationId, piece.id(), piece.numeroFacture(), ligne.typeFacture(), montant, "MANUEL"));
            }
        }
        if (ajout.add(rapprochementRepository.sommeMontantParOperationEtStatut(operationId, StatutRapprochement.VALIDE))
                .compareTo(montantOperation(operation)) > 0)
            throw new RegleMetierException("MONTANT_SUPERIEUR_OPERATION", "Le total dépasse le disponible bancaire.");
        actions.forEach(Runnable::run);
        return resultatManuel(contexte, operationId);
    }

    private RapprochementManuelResultat resultatManuel(ContexteRequete contexte, Long operationId) {
        return transactionAssistant.execute(st -> {
            var operation = trouverOperationActive(contexte, operationId);
            BigDecimal total = rapprochementRepository.sommeMontantParOperationEtStatut(operationId, StatutRapprochement.VALIDE);
            BigDecimal montant = montantOperation(operation);
            return new RapprochementManuelResultat(operationId, montant, total, montant.subtract(total), operation.getStatutRapprochement());
        });
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

    public RapprochementBancaireReponse annulerRapprochementValide(ContexteRequete contexte, Long id) {
        var rapprochement = trouverRapprochement(contexte, id);
        var operation = trouverOperation(contexte, rapprochement.getOperationBancaire().getId());
        entityManager.refresh(operation, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(rapprochement);
        if (rapprochement.getStatut() == StatutRapprochement.ANNULE && MOTIF_ANNULATION_VALIDE.equals(rapprochement.getMotifAnnulation()))
            return mapper.versReponse(rapprochement);
        if (rapprochement.getStatut() != StatutRapprochement.VALIDE)
            throw new RegleMetierException("RAPPROCHEMENT_STATUT_INVALIDE", "Seul un rapprochement validé peut être annulé par cette action.");

        var type = rapprochement.getTypeFacture();
        Long pieceId = type == TypeFactureRapprochement.SALAIRE ? rapprochement.getSalaireId() : rapprochement.getFactureId();
        String reference = rapprochement.getLibelleRapprochement();
        if (type == null || pieceId == null
                || rapprochement.getPieceRapprochementId() != null
                || (type == TypeFactureRapprochement.ACHAT && pieceId.equals(operation.getFactureRapprochementId()))
                || reference == null || reference.isBlank()) {
            throw new RegleMetierException("PAIEMENT_ORIGINE_NON_IDENTIFIE", "Le paiement d’origine n’a pas pu être identifié avec certitude.");
        }

        // Le lien garde sa réservation tant que Factures n'a pas confirmé. Une reprise
        // après interruption renvoie exactement le même reçu, déjà annulé le cas échéant.
        facturesClient.annulerPaiement(contexte, type, pieceId, new AnnulationPaiementRapprochementDto(
                id, operation.getId(), rapprochement.getMontantRapproche(), reference));

        var maintenant = LocalDateTime.now();
        rapprochement.setStatut(StatutRapprochement.ANNULE);
        rapprochement.setDateAnnulation(maintenant);
        rapprochement.setUtilisateurAnnulationId(contexte.utilisateurId());
        rapprochement.setMotifAnnulation(MOTIF_ANNULATION_VALIDE);
        entityManager.flush();
        var restants = rapprochementRepository.findByOperationBancaireIdAndStatut(operation.getId(), StatutRapprochement.VALIDE);
        BigDecimal total = restants.stream().map(RapprochementBancaire::getMontantRapproche).reduce(BigDecimal.ZERO, BigDecimal::add);
        var dernier = restants.stream().max(Comparator
                .comparing((RapprochementBancaire r) -> r.getDateValidation() == null ? r.getDateRapprochement() : r.getDateValidation())
                .thenComparing(RapprochementBancaire::getId)).orElse(null);
        boolean complet = total.signum() > 0 && total.compareTo(montantOperation(operation)) >= 0;
        operation.setRapprochee(complet);
        operation.setStatutRapprochement(complet ? StatutOperationBancaire.RAPPROCHEE
                : total.signum() > 0 ? StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE : StatutOperationBancaire.NON_RAPPROCHEE);
        operation.setFactureId(dernier == null ? null : dernier.getFactureId());
        operation.setSalaireId(dernier == null ? null : dernier.getSalaireId());
        operation.setTypeFacture(dernier == null ? null : dernier.getTypeFacture());
        operation.setLibelleRapprochement(dernier == null ? null : dernier.getLibelleRapprochement());
        operation.setDateRapprochement(dernier == null ? null
                : dernier.getDateValidation() == null ? dernier.getDateRapprochement() : dernier.getDateValidation());
        operation.setDateModification(maintenant);
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
        boolean identiteImportee = operation.getImportReleveBancaire() != null && operation.getHashOperation() != null;
        if (identiteImportee && !operation.getCompteBancaire().getId().equals(demande.compteBancaireId())) {
            throw new RegleMetierException("COMPTE_IMPORT_IMMUABLE", "Le compte d’une operation importee ne peut pas etre modifie.");
        }
        operation.setCompteBancaire(trouverCompte(contexte, demande.compteBancaireId()));
        if (!identiteImportee) {
            operation.setImportReleveBancaire(demande.importReleveBancaireId() == null ? null : trouverImport(contexte, demande.importReleveBancaireId()));
        }
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
        if ((demande.statutRapprochement()!=null && demande.statutRapprochement()!=operation.getStatutRapprochement())
                || !java.util.Objects.equals(demande.factureId(),operation.getFactureId())
                || !java.util.Objects.equals(demande.typeFacture(),operation.getTypeFacture()))
            throw new RegleMetierException("RAPPROCHEMENT_PAR_SERVICE_REQUIS", "Le statut et les pièces liées sont gérés par le service de rapprochement.");
        operation.setStatutRapprochement(demande.statutRapprochement() == null ? StatutOperationBancaire.NON_RAPPROCHEE : demande.statutRapprochement());
        operation.setRapprochee(operation.getStatutRapprochement() == StatutOperationBancaire.RAPPROCHEE);
        operation.setTiersId(demande.tiersId());
        operation.setFactureId(demande.factureId());
        operation.setTypeFacture(demande.typeFacture());
        // Import identities remain stable when editable business fields change.
        if (!identiteImportee) {
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
        }
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
        return classificationService.chargerRegles(contexte);
    }
    private List<RegleClassificationOperation> reglesReconnaissanceTiersOperationBancaire(ContexteRequete contexte) {
        return classificationService.chargerRegles(contexte);
    }

    private CompteBancaire trouverCompte(ContexteRequete contexte, Long id) {
        return compteRepository.findByIdAndTenantIdAndSocieteInterneId(id, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Compte bancaire introuvable."));
    }

    private void verifierOperationModifiable(OperationBancaire operation) {
        entityManager.refresh(operation, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (operation.isRapprochee() || rapprochementRepository.findByOperationBancaireIdAndStatut(operation.getId(), StatutRapprochement.PROPOSE)
                .stream().anyMatch(RapprochementBancaire::isPaiementEngage))
            throw new RegleMetierException("OPERATION_PAIEMENT_ENGAGE", "L'opération rapprochée ou en cours de paiement ne peut pas être modifiée.");
    }

    private boolean reprendrePaiementsEngages(ContexteRequete contexte, OperationBancaire operation) {
        var ids = rapprochementRepository.findByOperationBancaireIdAndStatut(operation.getId(), StatutRapprochement.PROPOSE).stream()
                .filter(r -> r.getTypeFacture() == TypeFactureRapprochement.ACHAT && r.isPaiementEngage())
                .map(RapprochementBancaire::getId).toList();
        for (Long id : ids) paiementsAchats.valider(contexte, id);
        if (!ids.isEmpty()) entityManager.refresh(operation);
        return !ids.isEmpty();
    }

    private boolean operationEligibleRapprochementFacture(OperationBancaire operation) {
        if (operation.isRapprochee() || operation.getStatutRapprochement() != StatutOperationBancaire.NON_RAPPROCHEE || operation.getTiersId() == null) {
            return false;
        }
        return (operation.getSens() == SensOperation.CREDIT && operation.getNatureOperation() == NatureOperationBancaire.FACTURE_CLIENT)
                || (operation.getSens() == SensOperation.DEBIT && operation.getNatureOperation() == NatureOperationBancaire.FACTURE_FOURNISSEUR)
                || (operation.getSens() == SensOperation.DEBIT
                    && operation.getNatureOperation() == NatureOperationBancaire.SALAIRE
                    && operation.getTypeTiersReconnu() == TypeTiersReconnu.SALARIE
                    && !PaiementAchatRapprochementService.remboursement(operation));
    }

    private boolean operationEligibleRapprochementSalaire(OperationBancaire operation) {
        return !operation.isRapprochee()
                && operation.getStatutRapprochement() == StatutOperationBancaire.NON_RAPPROCHEE
                && operation.getSens() == SensOperation.DEBIT
                && operation.getNatureOperation() == NatureOperationBancaire.SALAIRE
                && !PaiementAchatRapprochementService.remboursement(operation)
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
        return PaiementAchatRapprochementService.remboursement(operation);
    }

    private int score(OperationBancaire operation, FactureOuverteRapprochementDto facture, BigDecimal montantOperation) {
        int score = 0;
        if (differenceMontant(montantOperation, facture.resteAPayer()).compareTo(TOLERANCE_MONTANT) <= 0) {
            score += 50;
        }
        LocalDate achat=RapprochementCarteMatcher.dateAchat(operation.getLibelle(),operation.getDateOperation());
        if (achat!=null && facture.dateFacture()!=null) {
            long jours=Math.abs(ChronoUnit.DAYS.between(facture.dateFacture(),achat));
            if (jours==0) score+=35; else if (jours<=7) score+=25;
        } else if (facture.dateEcheance()!=null && Math.abs(ChronoUnit.DAYS.between(facture.dateEcheance(),operation.getDateOperation()))<=7) score+=30;
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
        if (typeFacture == TypeFactureRapprochement.ACHAT) {
            List<Long> ids = new ArrayList<>();
            for (var facture : factures) ids.add(paiementsAchats.preparer(contexte, operation.getId(), facture.id(), facture.resteAPayer(),
                    score, libelle, "RAPPROCHEMENT_FACTURES"));
            for (Long id : ids) paiementsAchats.valider(contexte, id);
            entityManager.refresh(operation);
            return;
        }
        for (var facture:factures) confirmerDocumentAssistant(contexte,operation.getId(),facture.id(),facture.numeroFacture(),typeFacture,facture.resteAPayer());
        entityManager.refresh(operation);
    }

    private void validerProposition(ContexteRequete contexte, RapprochementBancaire rapprochement) {
        if (rapprochement.getTypeFacture() == TypeFactureRapprochement.ACHAT) {
            paiementsAchats.valider(contexte, rapprochement.getId());
            entityManager.refresh(rapprochement.getOperationBancaire()); entityManager.refresh(rapprochement);
            return;
        }
        verifierPropositionModifiable(rapprochement, "valider");
        if (rapprochement.getTypeFacture() == TypeFactureRapprochement.SALAIRE
                && !"MANUEL".equals(rapprochement.getModeRapprochement())
                && PaiementAchatRapprochementService.remboursement(rapprochement.getOperationBancaire()))
            throw new RegleMetierException("REMBOURSEMENT_NON_SALAIRE", "Un remboursement de frais ne peut pas payer un salaire.");
        Long documentId = rapprochement.getTypeFacture() == TypeFactureRapprochement.SALAIRE
                ? rapprochement.getSalaireId() : rapprochement.getFactureId();
        if (documentId == null || rapprochement.getTypeFacture() == null) {
            throw new RegleMetierException("FACTURE_RAPPROCHEMENT_OBLIGATOIRE",
                    "La proposition doit etre liee a une facture pour etre validee.");
        }
        OperationBancaire operation = rapprochement.getOperationBancaire();
        entityManager.refresh(operation,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(rapprochement);
        verifierPropositionModifiable(rapprochement,"valider");
        BigDecimal reserve=rapprochementRepository.sommeMontantParOperationEtStatut(operation.getId(),StatutRapprochement.VALIDE);
        if (reserve.add(rapprochement.getMontantRapproche()).compareTo(montantOperation(operation))>0)
            throw new RegleMetierException("MONTANT_SUPERIEUR_OPERATION","Le montant dépasse le solde bancaire.");
        if ((rapprochement.getTypeFacture()==TypeFactureRapprochement.VENTE && (operation.getSens()!=SensOperation.CREDIT
                || !tiersDeDocumentCompatible(contexte, operation, TypeTiersReconnu.CLIENT, rapprochement.getModeRapprochement())))
            || (rapprochement.getTypeFacture()==TypeFactureRapprochement.SALAIRE && (operation.getSens()!=SensOperation.DEBIT
                || !tiersDeDocumentCompatible(contexte, operation, TypeTiersReconnu.SALARIE, rapprochement.getModeRapprochement()))))
            throw new RegleMetierException("TIERS_INCOMPATIBLE","Le tiers et le sens bancaire ne correspondent pas à la pièce.");
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
                new EnregistrementPaiementFactureDto(montantRapproche, operation.getDateOperation(), libelle,
                    operation.getId(),rapprochement.getId(),operation.getTiersId(),false)
        );

        BigDecimal totalValide = rapprochementRepository.sommeMontantParOperationEtStatut(operation.getId(), StatutRapprochement.VALIDE)
                .add(montantRapproche);
        BigDecimal montantOperation = montantOperation(operation);
        boolean complet = totalValide.compareTo(montantOperation)==0;

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
        if (rapprochement.getTypeFacture() == TypeFactureRapprochement.ACHAT) {
            paiementsAchats.annuler(contexte, rapprochement.getId(), motif); entityManager.refresh(rapprochement); return;
        }
        verifierPropositionModifiable(rapprochement, "annuler");
        LocalDateTime maintenant = LocalDateTime.now();
        rapprochement.setStatut(StatutRapprochement.ANNULE);
        rapprochement.setDateAnnulation(maintenant);
        rapprochement.setUtilisateurAnnulationId(contexte.utilisateurId());
        rapprochement.setMotifAnnulation(motif);
    }

    private void verifierPropositionModifiable(RapprochementBancaire rapprochement, String action) {
        if ("annuler".equals(action) && rapprochement.isPaiementEngage())
            throw new RegleMetierException("PAIEMENT_A_REPRENDRE", "Le paiement est engagé : reprendre sa validation avant toute autre action.");
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
        if (typeFacture == TypeFactureRapprochement.ACHAT) {
            Long id = paiementsAchats.preparer(contexte, operation.getId(), facture.id(), montant, score, libelle, mode);
            if (statut == StatutRapprochement.VALIDE) paiementsAchats.valider(contexte, id);
            entityManager.refresh(operation);
            return;
        }
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

    private boolean tiersDeDocumentCompatible(ContexteRequete c, OperationBancaire op, TypeTiersReconnu domaine, String mode) {
        if ("MANUEL".equals(mode)) return op.getTiersId() != null && tiersClient.listerTiersActifs(c).stream()
                .anyMatch(t -> op.getTiersId().equals(t.id()) && !Boolean.FALSE.equals(t.actif())
                        && t.roles() != null && t.roles().contains(domaine.name()));
        if (op.getTypeTiersReconnu()==domaine) return true;
        return "ASSISTANT".equals(mode) && op.getTiersId()!=null && tiersClient.listerTiersActifs(c).stream()
                .anyMatch(t -> op.getTiersId().equals(t.id()) && !Boolean.FALSE.equals(t.actif())
                        && t.roles()!=null && t.roles().contains(domaine.name()));
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
