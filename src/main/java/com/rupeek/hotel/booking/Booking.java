package com.rupeek.hotel.booking;

import com.rupeek.hotel.property.RoomType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record Booking(UUID id, UUID propertyId, String roomTypeCode, Stay stay, int guests,
                      BigDecimal total, Status status, PaymentReceipt payment, BigDecimal refundAmount) {
    public enum Status {
        PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED
    }

    public record PaymentReceipt(String method, String reference) {
    }

    static Booking pending(UUID propertyId, RoomType room, Stay stay, int guests) {
        BigDecimal total = room.nightlyPrice().multiply(BigDecimal.valueOf(stay.nights()));
        return new Booking(UUID.randomUUID(), propertyId, room.code(), stay, guests,
                total, Status.PENDING_PAYMENT, null, BigDecimal.ZERO);
    }

    public boolean holdsInventory() {
        return status == Status.PENDING_PAYMENT || status == Status.CONFIRMED;
    }

    void requirePendingPayment() {
        if (status != Status.PENDING_PAYMENT) {
            throw new Conflict("Only a pending booking can be paid");
        }
    }

    Booking recordPayment(PaymentReceipt receipt) {
        requirePendingPayment();
        Status nextStatus = receipt == null ? Status.PAYMENT_FAILED : Status.CONFIRMED;
        return new Booking(id, propertyId, roomTypeCode, stay, guests, total, nextStatus, receipt, refundAmount);
    }

    Booking cancel(CancellationPolicy policy, LocalDate today) {
        if (!holdsInventory()) {
            throw new Conflict("Only a pending or confirmed booking can be cancelled");
        }
        BigDecimal refund = status == Status.CONFIRMED ? policy.refundFor(this, today) : BigDecimal.ZERO;
        if (refund.signum() < 0 || refund.compareTo(total) > 0) {
            throw new IllegalStateException("Cancellation policy returned an invalid refund");
        }
        return new Booking(id, propertyId, roomTypeCode, stay, guests, total, Status.CANCELLED, payment, refund);
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }
}
