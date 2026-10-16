package com.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "api_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_keys_key_hash", columnNames = "key_hash")
)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, columnDefinition = "int not null default 10")
    private int capacity = 10;

    @Column(name = "refill_rate", nullable = false, columnDefinition = "double not null default 0.1666666667")
    private double refillRate = 10.0 / 60.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean active = true;

    protected ApiKey() {
    }

    public ApiKey(String keyHash) {
        this.keyHash = keyHash;
    }

    public ApiKey(String keyHash, int capacity, double refillRate) {
        this.keyHash = keyHash;
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public boolean isActive() {
        return active;
    }
}
