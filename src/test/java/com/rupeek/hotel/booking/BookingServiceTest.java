package com.rupeek.hotel.booking;

import com.rupeek.hotel.payment.MockPaymentMethod;
import com.rupeek.hotel.payment.PaymentMethod;
import com.rupeek.hotel.property.PropertyService;
import com.rupeek.hotel.property.RoomType;
import com.rupeek.hotel.storage.InMemoryBookingRepository;
import com.rupeek.hotel.storage.InMemoryPropertyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.rupeek.hotel.booking.Booking.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDate checkIn = LocalDate.now(clock).plusDays(2);
    private final Stay stay = new Stay(checkIn, checkIn.plusDays(2));
    private final InMemoryBookingRepository repository = new InMemoryBookingRepository();
    private final Availability availability = new Availability(repository);
    private final PropertyService properties = new PropertyService(new InMemoryPropertyRepository(), availability, clock);
    private final BookingService bookings = service(new MockPaymentMethod("CARD"));

    @Test
    void rejectsOverlappingStayAndAllowsCheckoutDayTurnover() {
        UUID property = addProperty(1);
        Booking first = bookings.book(property, "STANDARD", stay, 2);
        assertThat(first.status()).isEqualTo(PENDING_PAYMENT);
        assertThat(first.total()).isEqualByComparingTo("3000.00");

        Stay overlap = new Stay(checkIn.plusDays(1), checkIn.plusDays(3));
        assertThatThrownBy(() -> bookings.book(property, "STANDARD", overlap, 2)).isInstanceOf(Booking.Conflict.class);

        Stay afterCheckout = new Stay(stay.checkOut(), stay.checkOut().plusDays(1));
        assertThat(bookings.book(property, "STANDARD", afterCheckout, 2).status()).isEqualTo(PENDING_PAYMENT);
    }

    @Test
    void checksPeakOccupancyAcrossNights() {
        UUID property = addProperty(2);
        LocalDate middle = checkIn.plusDays(1);
        bookings.book(property, "STANDARD", new Stay(checkIn, middle), 1);
        bookings.book(property, "STANDARD", new Stay(middle, stay.checkOut()), 1);

        assertThat(bookings.book(property, "STANDARD", stay, 1).status()).isEqualTo(PENDING_PAYMENT);
        assertThatThrownBy(() -> bookings.book(property, "STANDARD", stay, 1)).isInstanceOf(Booking.Conflict.class);
    }

    @Test
    void declinedPaymentReleasesInventory() {
        UUID property = addProperty(1);
        Booking booking = bookings.book(property, "STANDARD", stay, 1);

        assertThat(bookings.pay(booking.id(), "CARD", "decline").status()).isEqualTo(PAYMENT_FAILED);
        assertThatThrownBy(() -> bookings.pay(booking.id(), "CARD", "approved")).isInstanceOf(Booking.Conflict.class);
        assertThat(bookings.book(property, "STANDARD", stay, 1).status()).isEqualTo(PENDING_PAYMENT);
    }

    @Test
    void cancellingAnUnpaidBookingReleasesItWithoutRefund() {
        UUID property = addProperty(1);
        Booking booking = bookings.book(property, "STANDARD", stay, 1);
        Booking cancelled = bookings.cancel(booking.id());

        assertThat(cancelled.status()).isEqualTo(CANCELLED);
        assertThat(cancelled.refundAmount()).isZero();
        assertThatThrownBy(() -> bookings.pay(booking.id(), "CARD", "approved")).isInstanceOf(Booking.Conflict.class);
        assertThat(bookings.book(property, "STANDARD", stay, 1).status()).isEqualTo(PENDING_PAYMENT);
    }

    @Test
    void keepsInventoryHeldWhenRefundFailsAndAllowsRetry() {
        PaymentMethod card = mock(PaymentMethod.class);
        when(card.name()).thenReturn("CARD");
        BookingService service = service(card);
        UUID property = addProperty(1);
        Booking booking = service.book(property, "STANDARD", stay, 1);
        when(card.charge(booking.id(), booking.total(), "token")).thenReturn(Optional.of("payment-1"));
        when(card.refund("payment-1", booking.total())).thenReturn(false, true);

        assertThat(service.pay(booking.id(), "card", "token").status()).isEqualTo(CONFIRMED);
        assertThatThrownBy(() -> service.pay(booking.id(), "card", "token")).isInstanceOf(Booking.Conflict.class);
        verify(card).charge(booking.id(), booking.total(), "token");
        assertThatThrownBy(() -> service.cancel(booking.id())).isInstanceOf(BookingService.RefundFailed.class);
        assertThat(service.get(booking.id()).status()).isEqualTo(CONFIRMED);
        assertThatThrownBy(() -> service.book(property, "STANDARD", stay, 1)).isInstanceOf(Booking.Conflict.class);

        Booking cancelled = service.cancel(booking.id());
        assertThat(cancelled.status()).isEqualTo(CANCELLED);
        assertThat(cancelled.refundAmount()).isEqualByComparingTo(booking.total());
        assertThatThrownBy(() -> service.cancel(booking.id())).isInstanceOf(Booking.Conflict.class);
        verify(card, times(2)).refund("payment-1", booking.total());
    }

    @Test
    void simultaneousBookingsCannotBothReserveTheLastRoom() throws Exception {
        UUID property = addProperty(1);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> reserve = () -> {
            ready.countDown();
            start.await();
            try {
                bookings.book(property, "STANDARD", stay, 2);
                return true;
            } catch (Booking.Conflict conflict) {
                return false;
            }
        };

        try {
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private BookingService service(PaymentMethod payment) {
        return new BookingService(repository, properties, availability, clock,
                (booking, today) -> booking.total(), List.of(payment));
    }

    private UUID addProperty(int roomCount) {
        var room = new RoomType("STANDARD", 2, roomCount, new BigDecimal("1500.00"));
        return properties.add("owner-1", "Hotel", "Kolkata", "Dum Dum", 4, Set.of(), List.of(room)).id();
    }
}
