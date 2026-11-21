package com.rupeek.hotel;

import com.rupeek.hotel.booking.CancellationPolicy;
import com.rupeek.hotel.payment.MockPaymentMethod;
import com.rupeek.hotel.payment.PaymentMethod;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class HotelBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelBookingApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }

    @Bean
    CancellationPolicy cancellationPolicy() {
        return (booking, today) -> today.isBefore(booking.stay().checkIn())
                ? booking.total() : BigDecimal.ZERO;
    }

    @Bean
    PaymentMethod card() {
        return new MockPaymentMethod("CARD");
    }

    @Bean
    PaymentMethod upi() {
        return new MockPaymentMethod("UPI");
    }

    @Bean
    PaymentMethod wallet() {
        return new MockPaymentMethod("WALLET");
    }
}
