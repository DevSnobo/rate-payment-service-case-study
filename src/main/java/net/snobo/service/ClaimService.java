package net.snobo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import net.snobo.db.repository.ClaimRepository;
import net.snobo.rest.dto.Claim;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;

    public ClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    public List<Claim> getClaims(String customerId) {
        return null;
    }
}
