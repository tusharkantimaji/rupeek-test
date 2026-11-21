package com.rupeek.hotel.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record MockPaymentMethod(String name) implements PaymentMethod {
    @Override
    public Optional<String> charge(UUID bookingId, BigDecimal amount, String token) {
        if ("decline".equalsIgnoreCase(token)) {
            return Optional.empty();
        }
        return Optional.of(UUID.randomUUID().toString());
    }

    @Override
    public boolean refund(String paymentReference, BigDecimal amount) {
        return true;
    }
}
