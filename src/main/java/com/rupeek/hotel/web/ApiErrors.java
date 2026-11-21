package com.rupeek.hotel.web;

import com.rupeek.hotel.booking.Booking;
import com.rupeek.hotel.booking.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(Booking.Conflict.class)
    ProblemDetail conflict(Booking.Conflict error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(BookingService.RefundFailed.class)
    ProblemDetail refundFailed(BookingService.RefundFailed error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.getMessage());
    }
}
