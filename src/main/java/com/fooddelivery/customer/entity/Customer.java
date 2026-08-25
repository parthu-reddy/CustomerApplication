package com.fooddelivery.customer.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Customer {
    @Id
    @Column(name = "id")
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private UUID id;
    @Column(name = "phone_number")
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private String phoneNumber;
    @CreationTimestamp
    @Column(name = "created_at")
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    

    

    

    

    

    

    

    

    

    

    

    

    

    

    
}
