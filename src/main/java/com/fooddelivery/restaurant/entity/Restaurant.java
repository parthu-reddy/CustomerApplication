package com.fooddelivery.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    
    @Id
    private UUID id;
    
    private String name;
    private String fssaiLicenseNumber;
    private String gstin;
    private String pan;
    private String cin;
    
    private Boolean isActive;
    
    @JsonIgnore
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;
    
    private LocalDateTime createdAt;
}
