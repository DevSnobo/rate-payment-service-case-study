package net.snobo.db.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.snobo.db.entity.Claim;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(attributePaths = {"paymentPlan", "paymentPlan.installments"})
    Optional<Claim> findByClaimNumber(Long claimNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.claimNumber = :claimNumber")
    Optional<Claim> findByClaimNumberForUpdate(@Param("claimNumber") Long claimNumber);

    boolean existsByClaimNumber(Long claimNumber);
}
