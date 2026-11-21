package com.rupeek.hotel.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethod {
    String name();

    Optional<String> charge(UUID bookingId, BigDecimal amount, String token);

    boolean refund(String paymentReference, BigDecimal amount);
}
