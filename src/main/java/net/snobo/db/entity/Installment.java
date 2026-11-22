package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "installments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_installments_plan_number",
                columnNames = {"payment_plan_id", "installment_number"}))
@Check(constraints = "installment_number > 0 and scheduled_amount > 0"
        + " and amount_paid >= 0 and amount_paid <= scheduled_amount")
public class Installment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "scheduled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal scheduledAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_plan_id", nullable = false)
    private PaymentPlan paymentPlan;

    protected Installment() {
    }

    public static Installment create(int number, BigDecimal scheduledAmount, LocalDate dueDate) {
        if (number <= 0) {
            throw new IllegalArgumentException("installmentNumber must be positive");
        }
        Installment installment = new Installment();
        installment.installmentNumber = number;
        installment.scheduledAmount = Money.requirePositive(scheduledAmount, "scheduledAmount");
        installment.amountPaid = BigDecimal.ZERO.setScale(2);
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        installment.dueDate = dueDate;
        return installment;
    }

    public BigDecimal outstandingAmount() {
        return scheduledAmount.subtract(amountPaid);
    }

    public Long getId() {
        return id;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getScheduledAmount() {
        return scheduledAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public PaymentPlan getPaymentPlan() {
        return paymentPlan;
    }

    void setPaymentPlan(PaymentPlan paymentPlan) {
        this.paymentPlan = paymentPlan;
    }

    void setAmountPaid(BigDecimal amountPaid) {
        BigDecimal value = Money.requireNonNegative(amountPaid, "amountPaid");
        if (scheduledAmount != null && value.compareTo(scheduledAmount) > 0) {
            throw new IllegalArgumentException("amountPaid must not exceed scheduledAmount");
        }
        this.amountPaid = value;
    }

    void setScheduledAmount(BigDecimal scheduledAmount) {
        BigDecimal value = Money.requirePositive(scheduledAmount, "scheduledAmount");
        if (amountPaid != null && amountPaid.compareTo(value) > 0) {
            throw new IllegalArgumentException("amountPaid must not exceed scheduledAmount");
        }
        this.scheduledAmount = value;
    }

    void setInstallmentNumber(int installmentNumber) {
        if (installmentNumber <= 0) {
            throw new IllegalArgumentException("installmentNumber must be positive");
        }
        this.installmentNumber = installmentNumber;
    }

    void setDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        this.dueDate = dueDate;
    }
}
