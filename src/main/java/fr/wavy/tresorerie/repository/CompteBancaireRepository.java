package fr.wavy.tresorerie.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import fr.wavy.tresorerie.modele.CompteBancaire;

public interface CompteBancaireRepository extends JpaRepository<CompteBancaire, Long> {
    List<CompteBancaire> findByTenantIdAndSocieteInterneIdAndActifTrueOrderByNomCompte(Long tenantId, Long societeInterneId);
    Optional<CompteBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
    @Query("select c from CompteBancaire c where c.id = :id and c.tenantId = :tenantId and c.societeInterneId = :societeInterneId and c.actif = true")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompteBancaire> verrouillerPourImport(Long id, Long tenantId, Long societeInterneId);
    Optional<CompteBancaire> findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(Long id, Long tenantId, Long societeInterneId);
}
