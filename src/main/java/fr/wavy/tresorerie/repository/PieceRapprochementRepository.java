package fr.wavy.tresorerie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import fr.wavy.tresorerie.modele.PieceRapprochement;

public interface PieceRapprochementRepository extends JpaRepository<PieceRapprochement, Long> {
    java.util.Optional<PieceRapprochement> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
    java.util.Optional<PieceRapprochement> findByOperationBancaireIdAndTenantIdAndSocieteInterneId(Long operationId, Long tenantId, Long societeInterneId);
    boolean existsByOperationBancaireId(Long operationBancaireId);
    java.util.List<PieceRapprochement> findByTenantIdAndSocieteInterneIdOrderByDatePieceDescIdDesc(Long tenantId, Long societeInterneId);
}
