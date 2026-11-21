package com.rupeek.hotel.web;

import com.rupeek.hotel.booking.Stay;
import com.rupeek.hotel.property.Property;
import com.rupeek.hotel.property.PropertyService;
import com.rupeek.hotel.property.RoomType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/properties")
public class PropertyController {
    private final PropertyService properties;

    public PropertyController(PropertyService properties) {
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<Property> add(@Valid @RequestBody AddProperty input) {
        List<RoomType> rooms = input.roomTypes().stream().map(RoomInput::toRoomType).toList();
        Property property = properties.add(input.ownerId(), input.name(), input.city(), input.locality(),
                input.stars(), input.amenities(), rooms);
        return ResponseEntity.created(URI.create("/properties/" + property.id())).body(property);
    }

    @GetMapping("/{id}")
    public Property get(@PathVariable UUID id) {
        return properties.get(id);
    }

    @GetMapping
    public List<PropertyService.SearchResult> search(@Valid @ModelAttribute SearchInput input) {
        return properties.search(input.city(), input.locality(), new Stay(input.checkIn(), input.checkOut()),
                input.guests(), input.filters());
    }

    public record AddProperty(@NotBlank String ownerId, @NotBlank String name, @NotBlank String city,
                              @NotBlank String locality, @Min(1) @Max(5) int stars,
                              @NotNull Set<@NotBlank String> amenities,
                              @NotEmpty List<@NotNull @Valid RoomInput> roomTypes) {
    }

    public record RoomInput(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,40}") String code,
                            @Positive int capacity, @Positive int quantity,
                            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal nightlyPrice) {
        RoomType toRoomType() {
            return new RoomType(code, capacity, quantity, nightlyPrice);
        }
    }

    public record SearchInput(String city, String locality,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                              @NotNull @Positive Integer guests, @DecimalMin("0") BigDecimal minPrice,
                              @DecimalMin("0") BigDecimal maxPrice, @Min(1) @Max(5) Integer minStars,
                              Set<@NotBlank String> amenities) {
        List<PropertyService.SearchFilter> filters() {
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                throw new IllegalArgumentException("minPrice cannot exceed maxPrice");
            }
            Set<String> requiredAmenities = amenities == null ? Set.of() : amenities.stream()
                    .map(Property::normalizeAmenity).collect(Collectors.toUnmodifiableSet());
            return List.of(
                    (property, room) -> minPrice == null || room.nightlyPrice().compareTo(minPrice) >= 0,
                    (property, room) -> maxPrice == null || room.nightlyPrice().compareTo(maxPrice) <= 0,
                    (property, room) -> minStars == null || property.stars() >= minStars,
                    (property, room) -> property.amenities().containsAll(requiredAmenities));
        }
    }
}
