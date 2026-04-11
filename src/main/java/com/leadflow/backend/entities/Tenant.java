package com.leadflow.backend.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    /* ======================================================
       ID
       ====================================================== */

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /* ======================================================
       FIELDS
       ====================================================== */

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* ======================================================
       CONSTRUCTORS
       ====================================================== */

    protected Tenant() {
    }

    public Tenant(String name) {
        setName(name);
    }

    /* ======================================================
       GETTERS
       ====================================================== */

    public UUID getId() { return id; }

    public String getName() { return name; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }

    /* ======================================================
       DOMAIN BEHAVIOR
       ====================================================== */

    public void rename(String newName) {
        setName(newName);
    }

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /* ======================================================
       VALIDATION
       ====================================================== */

    private void setName(String name) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tenant name cannot be blank");
        }

        if (name.length() > 100) {
            throw new IllegalArgumentException("Tenant name too long");
        }

        this.name = name.trim();
    }

    /* ======================================================
       EQUALS & HASHCODE
       ====================================================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tenant other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /* ======================================================
       TO STRING
       ====================================================== */

    @Override
    public String toString() {
        return "Tenant{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", deletedAt=" + deletedAt +
                '}';
    }
}