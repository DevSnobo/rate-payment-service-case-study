package net.snobo.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.snobo.db.entity.Claim;
import net.snobo.db.repository.ClaimRepository;
import net.snobo.domain.ClaimNumbers;
import net.snobo.rest.dto.ClaimResponse;
import net.snobo.rest.dto.CreateClaimRequest;
import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateClaimException;
import net.snobo.service.exception.InvalidClaimRequestException;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;

    public ClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaim(long claimNumber) {
        long validClaimNumber;
        try {
            validClaimNumber = ClaimNumbers.require(claimNumber);
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }
        Claim claim = claimRepository.findByClaimNumber(validClaimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(validClaimNumber));
        return ClaimResponse.from(claim);
    }

    @Transactional
    public ClaimResponse createClaim(CreateClaimRequest request) {
        if (request == null) {
            throw new InvalidClaimRequestException("request body is required");
        }
        if (request.claimNumber() == null) {
            throw new InvalidClaimRequestException("claimNumber is required");
        }

        Claim claim;
        try {
            claim = Claim.create(request.claimNumber(), request.totalAmount(), request.debtorName(),
                    request.createdOn());
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimRequestException(exception.getMessage(), exception);
        }

        if (claimRepository.existsByClaimNumber(claim.getClaimNumber())) {
            throw new DuplicateClaimException(claim.getClaimNumber());
        }

        Claim saved;
        try {
            saved = claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateClaimException(claim.getClaimNumber());
        }
        return ClaimResponse.from(saved);
    }
}
