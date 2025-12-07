package com.mvbr.store.infrastructure.adapter.out.persistence.repository;

import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for PaymentEntity.
 *
 * This is the INFRASTRUCTURE layer - handles database operations.
 * Extends JpaRepository to get CRUD operations for free.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    // Spring Data JPA provides implementations automatically
    // Methods: save(), findById(), existsById(), etc.
}
