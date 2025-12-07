package com.mvbr.store.infrastructure.adapter.out.persistence.mapper;

import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper between PaymentDomain (domain layer) and PaymentEntity (infrastructure layer).
 *
 * This is the ANTI-CORRUPTION LAYER - prevents JPA concerns from leaking into the domain.
 */
@Component
public class PaymentPersistenceMapper {

    /**
     * Converts PaymentDomain (domain) to PaymentEntity (JPA).
     *
     * @param domain the domain object
     * @return the JPA entity
     */
    public PaymentEntity toEntity(PaymentDomain domain) {
        if (domain == null) return null;

        return new PaymentEntity(
                domain.getPaymentId(),
                domain.getUserId(),
                domain.getAmount(),
                domain.getCurrency(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }

    /**
     * Converts PaymentEntity (JPA) to PaymentDomain (domain).
     *
     * Uses the "restore from database" constructor.
     *
     * @param entity the JPA entity
     * @return the domain object
     */
    public PaymentDomain toDomain(PaymentEntity entity) {
        if (entity == null) return null;

        return new PaymentDomain(
                entity.getPaymentId(),
                entity.getUserId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
