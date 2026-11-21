package com.rupeek.hotel.property;

import java.math.BigDecimal;

public record RoomType(String code, int capacity, int quantity, BigDecimal nightlyPrice) {
    public RoomType {
        if (capacity < 1 || quantity < 1 || nightlyPrice == null || nightlyPrice.signum() <= 0) {
            throw new IllegalArgumentException("Room capacity, quantity and nightly price must be positive");
        }
    }
}
