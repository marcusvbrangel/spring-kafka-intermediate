package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.PaymentDomain;

import java.util.Optional;

/**
 * OUTBOUND PORT - Payment Persistence.
 *
 * This is a PORT (interface) that defines the contract for payment persistence.
 * The implementation will be in the infrastructure layer (JPA adapter).
 *
 * In Hexagonal Architecture, this is the SECONDARY PORT (driven by the domain).
 * The domain DOES NOT depend on infrastructure - it depends on THIS interface.
 */
public interface PaymentRepositoryPort {

    /**
     * Saves a payment to the database.
     *
     * @param payment the payment domain object
     * @return the saved payment
     */
    PaymentDomain save(PaymentDomain payment);

    /**
     * Finds a payment by its ID.
     *
     * @param paymentId the payment identifier
     * @return optional containing the payment if found
     */
    Optional<PaymentDomain> findById(String paymentId);

    /**
     * Checks if a payment exists.
     *
     * @param paymentId the payment identifier
     * @return true if payment exists, false otherwise
     */
    boolean existsById(String paymentId);
}
