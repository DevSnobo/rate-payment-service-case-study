package net.snobo.db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.snobo.db.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundId(String refundId);

    List<Refund> findByPaymentPaymentPlanIdOrderByRefundedOnAscIdAsc(Long paymentPlanId);

    boolean existsByPaymentIdAndReference(Long paymentId, String reference);
}
