package net.snobo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import net.snobo.db.entity.Claim;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.rest.dto.ClaimResponse;
import net.snobo.rest.dto.CreateClaimRequest;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateClaimException;
import net.snobo.service.exception.InvalidClaimRequestException;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private ClaimService claimService;

    @Test
    void createsClaimAndReturnsPaddedNumber() {
        given(claimRepository.existsByClaimNumber(4711L)).willReturn(false);
        given(claimRepository.saveAndFlush(any(Claim.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ClaimResponse response = claimService.createClaim(validRequest());

        assertThat(response.claimNumber()).isEqualTo("0000004711");
        assertThat(response.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(response.totalAmount().scale()).isEqualTo(2);
        assertThat(response.debtorName()).isEqualTo("Ada Lovelace");
        assertThat(response.createdOn()).isEqualTo(LocalDate.of(2026, 1, 15));

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).saveAndFlush(captor.capture());
        Claim saved = captor.getValue();
        assertThat(saved.getClaimNumber()).isEqualTo(4711L);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(saved.getTotalAmount().scale()).isEqualTo(2);
        assertThat(saved.getDebtorName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getCreatedOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void rejectsDuplicateClaimNumber() {
        given(claimRepository.existsByClaimNumber(4711L)).willReturn(true);

        assertThatThrownBy(() -> claimService.createClaim(validRequest()))
                .isInstanceOf(DuplicateClaimException.class)
                .hasMessageContaining("0000004711");
    }

    @Test
    void translatesUniqueConstraintViolationToDuplicateClaim() {
        given(claimRepository.existsByClaimNumber(4711L)).willReturn(false);
        given(claimRepository.saveAndFlush(any(Claim.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> claimService.createClaim(validRequest()))
                .isInstanceOf(DuplicateClaimException.class)
                .hasMessageContaining("0000004711");
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> claimService.createClaim(null))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("request body is required");
    }

    @Test
    void rejectsNullClaimNumber() {
        CreateClaimRequest request = new CreateClaimRequest(null, new BigDecimal("300.00"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("claimNumber is required");
    }

    @Test
    void rejectsZeroClaimNumber() {
        CreateClaimRequest request = new CreateClaimRequest(0L, new BigDecimal("300.00"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("claimNumber must be positive");
    }

    @Test
    void rejectsClaimNumberWithMoreThanTenDigits() {
        CreateClaimRequest request = new CreateClaimRequest(10_000_000_000L, new BigDecimal("300.00"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("claimNumber must have at most 10 digits");
    }

    @Test
    void rejectsNullTotalAmount() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, null,
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("totalAmount is required");
    }

    @Test
    void rejectsZeroTotalAmount() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, new BigDecimal("0.00"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("totalAmount must be positive");
    }

    @Test
    void rejectsNegativeTotalAmount() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, new BigDecimal("-1.00"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("totalAmount must be positive");
    }

    @Test
    void rejectsTotalAmountWithMoreThanTwoDecimalPlaces() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, new BigDecimal("300.001"),
                "Ada Lovelace", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("totalAmount must have at most 2 decimal places");
    }

    @Test
    void rejectsBlankDebtorName() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, new BigDecimal("300.00"),
                "   ", LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("debtorName is required");
    }

    @Test
    void rejectsNullCreatedOn() {
        CreateClaimRequest request = new CreateClaimRequest(4711L, new BigDecimal("300.00"),
                "Ada Lovelace", null);

        assertThatThrownBy(() -> claimService.createClaim(request))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("createdOn is required");
    }

    @Test
    void getClaimReturnsPaddedResponse() {
        Claim claim = Claim.create(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
        given(claimRepository.findByClaimNumber(4711L)).willReturn(Optional.of(claim));

        ClaimResponse response = claimService.getClaim(4711L);

        assertThat(response.claimNumber()).isEqualTo("0000004711");
        assertThat(response.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(response.debtorName()).isEqualTo("Ada Lovelace");
        assertThat(response.createdOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void getClaimRejectsUnknownClaim() {
        given(claimRepository.findByClaimNumber(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaim(999L))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessage("No claim 0000000999");
    }

    @Test
    void getClaimRejectsInvalidClaimNumber() {
        assertThatThrownBy(() -> claimService.getClaim(0L))
                .isInstanceOf(InvalidClaimRequestException.class)
                .hasMessage("claimNumber must be positive");
    }

    private static CreateClaimRequest validRequest() {
        return new CreateClaimRequest(4711L, new BigDecimal("300.00"), "Ada Lovelace",
                LocalDate.of(2026, 1, 15));
    }
}
