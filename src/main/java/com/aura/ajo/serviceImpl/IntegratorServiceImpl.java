package com.aura.ajo.serviceImpl;

import com.aura.ajo.dto.CreateIntegratorRequest;
import com.aura.ajo.dto.IntegratorResponse;
import com.aura.ajo.entity.Integrator;
import com.aura.ajo.repository.IntegratorRepository;
import com.aura.ajo.service.IntegratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegratorServiceImpl implements IntegratorService {

    private final IntegratorRepository integratorRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Override
    @Transactional
    public IntegratorResponse register(CreateIntegratorRequest request) {
        String rawKey = generateRawKey();
        String keyHash = sha256Hex(rawKey);

        Integrator integrator = new Integrator();
        integrator.setName(request.getName());
        integrator.setApiKeyHash(keyHash);
        integrator = integratorRepository.save(integrator);

        log.info("Registered integrator '{}' (id={})", integrator.getName(), integrator.getId());

        return IntegratorResponse.builder()
                .id(integrator.getId())
                .name(integrator.getName())
                .apiKey(rawKey)
                .note("Store this key securely — it will not be shown again.")
                .createdAt(integrator.getCreatedAt())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Generates "rota_test_" + 32 random alphanumeric characters. */
    private static String generateRawKey() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return "rota_test_" + sb;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
