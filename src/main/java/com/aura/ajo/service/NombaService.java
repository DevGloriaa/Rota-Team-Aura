package com.aura.ajo.service;

import com.aura.ajo.dto.NombaBankResolveRequest;
import com.aura.ajo.dto.NombaBankResolveResponse;
import com.aura.ajo.dto.NombaBankTransferRequest;
import com.aura.ajo.dto.NombaBankTransferResponse;
import com.aura.ajo.dto.NombaCreateVirtualAccountRequest;
import com.aura.ajo.dto.NombaCreateVirtualAccountResponse;
import com.aura.ajo.dto.NombaWalletTransferRequest;
import com.aura.ajo.dto.NombaWalletTransferResponse;

/**
 * Abstraction over the Nomba API.
 * NombaServiceImpl makes real HTTP calls to the Nomba sandbox using credentials
 * from env vars (NOMBA_ACCOUNT_ID, NOMBA_CLIENT_ID, NOMBA_CLIENT_SECRET).
 */
public interface NombaService {

    /**
     * POST /v1/accounts/virtual
     * Creates a static inbound-only virtual account under the parent Nomba account.
     */
    NombaCreateVirtualAccountResponse createVirtualAccount(NombaCreateVirtualAccountRequest request);

    /**
     * POST /v2/transfers/wallet
     * Nomba-to-Nomba wallet transfer from the parent account to a recipient sub-account.
     * merchantTxRef in the request is Nomba's own idempotency key.
     */
    NombaWalletTransferResponse performWalletTransfer(NombaWalletTransferRequest request);

    /**
     * POST /v1/transfers/banks/resolve
     * Resolves a Nigerian bank account number to a verified account name.
     * Called at member registration to lock in a verified payout destination.
     */
    NombaBankResolveResponse resolveBankAccount(NombaBankResolveRequest request);

    /**
     * POST /v2/transfers/bank
     * Interbank transfer from the parent Nomba account to a recipient's Nigerian bank account.
     * merchantTxRef in the request is Nomba's own idempotency key — prevents double-payout.
     */
    NombaBankTransferResponse performBankTransfer(NombaBankTransferRequest request);
}