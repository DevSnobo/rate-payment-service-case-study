package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

import net.snobo.domain.Money;

@Entity
@Table(name = "refunds",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refunds_payment_reference",
                columnNames = {"payment_id", "reference"}))
@Check(constraints = "amount > 0")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true, length = 36)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "refunded_on", nullable = false)
    private LocalDate refundedOn;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(length = 255)
    private String reference;

    protected Refund() {
    }

    public static Refund create(BigDecimal amount, LocalDate refundedOn, String reference,
                                Instant recordedAt) {
        Refund refund = new Refund();
        refund.refundId = UUID.randomUUID().toString();
        refund.amount = Money.requirePositive(amount, "amount");
        if (refundedOn == null) {
            throw new IllegalArgumentException("refundedOn is required");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt is required");
        }
        refund.refundedOn = refundedOn;
        refund.recordedAt = recordedAt;
        refund.reference = normalizeReference(reference);
        return refund;
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

    public String getRefundId() {
        return refundId;
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getRefundedOn() {
        return refundedOn;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getReference() {
        return reference;
    }

    void setPayment(Payment payment) {
        this.payment = payment;
    }
}
