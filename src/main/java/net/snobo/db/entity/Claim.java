package net.snobo.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

import net.snobo.domain.ClaimNumbers;
import net.snobo.domain.Money;

@Entity
@Table(name = "claims")
@Check(constraints = "claim_number > 0 and claim_number <= 9999999999 and total_amount > 0")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true)
    private Long claimNumber;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "debtor_name", nullable = false)
    private String debtorName;

    @Column(name = "created_on", nullable = false)
    private LocalDate createdOn;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_plan_id", unique = true)
    private PaymentPlan paymentPlan;

    protected Claim() {
    }

    public static Claim create(long claimNumber, BigDecimal totalAmount, String debtorName, LocalDate createdOn) {
        Claim claim = new Claim();
        claim.claimNumber = ClaimNumbers.require(claimNumber);
        claim.totalAmount = Money.requirePositive(totalAmount, "totalAmount");
        claim.debtorName = requireDebtorName(debtorName);
        if (createdOn == null) {
            throw new IllegalArgumentException("createdOn is required");
        }
        claim.createdOn = createdOn;
        return claim;
    }

    public PaymentPlan createPaymentPlan() {
        if (paymentPlan != null) {
            throw new IllegalStateException("claim already has a payment plan");
        }
        PaymentPlan plan = new PaymentPlan();
        plan.setClaim(this);
        this.paymentPlan = plan;
        return plan;
    }

    private static String requireDebtorName(String debtorName) {
        if (debtorName == null || debtorName.isBlank()) {
            throw new IllegalArgumentException("debtorName is required");
        }
        return debtorName;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimNumber() {
        return claimNumber;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDate getCreatedOn() {
        return createdOn;
    }

    public String getDebtorName() {
        return debtorName;
    }

    public PaymentPlan getPaymentPlan() {
        return paymentPlan;
    }

    void setPaymentPlan(PaymentPlan paymentPlan) {
        this.paymentPlan = paymentPlan;
    }
}
