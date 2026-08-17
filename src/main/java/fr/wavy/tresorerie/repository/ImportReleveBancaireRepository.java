package fr.wavy.tresorerie.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.wavy.tresorerie.modele.ImportReleveBancaire;

public interface ImportReleveBancaireRepository extends JpaRepository<ImportReleveBancaire, Long> {
    List<ImportReleveBancaire> findByTenantIdAndSocieteInterneIdOrderByDateImportDesc(Long tenantId, Long societeInterneId);
    Optional<ImportReleveBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
}
