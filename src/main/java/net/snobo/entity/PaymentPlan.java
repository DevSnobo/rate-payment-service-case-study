package net.snobo.entity;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import net.snobo.rest.dto.Status;

@Entity
@Table(name = "payment_plans")
public class PaymentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long claimNumber;
    private Double totalClaimAmount;
    private Double outstandingBalance;
    private Double amountPaid;
    private Status status;
    @OneToMany(mappedBy = "paymentPlan")
    private List<Installment> installments;

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

    public Double getTotalClaimAmount() {
        return totalClaimAmount;
    }

    public void setTotalClaimAmount(Double totalClaimAmount) {
        this.totalClaimAmount = totalClaimAmount;
    }

    public Double getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(Double outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public Double getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(Double amountPaid) {
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
