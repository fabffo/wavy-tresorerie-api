package fr.wavy.tresorerie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import fr.wavy.tresorerie.modele.PieceRapprochement;

public interface PieceRapprochementRepository extends JpaRepository<PieceRapprochement, Long> {
    boolean existsByOperationBancaireId(Long operationBancaireId);
    java.util.List<PieceRapprochement> findByTenantIdAndSocieteInterneIdOrderByDatePieceDescIdDesc(Long tenantId, Long societeInterneId);
}
