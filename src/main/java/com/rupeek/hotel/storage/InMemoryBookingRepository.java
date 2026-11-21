package com.rupeek.hotel.storage;

import com.rupeek.hotel.booking.Booking;
import com.rupeek.hotel.booking.BookingRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InMemoryBookingRepository implements BookingRepository {
    private final Map<UUID, Booking> bookings = new HashMap<>();

    @Override
    public synchronized Booking save(Booking booking) {
        bookings.put(booking.id(), booking);
        return booking;
    }

    @Override
    public synchronized Optional<Booking> findById(UUID id) {
        return Optional.ofNullable(bookings.get(id));
    }

    @Override
    public synchronized List<Booking> findHoldingInventory(UUID propertyId, String roomTypeCode) {
        return bookings.values().stream()
                .filter(Booking::holdsInventory)
                .filter(booking -> booking.propertyId().equals(propertyId) && booking.roomTypeCode().equals(roomTypeCode))
                .toList();
    }
}
