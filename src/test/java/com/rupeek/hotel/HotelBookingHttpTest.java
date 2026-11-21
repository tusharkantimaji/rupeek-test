package com.rupeek.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HotelBookingHttpTest {
    private static final String PROPERTY = """
            {"ownerId":"chain-1","name":"First Hotel","city":"Kolkata","locality":"Dum Dum",
             "stars":4,"amenities":["WiFi","AC"],"roomTypes":[
               {"code":"STANDARD","capacity":2,"quantity":1,"nightlyPrice":1500},
               {"code":"SUITE","capacity":4,"quantity":1,"nightlyPrice":3000}
             ]}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Clock clock;

    @Test
    void onboardsSearchesBooksPaysAndCancels() throws Exception {
        String propertyPath = addProperty(PROPERTY);
        String propertyId = propertyPath.substring(propertyPath.lastIndexOf('/') + 1);
        mvc.perform(get(propertyPath)).andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value("chain-1"));
        addProperty(PROPERTY.replace("First Hotel", "Second Hotel").replace("Dum Dum", "Salt Lake"));

        LocalDate checkIn = LocalDate.now(clock).plusDays(2);
        LocalDate checkOut = checkIn.plusDays(2);
        mvc.perform(search(checkIn, checkOut)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(propertyId))
                .andExpect(jsonPath("$[0].roomTypes.length()").value(1))
                .andExpect(jsonPath("$[0].roomTypes[0].code").value("STANDARD"));

        String bookingBody = """
                {"propertyId":"%s","roomTypeCode":"STANDARD","checkIn":"%s","checkOut":"%s","guests":2}
                """.formatted(propertyId, checkIn, checkOut);
        String bookingPath = mvc.perform(post("/bookings").contentType(APPLICATION_JSON).content(bookingBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(post("/bookings").contentType(APPLICATION_JSON).content(bookingBody))
                .andExpect(status().isConflict());
        mvc.perform(search(checkIn, checkOut)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post(bookingPath + "/pay").contentType(APPLICATION_JSON)
                        .content("""
                                {"method":"UPI","token":"demo"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.reference").isString());
        mvc.perform(post(bookingPath + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(3000));
        mvc.perform(get(bookingPath)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(search(checkIn, checkOut)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        mvc.perform(get("/properties")).andExpect(status().isBadRequest());
        mvc.perform(search(tomorrow, tomorrow)).andExpect(status().isBadRequest());
        mvc.perform(search(tomorrow, tomorrow.plusDays(1)).param("minPrice", "2500"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/properties").contentType(APPLICATION_JSON).content(PROPERTY.replace("SUITE", "STANDARD")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/properties").contentType(APPLICATION_JSON).content(PROPERTY.replace("\"quantity\":1", "\"quantity\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/properties").contentType(APPLICATION_JSON).content(PROPERTY.replace("\"quantity\":1", "\"quantity\":1.5")))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder search(LocalDate checkIn, LocalDate checkOut) {
        return get("/properties").param("city", "kolkata").param("locality", "Dum Dum")
                .param("checkIn", checkIn.toString()).param("checkOut", checkOut.toString())
                .param("guests", "2").param("minStars", "4").param("maxPrice", "2000").param("amenities", "WIFI,AC");
    }

    private String addProperty(String body) throws Exception {
        return mvc.perform(post("/properties").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }
}
