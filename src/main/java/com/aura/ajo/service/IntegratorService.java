package com.aura.ajo.service;

import com.aura.ajo.dto.CreateIntegratorRequest;
import com.aura.ajo.dto.IntegratorResponse;

public interface IntegratorService {

    /** Registers a new integrator, generates a raw API key, and returns it once. */
    IntegratorResponse register(CreateIntegratorRequest request);
}
