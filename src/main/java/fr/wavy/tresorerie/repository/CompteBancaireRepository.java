package fr.wavy.tresorerie.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.wavy.tresorerie.modele.CompteBancaire;

public interface CompteBancaireRepository extends JpaRepository<CompteBancaire, Long> {
    List<CompteBancaire> findByTenantIdAndSocieteInterneIdAndActifTrueOrderByNomCompte(Long tenantId, Long societeInterneId);
    Optional<CompteBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
    Optional<CompteBancaire> findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(Long id, Long tenantId, Long societeInterneId);
}
