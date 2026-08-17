package fr.wavy.tresorerie.repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import fr.wavy.tresorerie.modele.RapprochementBancaire;
import fr.wavy.tresorerie.modele.StatutRapprochement;

public interface RapprochementBancaireRepository extends JpaRepository<RapprochementBancaire, Long> {
    List<RapprochementBancaire> findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(Long tenantId, Long societeInterneId);
    Optional<RapprochementBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
    long countByTenantIdAndSocieteInterneIdAndStatut(Long tenantId, Long societeInterneId, StatutRapprochement statut);
    List<RapprochementBancaire> findByTenantIdAndSocieteInterneIdAndStatutOrderByDateRapprochementAscIdAsc(Long tenantId, Long societeInterneId, StatutRapprochement statut);
    long countByOperationBancaireIdAndStatut(Long operationBancaireId, StatutRapprochement statut);
    boolean existsByOperationBancaireIdAndFactureIdAndStatutIn(Long operationBancaireId, Long factureId, List<StatutRapprochement> statuts);
    boolean existsByOperationBancaireIdAndSalaireIdAndStatutIn(Long operationBancaireId, Long salaireId, List<StatutRapprochement> statuts);

    @Query("""
            select coalesce(sum(r.montantRapproche), 0)
            from RapprochementBancaire r
            where r.operationBancaire.id = :operationBancaireId
              and r.statut = :statut
            """)
    BigDecimal sommeMontantParOperationEtStatut(Long operationBancaireId, StatutRapprochement statut);

    List<RapprochementBancaire> findByOperationBancaireIdAndStatut(Long operationBancaireId, StatutRapprochement statut);
}
