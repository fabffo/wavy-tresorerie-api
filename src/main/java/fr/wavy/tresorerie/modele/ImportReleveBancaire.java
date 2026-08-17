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
@Table(name = "import_releve_bancaire")
public class ImportReleveBancaire {
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
    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;
    @Enumerated(EnumType.STRING)
    @Column(name = "type_fichier", nullable = false, length = 40)
    private FormatImportReleve typeFichier = FormatImportReleve.MANUEL;
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_import", nullable = false, length = 40)
    private StatutImportReleve statutImport = StatutImportReleve.IMPORTE;
    @Column(name = "utilisateur_import_id")
    private Long utilisateurImportId;
    @Column(name = "nombre_lignes", nullable = false)
    private Integer nombreLignes = 0;
    @Column(name = "nombre_lignes_importees", nullable = false)
    private Integer nombreLignesImportees = 0;
    @Column(name = "nombre_lignes_rejetees", nullable = false)
    private Integer nombreLignesRejetees = 0;
    @Column(name = "total_debit", nullable = false)
    private BigDecimal totalDebit = BigDecimal.ZERO;
    @Column(name = "total_credit", nullable = false)
    private BigDecimal totalCredit = BigDecimal.ZERO;
    @Column(name = "date_import", nullable = false)
    private LocalDateTime dateImport = LocalDateTime.now();
    @Column(name = "message_erreur")
    private String messageErreur;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSocieteInterneId() { return societeInterneId; }
    public void setSocieteInterneId(Long societeInterneId) { this.societeInterneId = societeInterneId; }
    public CompteBancaire getCompteBancaire() { return compteBancaire; }
    public void setCompteBancaire(CompteBancaire compteBancaire) { this.compteBancaire = compteBancaire; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public FormatImportReleve getTypeFichier() { return typeFichier; }
    public void setTypeFichier(FormatImportReleve typeFichier) { this.typeFichier = typeFichier; }
    public StatutImportReleve getStatutImport() { return statutImport; }
    public void setStatutImport(StatutImportReleve statutImport) { this.statutImport = statutImport; }
    public Long getUtilisateurImportId() { return utilisateurImportId; }
    public void setUtilisateurImportId(Long utilisateurImportId) { this.utilisateurImportId = utilisateurImportId; }
    public Integer getNombreLignes() { return nombreLignes; }
    public void setNombreLignes(Integer nombreLignes) { this.nombreLignes = nombreLignes; }
    public Integer getNombreLignesImportees() { return nombreLignesImportees; }
    public void setNombreLignesImportees(Integer nombreLignesImportees) { this.nombreLignesImportees = nombreLignesImportees; }
    public Integer getNombreLignesRejetees() { return nombreLignesRejetees; }
    public void setNombreLignesRejetees(Integer nombreLignesRejetees) { this.nombreLignesRejetees = nombreLignesRejetees; }
    public BigDecimal getTotalDebit() { return totalDebit; }
    public void setTotalDebit(BigDecimal totalDebit) { this.totalDebit = totalDebit; }
    public BigDecimal getTotalCredit() { return totalCredit; }
    public void setTotalCredit(BigDecimal totalCredit) { this.totalCredit = totalCredit; }
    public LocalDateTime getDateImport() { return dateImport; }
    public void setDateImport(LocalDateTime dateImport) { this.dateImport = dateImport; }
    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String messageErreur) { this.messageErreur = messageErreur; }
}
