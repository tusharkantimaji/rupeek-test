package com.rupeek.hotel.booking;

import com.rupeek.hotel.property.RoomType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class Availability {
    private final BookingRepository bookings;

    public Availability(BookingRepository bookings) {
        this.bookings = bookings;
    }

    public boolean hasRoom(UUID propertyId, RoomType room, Stay stay) {
        Map<LocalDate, Integer> changes = new TreeMap<>();
        for (Booking booking : bookings.findHoldingInventory(propertyId, room.code())) {
            Stay held = booking.stay();
            if (stay.overlaps(held)) {
                LocalDate start = held.checkIn().isBefore(stay.checkIn()) ? stay.checkIn() : held.checkIn();
                changes.merge(start, 1, Integer::sum);
                changes.merge(held.checkOut(), -1, Integer::sum);
            }
        }

        // Group check-ins and check-outs on the same date before checking capacity.
        int occupied = 0;
        for (int change : changes.values()) {
            occupied += change;
            if (occupied >= room.quantity()) {
                return false;
            }
        }
        return true;
    }
}
