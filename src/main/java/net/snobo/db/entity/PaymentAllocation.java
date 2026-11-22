package net.snobo.db.entity;

import java.math.BigDecimal;

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
@Table(name = "payment_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_allocations_payment_installment",
                columnNames = {"payment_id", "installment_id"}))
@Check(constraints = "amount > 0")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private Installment installment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected PaymentAllocation() {
    }

    static PaymentAllocation create(Payment payment, Installment installment, BigDecimal amount) {
        if (payment == null) {
            throw new IllegalArgumentException("payment is required");
        }
        if (installment == null) {
            throw new IllegalArgumentException("installment is required");
        }
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.payment = payment;
        allocation.installment = installment;
        allocation.amount = Money.requirePositive(amount, "allocationAmount");
        return allocation;
    }

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public Installment getInstallment() {
        return installment;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
