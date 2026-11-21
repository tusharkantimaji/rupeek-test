package com.rupeek.hotel.web;

import com.rupeek.hotel.booking.Booking;
import com.rupeek.hotel.booking.BookingService;
import com.rupeek.hotel.booking.Stay;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    public ResponseEntity<Booking> book(@Valid @RequestBody BookInput input) {
        Booking booking = bookings.book(input.propertyId(), input.roomTypeCode(),
                new Stay(input.checkIn(), input.checkOut()), input.guests());
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/{id}")
    public Booking get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @PostMapping("/{id}/pay")
    public Booking pay(@PathVariable UUID id, @Valid @RequestBody PayInput input) {
        return bookings.pay(id, input.method(), input.token());
    }

    @PostMapping("/{id}/cancel")
    public Booking cancel(@PathVariable UUID id) {
        return bookings.cancel(id);
    }

    public record BookInput(@NotNull UUID propertyId, @NotBlank String roomTypeCode,
                            @NotNull LocalDate checkIn, @NotNull LocalDate checkOut, @Positive int guests) {
    }

    public record PayInput(@NotBlank String method, @NotBlank String token) {
    }
}
