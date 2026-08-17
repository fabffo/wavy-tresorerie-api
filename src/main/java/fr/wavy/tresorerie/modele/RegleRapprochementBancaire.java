package fr.wavy.tresorerie.modele;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regle_rapprochement_bancaire")
public class RegleRapprochementBancaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "societe_interne_id", nullable = false)
    private Long societeInterneId;
    @Column(nullable = false, length = 150)
    private String libelle;
    @Column(length = 255)
    private String motif;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SensOperation sens;
    @Column(name = "tiers_id")
    private Long tiersId;
    @Column(nullable = false)
    private Integer priorite = 100;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSocieteInterneId() { return societeInterneId; }
    public void setSocieteInterneId(Long societeInterneId) { this.societeInterneId = societeInterneId; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public SensOperation getSens() { return sens; }
    public void setSens(SensOperation sens) { this.sens = sens; }
    public Long getTiersId() { return tiersId; }
    public void setTiersId(Long tiersId) { this.tiersId = tiersId; }
    public Integer getPriorite() { return priorite; }
    public void setPriorite(Integer priorite) { this.priorite = priorite; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
}
