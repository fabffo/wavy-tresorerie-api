package fr.wavy.tresorerie.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import fr.wavy.tresorerie.client.TiersTresorerieClient;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.ImportReleveResultatDto;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;
import fr.wavy.tresorerie.exception.RegleMetierException;
import fr.wavy.tresorerie.exception.RessourceNonTrouveeException;
import fr.wavy.tresorerie.modele.CompteBancaire;
import fr.wavy.tresorerie.modele.FormatImportReleve;
import fr.wavy.tresorerie.modele.ImportReleveBancaire;
import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutImportReleve;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;
import fr.wavy.tresorerie.repository.CompteBancaireRepository;
import fr.wavy.tresorerie.repository.ImportReleveBancaireRepository;
import fr.wavy.tresorerie.repository.OperationBancaireRepository;
import fr.wavy.tresorerie.service.ClassificationOperationBancaireService.RegleClassificationOperation;
import fr.wavy.tresorerie.service.importreleve.LigneReleveBancaire;
import fr.wavy.tresorerie.service.importreleve.ReleveBancaireCsvParser;
import fr.wavy.tresorerie.service.importreleve.ReleveBancaireExcelParser;
import fr.wavy.tresorerie.service.importreleve.ResultatParsingReleve;

@Service
@Transactional
public class ImportReleveBancaireService {
    private final CompteBancaireRepository compteRepository;
    private final ImportReleveBancaireRepository importRepository;
    private final OperationBancaireRepository operationRepository;
    private final ReleveBancaireCsvParser csvParser;
    private final ReleveBancaireExcelParser excelParser;
    private final ClassificationOperationBancaireService classificationService;
    private final TiersTresorerieClient tiersClient;

    public ImportReleveBancaireService(
            CompteBancaireRepository compteRepository,
            ImportReleveBancaireRepository importRepository,
            OperationBancaireRepository operationRepository,
            ReleveBancaireCsvParser csvParser,
            ReleveBancaireExcelParser excelParser,
            ClassificationOperationBancaireService classificationService,
            TiersTresorerieClient tiersClient
    ) {
        this.compteRepository = compteRepository;
        this.importRepository = importRepository;
        this.operationRepository = operationRepository;
        this.csvParser = csvParser;
        this.excelParser = excelParser;
        this.classificationService = classificationService;
        this.tiersClient = tiersClient;
    }

    public ImportReleveResultatDto importer(ContexteRequete contexte, Long compteBancaireId, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new RegleMetierException("FICHIER_OBLIGATOIRE", "Le fichier de releve bancaire est obligatoire.");
        }
        CompteBancaire compte = compteRepository.findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(
                        compteBancaireId, contexte.tenantId(), contexte.societeCouranteId())
                .orElseThrow(() -> new RessourceNonTrouveeException("Compte bancaire introuvable."));

        String nomFichier = fichier.getOriginalFilename() == null ? "releve" : fichier.getOriginalFilename();
        FormatImportReleve typeFichier = typeFichier(nomFichier);
        ResultatParsingReleve parsing = parser(typeFichier, fichier);

        ImportReleveBancaire importReleve = new ImportReleveBancaire();
        importReleve.setTenantId(contexte.tenantId());
        importReleve.setSocieteInterneId(contexte.societeCouranteId());
        importReleve.setCompteBancaire(compte);
        importReleve.setNomFichier(nomFichier);
        importReleve.setTypeFichier(typeFichier);
        importReleve.setUtilisateurImportId(contexte.utilisateurId());
        importReleve.setDateImport(LocalDateTime.now());

        List<String> erreurs = new ArrayList<>(parsing.erreurs());
        int importees = 0;
        int rejetees = parsing.erreurs().size();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        importReleve = importRepository.save(importReleve);
        List<RegleClassificationOperation> reglesSalaries = reglesSalaries(contexte);

        for (LigneReleveBancaire ligne : parsing.lignes()) {
            String hash = hash(contexte, compte.getId(), ligne);
            if (operationRepository.existsByTenantIdAndSocieteInterneIdAndCompteBancaireIdAndHashOperation(
                    contexte.tenantId(), contexte.societeCouranteId(), compte.getId(), hash)) {
                rejetees++;
                erreurs.add("Ligne " + ligne.numeroLigne() + " : doublon");
                continue;
            }
            OperationBancaire operation = new OperationBancaire();
            operation.setTenantId(contexte.tenantId());
            operation.setSocieteInterneId(contexte.societeCouranteId());
            operation.setCompteBancaire(compte);
            operation.setImportReleveBancaire(importReleve);
            operation.setDateOperation(ligne.dateOperation());
            operation.setDateValeur(ligne.dateValeur());
            operation.setLibelle(ligne.libelle());
            operation.setReferenceBancaire(ligne.referenceBancaire());
            operation.setDebit(ligne.debit());
            operation.setCredit(ligne.credit());
            operation.setMontant(ligne.montant());
            operation.setSens(ligne.montant().signum() < 0 ? SensOperation.DEBIT : SensOperation.CREDIT);
            operation.setTypeOperation(deduireTypeOperation(ligne.libelle(), operation.getSens()));
            classificationService.classerAutomatiquement(operation, reglesSalaries);
            operation.setStatutRapprochement(StatutOperationBancaire.NON_RAPPROCHEE);
            operation.setRapprochee(false);
            operation.setNumeroLigneImport(ligne.numeroLigne());
            operation.setHashOperation(hash);
            operation.setDateImport(importReleve.getDateImport());
            operationRepository.save(operation);
            importees++;
            totalDebit = totalDebit.add(ligne.debit());
            totalCredit = totalCredit.add(ligne.credit());
        }

        importReleve.setNombreLignes(parsing.nombreLignes());
        importReleve.setNombreLignesImportees(importees);
        importReleve.setNombreLignesRejetees(rejetees);
        importReleve.setTotalDebit(totalDebit);
        importReleve.setTotalCredit(totalCredit);
        importReleve.setStatutImport(statut(importees, rejetees));
        importReleve.setMessageErreur(erreurs.isEmpty() ? null : String.join("\n", erreurs));

        return new ImportReleveResultatDto(
                importReleve.getId(),
                importReleve.getNomFichier(),
                importReleve.getNombreLignes(),
                importReleve.getNombreLignesImportees(),
                importReleve.getNombreLignesRejetees(),
                importReleve.getTotalDebit(),
                importReleve.getTotalCredit(),
                importReleve.getStatutImport(),
                erreurs
        );
    }

    public TypeOperationBancaire deduireTypeOperation(String libelle, SensOperation sens) {
        String texte = libelle == null ? "" : libelle.toUpperCase(Locale.ROOT);
        if (texte.contains("COTISATION")) return TypeOperationBancaire.COTISATION;
        if (texte.contains("COMMISSION")) return TypeOperationBancaire.COMMISSION;
        if (texte.contains("FRAIS")) return TypeOperationBancaire.FRAIS_BANCAIRES;
        if (texte.contains("CARTE") || texte.contains(" CB") || texte.startsWith("CB")) return TypeOperationBancaire.PAIEMENT_CB;
        if (texte.contains("PRELEVEMENT") || texte.contains("PRLV")) return TypeOperationBancaire.PRELEVEMENT;
        if (texte.contains("RETRAIT") || texte.contains("DAB")) return TypeOperationBancaire.RETRAIT_DAB;
        if (texte.contains("REMISE") && texte.contains("CHEQUE")) return TypeOperationBancaire.REMISE_CHEQUE;
        if (texte.contains("CHEQUE")) return TypeOperationBancaire.CHEQUE;
        if (texte.contains("ESPECES")) return TypeOperationBancaire.ESPECES;
        if (texte.contains("VIR") || texte.contains("VIREMENT")) {
            return sens == SensOperation.CREDIT ? TypeOperationBancaire.VIREMENT_RECU : TypeOperationBancaire.VIREMENT_EMIS;
        }
        return TypeOperationBancaire.AUTRE;
    }

    private List<RegleClassificationOperation> reglesSalaries(ContexteRequete contexte) {
        return tiersClient.listerSalaries(contexte)
                .stream()
                .filter(tiers -> tiers.roles() == null || tiers.roles().contains("SALARIE"))
                .map(this::versRegleClassificationSalarie)
                .toList();
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
                true,
                normaliserTexteLibre(tiers.nom()),
                normaliserTexteLibre(tiers.prenom())
        );
    }

    private String normaliserNomTiersReconnu(String libelle, String motif) {
        String valeur = normaliserTexteLibre(libelle);
        if (valeur != null) {
            return valeur;
        }
        return normaliserTexteLibre(motif);
    }

    private String normaliserTexteLibre(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        return valeur.trim().replaceAll("\\s+", " ");
    }

    public String hash(ContexteRequete contexte, Long compteBancaireId, LigneReleveBancaire ligne) {
        String source = contexte.tenantId() + "|" + contexte.societeCouranteId() + "|" + compteBancaireId + "|"
                + ligne.dateOperation() + "|" + ligne.libelle() + "|" + ligne.montant() + "|"
                + (ligne.referenceBancaire() == null ? "" : ligne.referenceBancaire());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private ResultatParsingReleve parser(FormatImportReleve typeFichier, MultipartFile fichier) {
        try {
            return switch (typeFichier) {
                case CSV -> csvParser.parser(fichier.getInputStream());
                case XLS, XLSX -> excelParser.parser(fichier.getInputStream());
                default -> throw new RegleMetierException("FORMAT_NON_SUPPORTE", "Format de fichier non supporte.");
            };
        } catch (IOException exception) {
            throw new RegleMetierException("IMPORT_ILLISIBLE", "Le fichier de releve bancaire est illisible.");
        }
    }

    private FormatImportReleve typeFichier(String nomFichier) {
        String nom = nomFichier.toLowerCase(Locale.ROOT);
        if (nom.endsWith(".csv")) return FormatImportReleve.CSV;
        if (nom.endsWith(".xls")) return FormatImportReleve.XLS;
        if (nom.endsWith(".xlsx")) return FormatImportReleve.XLSX;
        throw new RegleMetierException("FORMAT_NON_SUPPORTE", "Le fichier doit etre au format CSV, XLS ou XLSX.");
    }

    private StatutImportReleve statut(int importees, int rejetees) {
        if (importees == 0 && rejetees > 0) return StatutImportReleve.ECHEC;
        if (rejetees > 0) return StatutImportReleve.IMPORTE_AVEC_ERREURS;
        return StatutImportReleve.IMPORTE;
    }
}
