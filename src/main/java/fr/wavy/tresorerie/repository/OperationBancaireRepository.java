package fr.wavy.tresorerie.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;

public interface OperationBancaireRepository extends JpaRepository<OperationBancaire, Long> {
    List<OperationBancaire> findByTenantIdAndSocieteInterneIdAndActifTrueOrderByDateOperationDescIdDesc(Long tenantId, Long societeInterneId);
    Optional<OperationBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
    Optional<OperationBancaire> findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(Long id, Long tenantId, Long societeInterneId);
    List<OperationBancaire> findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(Long tenantId, Long societeInterneId, StatutOperationBancaire statut);
    List<OperationBancaire> findByTenantIdAndSocieteInterneIdAndImportReleveBancaireIdAndActifTrueOrderByDateOperationDescIdDesc(Long tenantId, Long societeInterneId, Long importReleveBancaireId);
    boolean existsByTenantIdAndSocieteInterneIdAndCompteBancaireIdAndHashOperation(Long tenantId, Long societeInterneId, Long compteBancaireId, String hashOperation);
    long countByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochement(Long tenantId, Long societeInterneId, StatutOperationBancaire statut);

    @Query("""
            select coalesce(sum(o.montant), 0)
            from OperationBancaire o
            where o.tenantId = :tenantId
              and o.societeInterneId = :societeInterneId
              and o.actif = true
            """)
    BigDecimal sommeMontants(Long tenantId, Long societeInterneId);

    @Query("""
            select coalesce(sum(case when o.sens = fr.wavy.tresorerie.modele.SensOperation.CREDIT then o.credit else 0 end), 0)
            from OperationBancaire o
            where o.tenantId = :tenantId
              and o.societeInterneId = :societeInterneId
              and o.actif = true
            """)
    BigDecimal sommeCredits(Long tenantId, Long societeInterneId);

    @Query("""
            select coalesce(sum(case when o.sens = fr.wavy.tresorerie.modele.SensOperation.DEBIT then o.debit else 0 end), 0)
            from OperationBancaire o
            where o.tenantId = :tenantId
              and o.societeInterneId = :societeInterneId
              and o.actif = true
            """)
    BigDecimal sommeDebits(Long tenantId, Long societeInterneId);

    @Query("""
            select o
            from OperationBancaire o
            where o.tenantId = :tenantId
              and o.societeInterneId = :societeInterneId
              and o.actif = true
              and (:compteBancaireId is null or o.compteBancaire.id = :compteBancaireId)
              and (:dateDebut is null or o.dateOperation >= :dateDebut)
              and (:dateFin is null or o.dateOperation <= :dateFin)
              and (:libelle = '' or lower(o.libelle) like lower(concat('%', :libelle, '%')))
              and (:montantMin is null or o.montant >= :montantMin)
              and (:montantMax is null or o.montant <= :montantMax)
              and (:sens is null or o.sens = :sens)
              and (:typeOperation is null or o.typeOperation = :typeOperation)
              and (:tiersReconnu = '' or lower(coalesce(o.tiersReconnu, '')) like lower(concat('%', :tiersReconnu, '%')))
              and (:typeTiersReconnu is null or o.typeTiersReconnu = :typeTiersReconnu)
              and (:natureOperation is null or o.natureOperation = :natureOperation)
              and (:statutClassification is null or o.statutClassification = :statutClassification)
              and (:statutRapprochement is null or o.statutRapprochement = :statutRapprochement)
            """)
    Page<OperationBancaire> rechercher(
            Long tenantId,
            Long societeInterneId,
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
            Pageable pageable
    );
}
