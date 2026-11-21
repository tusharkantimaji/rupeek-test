package com.rupeek.hotel.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Stay(LocalDate checkIn, LocalDate checkOut) {
    public Stay {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
            throw new IllegalArgumentException("Check-out must be after check-in");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public boolean overlaps(Stay other) {
        return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut);
    }

    public void requireStartOnOrAfter(LocalDate today) {
        if (checkIn.isBefore(today)) {
            throw new IllegalArgumentException("Check-in cannot be in the past");
        }
    }
}
