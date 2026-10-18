package com.example.backend.service.payment;

import com.example.backend.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The provider beans by code. The mock provider is a bean only when {@code ecogreen.payment.mock-enabled=true}. */
@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGatewayClient> byCode = new HashMap<>();

    @Autowired
    public PaymentGatewayRegistry(List<PaymentGatewayClient> clients) {
        for (PaymentGatewayClient client : clients) {
            byCode.put(client.providerCode(), client);
        }
    }

    public Optional<PaymentGatewayClient> find(String providerCode) {
        return providerCode == null ? Optional.empty()
                : Optional.ofNullable(byCode.get(providerCode.trim().toUpperCase(Locale.ROOT)));
    }

    /** @throws ResourceNotFoundException for an unknown (or disabled) provider */
    public PaymentGatewayClient get(String providerCode) {
        return find(providerCode).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cổng thanh toán."));
    }
}
