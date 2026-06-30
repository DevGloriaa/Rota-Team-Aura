package com.aura.ajo.service;

import java.util.UUID;

/**
 * Published (within the contribution transaction) when a member's payment_success
 * webhook is processed. The {@code @TransactionalEventListener(AFTER_COMMIT)} in
 * PayoutServiceImpl fires after the contribution TX commits and checks whether the
 * cycle is fully funded — triggering the payout in a fresh transaction if so.
 */
public record CycleFundedCheckEvent(UUID groupId, int cycleNumber) {}