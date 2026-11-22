package net.snobo.db.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import net.snobo.db.entity.Claim;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(attributePaths = {"paymentPlan", "paymentPlan.installments"})
    Optional<Claim> findByClaimNumber(Long claimNumber);

    boolean existsByClaimNumber(Long claimNumber);
}
