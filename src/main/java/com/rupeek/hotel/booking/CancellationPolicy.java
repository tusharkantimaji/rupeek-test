package com.rupeek.hotel.booking;

import java.math.BigDecimal;
import java.time.LocalDate;

@FunctionalInterface
public interface CancellationPolicy {
    BigDecimal refundFor(Booking booking, LocalDate cancellationDate);
}
