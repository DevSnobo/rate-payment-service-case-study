package net.snobo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import net.snobo.db.entity.PaymentPlan;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {
}
