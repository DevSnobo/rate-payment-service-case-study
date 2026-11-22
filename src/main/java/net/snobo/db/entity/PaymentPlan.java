package net.snobo.db.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import net.snobo.domain.Status;

@Entity
@Table(name = "payment_plans", uniqueConstraints = @UniqueConstraint(columnNames = "claim_number"))
public class PaymentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long claimNumber;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalClaimAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(precision = 19, scale = 2)
    private BigDecimal amountPaid;
    @Enumerated(EnumType.STRING)
    private Status status;
    @OneToMany(mappedBy = "paymentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<Installment> installments;

    @PrePersist
    @PreUpdate
    public void currencyPrecisionConversion() {
        totalClaimAmount = totalClaimAmount.setScale(2, RoundingMode.HALF_UP);
        outstandingBalance = outstandingBalance.setScale(2, RoundingMode.HALF_UP);
        amountPaid = amountPaid.setScale(2, RoundingMode.HALF_UP);
    }

    public void addInstallment(Installment installment) {
        this.installments.add(installment);
        installment.setPaymentPlan(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClaimNumber() {
        return claimNumber;
    }

    public void setClaimNumber(Long claimNumber) {
        this.claimNumber = claimNumber;
    }

    public BigDecimal getTotalClaimAmount() {
        return totalClaimAmount;
    }

    public void setTotalClaimAmount(BigDecimal totalClaimAmount) {
        this.totalClaimAmount = totalClaimAmount;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Installment> getInstallments() {
        return installments;
    }

    public void setInstallments(List<Installment> installments) {
        this.installments = installments;
    }
}
