package com.aura.ajo.service;

import com.aura.ajo.dto.QuarantinedPaymentResponse;
import com.aura.ajo.dto.ResolveQuarantineRequest;

import java.util.List;
import java.util.UUID;

public interface QuarantineService {

    List<QuarantinedPaymentResponse> getQuarantinedPayments(UUID groupId);

    QuarantinedPaymentResponse resolve(UUID quarantineId, ResolveQuarantineRequest request);
}
