package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.application.port.out.PaymentRepositoryPort;
import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.mvbr.store.infrastructure.adapter.out.persistence.mapper.PaymentPersistenceMapper;
import com.mvbr.store.infrastructure.adapter.out.persistence.repository.PaymentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OUTBOUND ADAPTER - Payment Persistence Implementation.
 *
 * Implements PaymentRepositoryPort (outbound port from application layer).
 * Uses Spring Data JPA (PaymentJpaRepository) for actual database operations.
 *
 * This is the ADAPTER that translates between:
 * - Domain layer (PaymentDomain)
 * - Infrastructure layer (PaymentEntity + JPA)
 *
 * DEPENDENCY DIRECTION: Application → Port ← Adapter (Dependency Inversion!)
 */
@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    public PaymentPersistenceAdapter(
            PaymentJpaRepository jpaRepository,
            PaymentPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PaymentDomain save(PaymentDomain payment) {
        // Domain → Entity → JPA save → Entity → Domain
        PaymentEntity entity = mapper.toEntity(payment);
        PaymentEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentDomain> findById(String paymentId) {
        // JPA find → Entity → Domain
        return jpaRepository.findById(paymentId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(String paymentId) {
        return jpaRepository.existsById(paymentId);
    }
}
