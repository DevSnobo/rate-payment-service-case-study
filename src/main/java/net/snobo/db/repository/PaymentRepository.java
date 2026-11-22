package net.snobo.db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.snobo.db.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    List<Payment> findByPaymentPlanIdOrderByPaidOnAscIdAsc(Long paymentPlanId);

    boolean existsByPaymentPlanIdAndReference(Long paymentPlanId, String reference);
}
