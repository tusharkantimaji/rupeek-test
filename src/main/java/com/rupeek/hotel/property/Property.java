package com.rupeek.hotel.property;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record Property(UUID id, String ownerId, String name, String city, String locality,
                       int stars, Set<String> amenities, List<RoomType> roomTypes) {
    public Property {
        amenities = amenities.stream().map(Property::normalizeAmenity).collect(Collectors.toUnmodifiableSet());
        roomTypes = List.copyOf(roomTypes);
        if (roomTypes.stream().map(RoomType::code).distinct().count() != roomTypes.size()) {
            throw new IllegalArgumentException("Room type codes must be unique within a property");
        }
    }

    public RoomType roomType(String code) {
        return roomTypes.stream().filter(room -> room.code().equals(code)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Room type not found: " + code));
    }

    public static String normalizeAmenity(String amenity) {
        return amenity.strip().toLowerCase(Locale.ROOT);
    }
}
