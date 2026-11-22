package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

import net.snobo.domain.Money;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_plan_reference",
                columnNames = {"payment_plan_id", "reference"}))
@Check(constraints = "amount > 0")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 36)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_plan_id", nullable = false)
    private PaymentPlan paymentPlan;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(length = 255)
    private String reference;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private final List<PaymentAllocation> allocations = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("refundedOn ASC, id ASC")
    private final List<Refund> refunds = new ArrayList<>();

    protected Payment() {
    }

    public static Payment create(BigDecimal amount, LocalDate paidOn, String reference, Instant recordedAt) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID().toString();
        payment.amount = Money.requirePositive(amount, "amount");
        if (paidOn == null) {
            throw new IllegalArgumentException("paidOn is required");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt is required");
        }
        payment.paidOn = paidOn;
        payment.recordedAt = recordedAt;
        payment.reference = normalizeReference(reference);
        return payment;
    }

    public PaymentAllocation addAllocation(Installment installment, BigDecimal allocationAmount) {
        PaymentAllocation allocation = PaymentAllocation.create(this, installment, allocationAmount);
        allocations.add(allocation);
        return allocation;
    }

    public void addRefund(Refund refund) {
        if (refund == null) {
            throw new IllegalArgumentException("refund is required");
        }
        if (refund.getPayment() != null && refund.getPayment() != this) {
            throw new IllegalStateException("refund belongs to another payment");
        }
        if (refund.getAmount().compareTo(unallocatedAmount()) > 0) {
            throw new IllegalArgumentException("refund exceeds available credit");
        }
        refunds.add(refund);
        refund.setPayment(this);
    }

    public BigDecimal allocatedAmount() {
        return allocations.stream()
                .map(PaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public BigDecimal refundedAmount() {
        return refunds.stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public BigDecimal unallocatedAmount() {
        BigDecimal remaining = amount.subtract(allocatedAmount()).subtract(refundedAmount());
        return Money.requireNonNegative(remaining, "unallocatedAmount");
    }

    private static String normalizeReference(String reference) {
        if (reference == null) {
            return null;
        }
        String trimmed = reference.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("reference must be at most 255 characters");
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public PaymentPlan getPaymentPlan() {
        return paymentPlan;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getReference() {
        return reference;
    }

    public List<PaymentAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }

    public List<Refund> getRefunds() {
        return Collections.unmodifiableList(refunds);
    }

    void setPaymentPlan(PaymentPlan paymentPlan) {
        this.paymentPlan = paymentPlan;
    }
}
