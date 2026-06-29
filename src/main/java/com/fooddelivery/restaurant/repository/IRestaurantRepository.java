package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IRestaurantRepository extends JpaRepository<Restaurant, UUID> {

    @Query(value = "SELECT r.* FROM restaurants r WHERE r.is_active = true AND ST_DWithin(r.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :distanceInMeters)", nativeQuery = true)
    List<Restaurant> findNearbyActiveRestaurants(@Param("lat") double lat, @Param("lng") double lng, @Param("distanceInMeters") double distanceInMeters);
}
