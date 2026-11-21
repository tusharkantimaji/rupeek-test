package com.rupeek.hotel.property;

import com.rupeek.hotel.booking.Availability;
import com.rupeek.hotel.booking.Stay;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyService {
    private final PropertyRepository properties;
    private final Availability availability;
    private final Clock clock;

    public PropertyService(PropertyRepository properties, Availability availability, Clock clock) {
        this.properties = properties;
        this.availability = availability;
        this.clock = clock;
    }

    public Property add(String ownerId, String name, String city, String locality, int stars,
                        Set<String> amenities, List<RoomType> roomTypes) {
        return properties.save(new Property(UUID.randomUUID(), ownerId.strip(), name.strip(),
                city.strip(), locality.strip(), stars, amenities, roomTypes));
    }

    public Property get(UUID id) {
        return properties.findById(id).orElseThrow(() -> new NoSuchElementException("Property not found: " + id));
    }

    public List<SearchResult> search(String city, String locality, Stay stay, int guests, List<SearchFilter> filters) {
        stay.requireStartOnOrAfter(LocalDate.now(clock));
        return properties.findAll().stream()
                .filter(property -> matchesLocation(city, property.city()) && matchesLocation(locality, property.locality()))
                .sorted(Comparator.comparing(Property::name).thenComparing(Property::id))
                .map(property -> match(property, stay, guests, filters))
                .filter(result -> !result.roomTypes().isEmpty())
                .toList();
    }

    private SearchResult match(Property property, Stay stay, int guests, List<SearchFilter> filters) {
        List<RoomType> rooms = property.roomTypes().stream()
                .filter(room -> room.capacity() >= guests)
                .filter(room -> filters.stream().allMatch(filter -> filter.matches(property, room)))
                .filter(room -> availability.hasRoom(property.id(), room, stay))
                .toList();
        return new SearchResult(property.id(), property.name(), property.city(), property.locality(),
                property.stars(), property.amenities(), rooms);
    }

    private boolean matchesLocation(String query, String actual) {
        return query == null || query.strip().equalsIgnoreCase(actual);
    }

    @FunctionalInterface
    public interface SearchFilter {
        boolean matches(Property property, RoomType room);
    }

    public record SearchResult(UUID id, String name, String city, String locality, int stars,
                               Set<String> amenities, List<RoomType> roomTypes) {
    }
}
