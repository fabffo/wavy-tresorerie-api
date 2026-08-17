package fr.wavy.tresorerie.modele;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "piece_rapprochement")
public class PieceRapprochement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id", nullable=false) private Long tenantId;
    @Column(name="societe_interne_id", nullable=false) private Long societeInterneId;
    @Column(name="operation_bancaire_id", nullable=false, unique=true) private Long operationBancaireId;
    @Column(name="tiers_id", nullable=false) private Long tiersId;
    @Enumerated(EnumType.STRING) @Column(name="type_piece", nullable=false, length=40) private TypePieceRapprochement typePiece;
    @Column(name="numero_piece", nullable=false, length=40) private String numeroPiece;
    @Column(name="date_piece", nullable=false) private LocalDate datePiece;
    @Column(nullable=false, length=500) private String libelle;
    @Column(name="montant_ht", nullable=false) private BigDecimal montantHt = BigDecimal.ZERO;
    @Column(name="montant_tva", nullable=false) private BigDecimal montantTva = BigDecimal.ZERO;
    @Column(name="montant_ttc", nullable=false) private BigDecimal montantTtc = BigDecimal.ZERO;
    @Column(name="famille_activite", length=120) private String familleActivite;
    @Column(name="categorie_budgetaire", length=120) private String categorieBudgetaire;
    @Column(nullable=false, length=30) private String statut = "VALIDEE";
    @Column(nullable=false, length=30) private String origine = "AUTOMATIQUE";
    @Column(name="libelle_rapprochement", nullable=false, length=180) private String libelleRapprochement;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public Long getSocieteInterneId() { return societeInterneId; }
    public Long getOperationBancaireId() { return operationBancaireId; } public Long getTiersId() { return tiersId; }
    public TypePieceRapprochement getTypePiece() { return typePiece; } public String getNumeroPiece() { return numeroPiece; }
    public LocalDate getDatePiece() { return datePiece; } public String getLibelle() { return libelle; }
    public BigDecimal getMontantTtc() { return montantTtc; } public String getStatut() { return statut; }
    public String getLibelleRapprochement() { return libelleRapprochement; }
    public void setTenantId(Long v) { tenantId=v; } public void setSocieteInterneId(Long v) { societeInterneId=v; }
    public void setOperationBancaireId(Long v) { operationBancaireId=v; } public void setTiersId(Long v) { tiersId=v; }
    public void setTypePiece(TypePieceRapprochement v) { typePiece=v; } public void setNumeroPiece(String v) { numeroPiece=v; }
    public void setDatePiece(LocalDate v) { datePiece=v; } public void setLibelle(String v) { libelle=v; }
    public void setMontantHt(BigDecimal v) { montantHt=v; } public void setMontantTva(BigDecimal v) { montantTva=v; }
    public void setMontantTtc(BigDecimal v) { montantTtc=v; } public void setLibelleRapprochement(String v) { libelleRapprochement=v; }
}
