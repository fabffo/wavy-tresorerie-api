package fr.wavy.tresorerie.modele;

import java.math.BigDecimal;
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

@Entity
@Table(name = "rapprochement_bancaire")
public class RapprochementBancaire {
    @Column(name = "paiement_engage", nullable = false)
    private boolean paiementEngage;
    public boolean isPaiementEngage() { return paiementEngage; }
    public void setPaiementEngage(boolean valeur) { paiementEngage = valeur; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "societe_interne_id", nullable = false)
    private Long societeInterneId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "operation_bancaire_id", nullable = false)
    private OperationBancaire operationBancaire;
    @Column(name = "facture_id")
    private Long factureId;
    @Column(name = "piece_rapprochement_id")
    private Long pieceRapprochementId;
    @Column(name = "salaire_id")
    private Long salaireId;
    @Column(name = "periode_salaire", length = 7)
    private String periodeSalaire;
    @Column(name = "matricule_salarie", length = 80)
    private String matriculeSalarie;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_piece", length = 40)
    private TypePieceRapprochement typePiece;
    @Column(name = "document_associe_numero", length = 80)
    private String documentAssocieNumero;
    @Column(name = "document_associe_libelle", length = 500)
    private String documentAssocieLibelle;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_facture", length = 20)
    private TypeFactureRapprochement typeFacture;
    @Column(name = "montant_rapproche", nullable = false)
    private BigDecimal montantRapproche = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal ecart = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatutRapprochement statut = StatutRapprochement.PROPOSE;
    private String commentaire;
    @Column(name = "mode_rapprochement", length = 60)
    private String modeRapprochement;
    @Column(name = "libelle_rapprochement", length = 180)
    private String libelleRapprochement;
    @Column(name = "score_matching")
    private Integer scoreMatching;
    @Column(name = "date_rapprochement", nullable = false)
    private LocalDateTime dateRapprochement = LocalDateTime.now();
    @Column(name = "utilisateur_id")
    private Long utilisateurId;
    @Column(name = "date_proposition")
    private LocalDateTime dateProposition;
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;
    @Column(name = "utilisateur_validation_id")
    private Long utilisateurValidationId;
    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;
    @Column(name = "utilisateur_annulation_id")
    private Long utilisateurAnnulationId;
    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSocieteInterneId() { return societeInterneId; }
    public void setSocieteInterneId(Long societeInterneId) { this.societeInterneId = societeInterneId; }
    public OperationBancaire getOperationBancaire() { return operationBancaire; }
    public void setOperationBancaire(OperationBancaire operationBancaire) { this.operationBancaire = operationBancaire; }
    public Long getFactureId() { return factureId; }
    public void setFactureId(Long factureId) { this.factureId = factureId; }
    public Long getPieceRapprochementId() { return pieceRapprochementId; }
    public void setPieceRapprochementId(Long pieceRapprochementId) { this.pieceRapprochementId = pieceRapprochementId; }
    public Long getSalaireId() { return salaireId; }
    public void setSalaireId(Long salaireId) { this.salaireId = salaireId; }
    public String getPeriodeSalaire() { return periodeSalaire; }
    public void setPeriodeSalaire(String periodeSalaire) { this.periodeSalaire = periodeSalaire; }
    public String getMatriculeSalarie() { return matriculeSalarie; }
    public void setMatriculeSalarie(String matriculeSalarie) { this.matriculeSalarie = matriculeSalarie; }
    public TypePieceRapprochement getTypePiece() { return typePiece; } public void setTypePiece(TypePieceRapprochement v) { typePiece=v; }
    public String getDocumentAssocieNumero() { return documentAssocieNumero; } public void setDocumentAssocieNumero(String v) { documentAssocieNumero=v; }
    public String getDocumentAssocieLibelle() { return documentAssocieLibelle; } public void setDocumentAssocieLibelle(String v) { documentAssocieLibelle=v; }
    public TypeFactureRapprochement getTypeFacture() { return typeFacture; }
    public void setTypeFacture(TypeFactureRapprochement typeFacture) { this.typeFacture = typeFacture; }
    public BigDecimal getMontantRapproche() { return montantRapproche; }
    public void setMontantRapproche(BigDecimal montantRapproche) { this.montantRapproche = montantRapproche; }
    public BigDecimal getEcart() { return ecart; }
    public void setEcart(BigDecimal ecart) { this.ecart = ecart; }
    public StatutRapprochement getStatut() { return statut; }
    public void setStatut(StatutRapprochement statut) { this.statut = statut; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public String getModeRapprochement() { return modeRapprochement; }
    public void setModeRapprochement(String modeRapprochement) { this.modeRapprochement = modeRapprochement; }
    public String getLibelleRapprochement() { return libelleRapprochement; }
    public void setLibelleRapprochement(String libelleRapprochement) { this.libelleRapprochement = libelleRapprochement; }
    public Integer getScoreMatching() { return scoreMatching; }
    public void setScoreMatching(Integer scoreMatching) { this.scoreMatching = scoreMatching; }
    public LocalDateTime getDateRapprochement() { return dateRapprochement; }
    public void setDateRapprochement(LocalDateTime dateRapprochement) { this.dateRapprochement = dateRapprochement; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public LocalDateTime getDateProposition() { return dateProposition; }
    public void setDateProposition(LocalDateTime dateProposition) { this.dateProposition = dateProposition; }
    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }
    public Long getUtilisateurValidationId() { return utilisateurValidationId; }
    public void setUtilisateurValidationId(Long utilisateurValidationId) { this.utilisateurValidationId = utilisateurValidationId; }
    public LocalDateTime getDateAnnulation() { return dateAnnulation; }
    public void setDateAnnulation(LocalDateTime dateAnnulation) { this.dateAnnulation = dateAnnulation; }
    public Long getUtilisateurAnnulationId() { return utilisateurAnnulationId; }
    public void setUtilisateurAnnulationId(Long utilisateurAnnulationId) { this.utilisateurAnnulationId = utilisateurAnnulationId; }
    public String getMotifAnnulation() { return motifAnnulation; }
    public void setMotifAnnulation(String motifAnnulation) { this.motifAnnulation = motifAnnulation; }
}
