package fr.wavy.tresorerie.modele;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "operation_bancaire",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_operation_bancaire_hash",
                columnNames = {"tenant_id", "societe_interne_id", "compte_bancaire_id", "hash_operation"}
        )
)
public class OperationBancaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "societe_interne_id", nullable = false)
    private Long societeInterneId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "compte_bancaire_id", nullable = false)
    private CompteBancaire compteBancaire;
    @ManyToOne
    @JoinColumn(name = "import_releve_bancaire_id")
    private ImportReleveBancaire importReleveBancaire;
    @Column(name = "date_operation", nullable = false)
    private LocalDate dateOperation;
    @Column(name = "date_valeur")
    private LocalDate dateValeur;
    @Column(nullable = false, length = 500)
    private String libelle;
    @Column(name = "reference_bancaire", length = 150)
    private String referenceBancaire;
    @Column(nullable = false)
    private BigDecimal debit = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal credit = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal montant = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensOperation sens = SensOperation.DEBIT;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_rapprochement", nullable = false, length = 40)
    private StatutOperationBancaire statutRapprochement = StatutOperationBancaire.NON_RAPPROCHEE;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation", nullable = false, length = 40)
    private TypeOperationBancaire typeOperation = TypeOperationBancaire.AUTRE;
    @Column(name = "tiers_reconnu")
    private String tiersReconnu;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_tiers_reconnu", length = 40)
    private TypeTiersReconnu typeTiersReconnu;
    @Enumerated(EnumType.STRING)
    @Column(name = "nature_operation", nullable = false, length = 40)
    private NatureOperationBancaire natureOperation = NatureOperationBancaire.AUTRE;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_classification", nullable = false, length = 40)
    private StatutClassificationOperation statutClassification = StatutClassificationOperation.A_VERIFIER;
    @Column(nullable = false)
    private boolean rapprochee;
    @Column(name = "numero_ligne_import")
    private Integer numeroLigneImport;
    @Column(name = "hash_operation", nullable = false, length = 64)
    private String hashOperation;
    @Column(name = "date_import")
    private LocalDateTime dateImport;
    @Column(nullable = false)
    private boolean actif = true;
    @Column(name = "tiers_id")
    private Long tiersId;
    @Column(name = "facture_id")
    private Long factureId;
    @Column(name = "facture_rapprochement_id")
    private Long factureRapprochementId;
    @Column(name = "piece_rapprochement_id")
    private Long pieceRapprochementId;
    @Column(name = "salaire_id")
    private Long salaireId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_facture", length = 20)
    private TypeFactureRapprochement typeFacture;
    @Column(name = "libelle_rapprochement", length = 180)
    private String libelleRapprochement;
    @Column(name = "date_rapprochement")
    private LocalDateTime dateRapprochement;
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification = LocalDateTime.now();

    @org.hibernate.annotations.Formula("""
            (select coalesce(sum(r.montant_rapproche), 0) from rapprochement_bancaire r
             where r.operation_bancaire_id = id and r.tenant_id = tenant_id
               and r.societe_interne_id = societe_interne_id and r.statut = 'VALIDE')
            """)
    private BigDecimal montantRapproche;
    @org.hibernate.annotations.Formula("""
            (select min(coalesce(r.document_associe_numero, r.libelle_rapprochement))
             from rapprochement_bancaire r where r.operation_bancaire_id = id and r.tenant_id = tenant_id
               and r.societe_interne_id = societe_interne_id and r.statut = 'VALIDE'
               and r.mode_rapprochement is distinct from 'EXERCICE_ANTERIEUR')
            """)
    private String numeroPieceAssociee;

    public BigDecimal getMontantRapproche() { return montantRapproche; }
    public String getNumeroPieceAssociee() { return numeroPieceAssociee; }
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSocieteInterneId() { return societeInterneId; }
    public void setSocieteInterneId(Long societeInterneId) { this.societeInterneId = societeInterneId; }
    public CompteBancaire getCompteBancaire() { return compteBancaire; }
    public void setCompteBancaire(CompteBancaire compteBancaire) { this.compteBancaire = compteBancaire; }
    public ImportReleveBancaire getImportReleveBancaire() { return importReleveBancaire; }
    public void setImportReleveBancaire(ImportReleveBancaire importReleveBancaire) { this.importReleveBancaire = importReleveBancaire; }
    public LocalDate getDateOperation() { return dateOperation; }
    public void setDateOperation(LocalDate dateOperation) { this.dateOperation = dateOperation; }
    public LocalDate getDateValeur() { return dateValeur; }
    public void setDateValeur(LocalDate dateValeur) { this.dateValeur = dateValeur; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getReferenceBancaire() { return referenceBancaire; }
    public void setReferenceBancaire(String referenceBancaire) { this.referenceBancaire = referenceBancaire; }
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public SensOperation getSens() { return sens; }
    public void setSens(SensOperation sens) { this.sens = sens; }
    public StatutOperationBancaire getStatutRapprochement() { return statutRapprochement; }
    public void setStatutRapprochement(StatutOperationBancaire statutRapprochement) { this.statutRapprochement = statutRapprochement; }
    public TypeOperationBancaire getTypeOperation() { return typeOperation; }
    public void setTypeOperation(TypeOperationBancaire typeOperation) { this.typeOperation = typeOperation; }
    public String getTiersReconnu() { return tiersReconnu; }
    public void setTiersReconnu(String tiersReconnu) { this.tiersReconnu = tiersReconnu; }
    public TypeTiersReconnu getTypeTiersReconnu() { return typeTiersReconnu; }
    public void setTypeTiersReconnu(TypeTiersReconnu typeTiersReconnu) { this.typeTiersReconnu = typeTiersReconnu; }
    public NatureOperationBancaire getNatureOperation() { return natureOperation; }
    public void setNatureOperation(NatureOperationBancaire natureOperation) { this.natureOperation = natureOperation; }
    public StatutClassificationOperation getStatutClassification() { return statutClassification; }
    public void setStatutClassification(StatutClassificationOperation statutClassification) { this.statutClassification = statutClassification; }
    public boolean isRapprochee() { return rapprochee; }
    public void setRapprochee(boolean rapprochee) { this.rapprochee = rapprochee; }
    public Integer getNumeroLigneImport() { return numeroLigneImport; }
    public void setNumeroLigneImport(Integer numeroLigneImport) { this.numeroLigneImport = numeroLigneImport; }
    public String getHashOperation() { return hashOperation; }
    public void setHashOperation(String hashOperation) { this.hashOperation = hashOperation; }
    public LocalDateTime getDateImport() { return dateImport; }
    public void setDateImport(LocalDateTime dateImport) { this.dateImport = dateImport; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Long getTiersId() { return tiersId; }
    public void setTiersId(Long tiersId) { this.tiersId = tiersId; }
    public Long getFactureId() { return factureId; }
    public void setFactureId(Long factureId) { this.factureId = factureId; }
    public Long getFactureRapprochementId() { return factureRapprochementId; }
    public void setFactureRapprochementId(Long factureRapprochementId) { this.factureRapprochementId = factureRapprochementId; }
    public Long getPieceRapprochementId() { return pieceRapprochementId; }
    public void setPieceRapprochementId(Long pieceRapprochementId) { this.pieceRapprochementId = pieceRapprochementId; }
    public Long getSalaireId() { return salaireId; }
    public void setSalaireId(Long salaireId) { this.salaireId = salaireId; }
    public TypeFactureRapprochement getTypeFacture() { return typeFacture; }
    public void setTypeFacture(TypeFactureRapprochement typeFacture) { this.typeFacture = typeFacture; }
    public String getLibelleRapprochement() { return libelleRapprochement; }
    public void setLibelleRapprochement(String libelleRapprochement) { this.libelleRapprochement = libelleRapprochement; }
    public LocalDateTime getDateRapprochement() { return dateRapprochement; }
    public void setDateRapprochement(LocalDateTime dateRapprochement) { this.dateRapprochement = dateRapprochement; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
}
