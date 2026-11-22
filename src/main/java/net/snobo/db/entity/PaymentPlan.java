package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import net.snobo.domain.Status;

@Entity
@Table(name = "payment_plans")
public class PaymentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "paymentPlan", fetch = FetchType.LAZY)
    private Claim claim;

    @OneToMany(mappedBy = "paymentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private final List<Installment> installments = new ArrayList<>();

    protected PaymentPlan() {
    }

    public void addInstallment(Installment installment) {
        if (installment == null) {
            throw new IllegalArgumentException("installment is required");
        }
        this.installments.add(installment);
        installment.setPaymentPlan(this);
    }

    public void validateAgainstClaim() {
        if (claim == null) {
            throw new IllegalStateException("payment plan is not attached to a claim");
        }
        if (installments.isEmpty()) {
            throw new IllegalStateException("payment plan must have at least one installment");
        }
        BigDecimal scheduledTotal = installments.stream()
                .map(Installment::getScheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scheduledTotal.compareTo(claim.getTotalAmount()) != 0) {
            throw new IllegalStateException("installments total " + scheduledTotal
                    + " does not match claim total " + claim.getTotalAmount());
        }
        Set<Integer> numbers = new HashSet<>();
        for (Installment installment : installments) {
            if (!numbers.add(installment.getInstallmentNumber())) {
                throw new IllegalStateException("duplicate installment number "
                        + installment.getInstallmentNumber());
            }
        }
    }

    public BigDecimal amountPaid() {
        return installments.stream()
                .map(Installment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public BigDecimal outstandingBalance() {
        return installments.stream()
                .map(Installment::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public Status statusAt(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        if (claim == null) {
            throw new IllegalStateException("payment plan is not attached to a claim");
        }
        if (installments.isEmpty()) {
            throw new IllegalStateException("payment plan must have at least one installment");
        }
        if (outstandingBalance().signum() == 0) {
            return Status.COMPLETED;
        }
        LocalDate today = LocalDate.now(clock);
        boolean overdue = installments.stream()
                .filter(installment -> installment.outstandingAmount().signum() > 0)
                .anyMatch(installment -> installment.getDueDate().isBefore(today));
        return overdue ? Status.OVERDUE : Status.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public List<Installment> getInstallments() {
        return Collections.unmodifiableList(installments);
    }

    void setClaim(Claim claim) {
        this.claim = claim;
    }
}
