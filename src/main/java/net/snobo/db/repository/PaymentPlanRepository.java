package net.snobo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import net.snobo.db.entity.PaymentPlan;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {
}
