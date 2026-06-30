package com.aura.ajo.service;

import java.util.UUID;

public record PeriodEndPayoutEvent(UUID groupId, int cycleNumber) {}