package fr.wavy.tresorerie.modele;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "compte_bancaire",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_compte_bancaire_contexte_iban",
                columnNames = {"tenant_id", "societe_interne_id", "iban"}
        )
)
public class CompteBancaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "societe_interne_id", nullable = false)
    private Long societeInterneId;
    @Column(name = "nom_compte", nullable = false, length = 150)
    private String nomCompte;
    @Column(nullable = false, length = 150)
    private String banque;
    @Column(nullable = false, length = 34)
    private String iban;
    @Column(length = 11)
    private String bic;
    @Column(nullable = false, length = 3)
    private String devise = "EUR";
    @Column(name = "solde_initial", nullable = false)
    private BigDecimal soldeInitial = BigDecimal.ZERO;
    @Column(name = "date_solde_initial")
    private LocalDate dateSoldeInitial;
    @Column(name = "solde_actuel", nullable = false)
    private BigDecimal soldeActuel = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean actif = true;
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSocieteInterneId() { return societeInterneId; }
    public void setSocieteInterneId(Long societeInterneId) { this.societeInterneId = societeInterneId; }
    public String getNomCompte() { return nomCompte; }
    public void setNomCompte(String nomCompte) { this.nomCompte = nomCompte; }
    public String getBanque() { return banque; }
    public void setBanque(String banque) { this.banque = banque; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getBic() { return bic; }
    public void setBic(String bic) { this.bic = bic; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public BigDecimal getSoldeInitial() { return soldeInitial; }
    public void setSoldeInitial(BigDecimal soldeInitial) { this.soldeInitial = soldeInitial; }
    public LocalDate getDateSoldeInitial() { return dateSoldeInitial; }
    public void setDateSoldeInitial(LocalDate dateSoldeInitial) { this.dateSoldeInitial = dateSoldeInitial; }
    public BigDecimal getSoldeActuel() { return soldeActuel; }
    public void setSoldeActuel(BigDecimal soldeActuel) { this.soldeActuel = soldeActuel; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
}
