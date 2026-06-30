package com.aura.ajo.enums;

public enum VirtualAccountType {
    /** Individual member's inbound-only contribution account. */
    MEMBER,
    /**
     * Logical pool account tracked in our ledger.
     * Funds physically reside in the parent Nomba wallet.
     * NOTE: true per-group sub-account isolation (via Nomba-provisioned sub-accounts)
     * is a known production hardening step — it requires dashboard or Nomba-enabled
     * sub-account creation and is not implemented here for hackathon scope.
     */
    GROUP_POOL
}