package fr.wavy.tresorerie.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.wavy.tresorerie.modele.RegleRapprochementBancaire;

public interface RegleRapprochementBancaireRepository extends JpaRepository<RegleRapprochementBancaire, Long> {
    List<RegleRapprochementBancaire> findByTenantIdAndSocieteInterneIdOrderByPrioriteAscLibelleAsc(Long tenantId, Long societeInterneId);
    List<RegleRapprochementBancaire> findByTenantIdAndSocieteInterneIdAndActiveTrueOrderByPrioriteDescLibelleAsc(Long tenantId, Long societeInterneId);
    Optional<RegleRapprochementBancaire> findByIdAndTenantIdAndSocieteInterneId(Long id, Long tenantId, Long societeInterneId);
}
