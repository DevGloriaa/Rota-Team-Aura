package com.aura.ajo.serviceImpl;

import com.aura.ajo.config.NombaProperties;
import com.aura.ajo.dto.NombaBankResolveRequest;
import com.aura.ajo.dto.NombaBankResolveResponse;
import com.aura.ajo.dto.NombaBankTransferRequest;
import com.aura.ajo.dto.NombaBankTransferResponse;
import com.aura.ajo.dto.NombaCreateVirtualAccountRequest;
import com.aura.ajo.dto.NombaCreateVirtualAccountResponse;
import com.aura.ajo.dto.NombaTokenRequest;
import com.aura.ajo.dto.NombaTokenResponse;
import com.aura.ajo.dto.NombaUpdateVirtualAccountRequest;
import com.aura.ajo.dto.NombaUpdateVirtualAccountResponse;
import com.aura.ajo.dto.NombaWalletTransferRequest;
import com.aura.ajo.dto.NombaWalletTransferResponse;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.service.NombaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class NombaServiceImpl implements NombaService {

    private final NombaProperties properties;
    private final RestClient restClient;

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final ReentrantLock tokenLock = new ReentrantLock();

    public NombaServiceImpl(NombaProperties properties) {
        this.properties = properties;
        String baseUrl = properties.getApi().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("nomba.api.base-url must be configured");
        }
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public NombaCreateVirtualAccountResponse createVirtualAccount(NombaCreateVirtualAccountRequest request) {
        try {
            NombaCreateVirtualAccountResponse resp = restClient.post()
                    .uri("/v1/accounts/virtual/{subAccountId}", properties.getApi().getSubAccountId())
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .body(request)
                    .retrieve()
                    .body(NombaCreateVirtualAccountResponse.class);
            if (resp == null) {
                throw new AppException("NOMBA_NULL_RESPONSE",
                        "Nomba returned empty body for createVirtualAccount", HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (RestClientException e) {
            throw new AppException("NOMBA_VA_ERROR",
                    "Nomba createVirtualAccount HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public NombaWalletTransferResponse performWalletTransfer(NombaWalletTransferRequest request) {
        try {
            NombaWalletTransferResponse resp = restClient.post()
                    .uri("/v2/transfers/wallet")
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .body(request)
                    .retrieve()
                    .body(NombaWalletTransferResponse.class);
            if (resp == null) {
                throw new AppException("NOMBA_NULL_RESPONSE",
                        "Nomba returned empty body for performWalletTransfer", HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (RestClientException e) {
            throw new AppException("NOMBA_TRANSFER_ERROR",
                    "Nomba performWalletTransfer HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public NombaBankResolveResponse resolveBankAccount(NombaBankResolveRequest request) {
        try {
            NombaBankResolveResponse resp = restClient.post()
                    .uri("/v1/transfers/bank/lookup")
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .body(request)
                    .retrieve()
                    .body(NombaBankResolveResponse.class);
            if (resp == null) {
                throw new AppException("NOMBA_NULL_RESPONSE",
                        "Nomba returned empty body for resolveBankAccount", HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (RestClientException e) {
            throw new AppException("NOMBA_RESOLVE_ERROR",
                    "Nomba resolveBankAccount HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public NombaBankTransferResponse performBankTransfer(NombaBankTransferRequest request) {
        try {
            NombaBankTransferResponse resp = restClient.post()
                    .uri("/v2/transfers/bank/{subAccountId}", properties.getApi().getSubAccountId())
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .body(request)
                    .retrieve()
                    .body(NombaBankTransferResponse.class);
            if (resp == null) {
                throw new AppException("NOMBA_NULL_RESPONSE",
                        "Nomba returned empty body for performBankTransfer", HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (RestClientException e) {
            throw new AppException("NOMBA_BANK_TRANSFER_ERROR",
                    "Nomba performBankTransfer HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public void expireVirtualAccount(String accountRef) {
        try {
            restClient.delete()
                    .uri("/v1/accounts/virtual/{accountRef}", accountRef)
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AppException("NOMBA_EXPIRE_VA_ERROR",
                    "Nomba expireVirtualAccount HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public NombaUpdateVirtualAccountResponse updateVirtualAccount(String accountRef, NombaUpdateVirtualAccountRequest request) {
        try {
            NombaUpdateVirtualAccountResponse resp = restClient.put()
                    .uri("/v1/accounts/virtual/{accountRef}", accountRef)
                    .header("Authorization", "Bearer " + getValidToken())
                    .header("accountId", properties.getApi().getAccountId())
                    .body(request)
                    .retrieve()
                    .body(NombaUpdateVirtualAccountResponse.class);
            if (resp == null) {
                throw new AppException("NOMBA_NULL_RESPONSE",
                        "Nomba returned empty body for updateVirtualAccount", HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (RestClientException e) {
            throw new AppException("NOMBA_UPDATE_VA_ERROR",
                    "Nomba updateVirtualAccount HTTP error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ── Token management ──────────────────────────────────────────────────────

    private String getValidToken() {
        tokenLock.lock();
        try {
            // Proactively refresh 5 minutes before expiry to avoid mid-request failures.
            if (accessToken == null || Instant.now().isAfter(tokenExpiresAt.minusSeconds(300))) {
                obtainToken();
            }
            return accessToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private void obtainToken() {
        log.info("Requesting Nomba access token");
        NombaTokenRequest req = NombaTokenRequest.builder()
                .grantType("client_credentials")
                .clientId(properties.getApi().getClientId())
                .clientSecret(properties.getApi().getClientSecret())
                .build();

        NombaTokenResponse resp;
        try {
            resp = restClient.post()
                    .uri("/v1/auth/token/issue")
                    .header("accountId", properties.getApi().getAccountId())
                    .body(req)
                    .retrieve()
                    .body(NombaTokenResponse.class);
        } catch (RestClientException e) {
            throw new AppException("NOMBA_AUTH_ERROR",
                    "Failed to obtain Nomba access token: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (resp == null || !"00".equals(resp.getCode())) {
            String code = resp != null ? resp.getCode() : "null";
            throw new AppException("NOMBA_AUTH_FAILED",
                    "Nomba auth returned non-success code: " + code,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        this.accessToken = resp.getData().getAccessToken();
        try {
            this.tokenExpiresAt = OffsetDateTime.parse(resp.getData().getExpiresAt()).toInstant();
        } catch (DateTimeParseException e) {
            // Cannot parse expiry — default to 30 minutes rather than caching indefinitely.
            this.tokenExpiresAt = Instant.now().plusSeconds(1800);
            log.warn("Cannot parse Nomba token expiry '{}', defaulting to 30 min",
                    resp.getData().getExpiresAt());
        }
        log.info("Nomba access token valid until {}", this.tokenExpiresAt);
    }
}