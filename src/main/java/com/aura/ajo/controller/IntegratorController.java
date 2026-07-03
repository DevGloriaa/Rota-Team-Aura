package com.aura.ajo.controller;

import com.aura.ajo.dto.ApiResponse;
import com.aura.ajo.dto.CreateIntegratorRequest;
import com.aura.ajo.dto.IntegratorResponse;
import com.aura.ajo.service.IntegratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrators")
@RequiredArgsConstructor
public class IntegratorController {

    private final IntegratorService integratorService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<IntegratorResponse>> register(
            @Valid @RequestBody CreateIntegratorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Integrator registered. Save your API key — it will not be shown again.",
                        integratorService.register(request)));
    }
}
