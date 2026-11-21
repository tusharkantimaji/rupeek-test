# Hotel Booking

Requires Java 17+ and Maven. Run `mvn test` for the tests or `mvn spring-boot:run` to start the API on port 8080. The service starts with an empty in-memory store.

## Try it

Use future stay dates and copy the IDs from each creation response into `PROPERTY_ID` and `BOOKING_ID`.

```sh
curl -X POST localhost:8080/properties -H 'Content-Type: application/json' \
  -d '{"ownerId":"owner-1","name":"Park Hotel","city":"Kolkata","locality":"Dum Dum","stars":4,"amenities":["wifi"],"roomTypes":[{"code":"STANDARD","capacity":2,"quantity":1,"nightlyPrice":1500}]}'

curl 'http://localhost:8080/properties?city=Kolkata&checkIn=2030-06-10&checkOut=2030-06-12&guests=2&minStars=4&maxPrice=2000&amenities=wifi'

curl -X POST localhost:8080/bookings -H 'Content-Type: application/json' \
  -d "{\"propertyId\":\"$PROPERTY_ID\",\"roomTypeCode\":\"STANDARD\",\"checkIn\":\"2030-06-10\",\"checkOut\":\"2030-06-12\",\"guests\":2}"

curl -X POST "localhost:8080/bookings/$BOOKING_ID/pay" -H 'Content-Type: application/json' \
  -d '{"method":"UPI","token":"demo"}'

curl -X POST "localhost:8080/bookings/$BOOKING_ID/cancel"
```

Search also accepts `locality`, `minPrice` and comma-separated `amenities`. The price bounds apply to the nightly rate. Read a created property or booking with `GET /properties/{id}` or `GET /bookings/{id}`.

## Rules and trade-offs

- Onboard multiple properties with the same `ownerId`. Each booking holds one room of a type, counted by night from check-in through the night before checkout. Prices are in INR and dates use India time.
- Search and booking share the availability check. Search composes room and property filters. Booking rechecks availability inside a single-process lock that serializes booking changes.
- A new booking holds inventory in `PENDING_PAYMENT`. The mock accepts `CARD`, `UPI` and `WALLET`. Use `decline` as the token to see `PAYMENT_FAILED` and release the hold. A successful charge moves it to `CONFIRMED`.
- Pending and confirmed bookings can be cancelled. The default policy refunds a confirmed booking in full before its check-in date. On or after check-in the refund is zero. Cancellation releases the hold after any required refund succeeds.
- Repositories, payment methods and the cancellation policy have separate interfaces. With more time, I'd add expiring holds, transactional storage and idempotency keys for payment and refund retries.
