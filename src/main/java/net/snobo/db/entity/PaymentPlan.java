package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    @OneToMany(mappedBy = "paymentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidOn ASC, id ASC")
    private final List<Payment> payments = new ArrayList<>();

    protected PaymentPlan() {
    }

    public void addInstallment(Installment installment) {
        if (installment == null) {
            throw new IllegalArgumentException("installment is required");
        }
        this.installments.add(installment);
        installment.setPaymentPlan(this);
    }

    public void addPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("payment is required");
        }
        if (payment.getPaymentPlan() != null && payment.getPaymentPlan() != this) {
            throw new IllegalStateException("payment belongs to another plan");
        }
        this.payments.add(payment);
        payment.setPaymentPlan(this);
    }

    public void allocate(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("payment is required");
        }
        if (payment.getPaymentPlan() != this) {
            throw new IllegalStateException("payment is not attached to this plan");
        }
        if (installments.isEmpty()) {
            throw new IllegalStateException("payment plan must have at least one installment");
        }

        BigDecimal remaining = payment.getAmount();
        List<Installment> allocationOrder = installments.stream()
                .sorted(Comparator.comparing(Installment::getDueDate)
                        .thenComparingInt(Installment::getInstallmentNumber))
                .toList();

        for (Installment installment : allocationOrder) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal outstanding = installment.outstandingAmount();
            if (outstanding.signum() <= 0) {
                continue;
            }
            BigDecimal allocationAmount = remaining.min(outstanding);
            installment.applyAllocation(allocationAmount);
            payment.addAllocation(installment, allocationAmount);
            remaining = remaining.subtract(allocationAmount);
        }
    }

    public BigDecimal creditBalance() {
        return payments.stream()
                .map(Payment::unallocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
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

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    void setClaim(Claim claim) {
        this.claim = claim;
    }
}
