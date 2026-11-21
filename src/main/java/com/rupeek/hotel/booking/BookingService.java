package com.rupeek.hotel.booking;

import com.rupeek.hotel.payment.PaymentMethod;
import com.rupeek.hotel.property.PropertyService;
import com.rupeek.hotel.property.RoomType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final PropertyService properties;
    private final Availability availability;
    private final Clock clock;
    private final CancellationPolicy cancellationPolicy;
    private final Map<String, PaymentMethod> paymentMethods;

    public BookingService(BookingRepository bookings, PropertyService properties, Availability availability,
                          Clock clock, CancellationPolicy cancellationPolicy, List<PaymentMethod> paymentMethods) {
        this.bookings = bookings;
        this.properties = properties;
        this.availability = availability;
        this.clock = clock;
        this.cancellationPolicy = cancellationPolicy;
        this.paymentMethods = paymentMethods.stream().collect(Collectors.toUnmodifiableMap(
                method -> normalizeMethod(method.name()), Function.identity()));
    }

    public Booking get(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new NoSuchElementException("Booking not found: " + id));
    }

    public synchronized Booking book(UUID propertyId, String roomTypeCode, Stay stay, int guests) {
        stay.requireStartOnOrAfter(LocalDate.now(clock));
        RoomType room = properties.get(propertyId).roomType(roomTypeCode);
        if (guests < 1 || guests > room.capacity()) {
            throw new IllegalArgumentException("Guest count must be between 1 and " + room.capacity());
        }
        if (!availability.hasRoom(propertyId, room, stay)) {
            throw new Booking.Conflict("Room type is sold out for the requested dates");
        }
        return bookings.save(Booking.pending(propertyId, room, stay, guests));
    }

    public synchronized Booking pay(UUID id, String methodName, String token) {
        Booking booking = get(id);
        booking.requirePendingPayment();
        booking.stay().requireStartOnOrAfter(LocalDate.now(clock));
        PaymentMethod method = paymentMethod(methodName);
        Booking.PaymentReceipt receipt = method.charge(booking.id(), booking.total(), token)
                .map(reference -> new Booking.PaymentReceipt(method.name(), reference)).orElse(null);
        return bookings.save(booking.recordPayment(receipt));
    }

    public synchronized Booking cancel(UUID id) {
        Booking booking = get(id);
        Booking cancelled = booking.cancel(cancellationPolicy, LocalDate.now(clock));
        if (cancelled.refundAmount().signum() > 0) {
            Booking.PaymentReceipt payment = booking.payment();
            if (!paymentMethod(payment.method()).refund(payment.reference(), cancelled.refundAmount())) {
                throw new RefundFailed();
            }
        }
        return bookings.save(cancelled);
    }

    private PaymentMethod paymentMethod(String name) {
        PaymentMethod method = paymentMethods.get(normalizeMethod(name));
        if (method == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + name);
        }
        return method;
    }

    private static String normalizeMethod(String name) {
        return name.strip().toUpperCase(Locale.ROOT);
    }

    public static class RefundFailed extends RuntimeException {
        public RefundFailed() {
            super("Refund failed. The booking remains confirmed");
        }
    }
}
