package com.mvbr.store.infrastructure.adapter.out.persistence.repository;

import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository Tests for PaymentJpaRepository.
 *
 * Uses @DataJpaTest for lightweight JPA testing:
 * - Configures H2 in-memory database
 * - Scans @Entity classes
 * - Configures Spring Data JPA
 * - Provides TestEntityManager
 * - Transactional by default (rollback after each test)
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PaymentJpaRepository - Persistence Tests")
class PaymentJpaRepositoryTest {

    @Autowired
    private PaymentJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    // =======================================
    //      SAVE OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should save payment entity successfully")
    void shouldSavePaymentEntity() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_1", "user_1", "100.00", "USD");

        // When
        PaymentEntity savedPayment = repository.save(payment);

        // Then
        assertThat(savedPayment).isNotNull();
        assertThat(savedPayment.getPaymentId()).isEqualTo("pay_1");
        assertThat(savedPayment.getUserId()).isEqualTo("user_1");
        assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(savedPayment.getCurrency()).isEqualTo("USD");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("Should generate payment ID in database")
    void shouldPersistPaymentInDatabase() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_db_1", "user_1", "50.00", "BRL");

        // When
        repository.save(payment);
        entityManager.flush();
        entityManager.clear();

        // Then
        PaymentEntity found = entityManager.find(PaymentEntity.class, "pay_db_1");
        assertThat(found).isNotNull();
        assertThat(found.getPaymentId()).isEqualTo("pay_db_1");
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should update existing payment")
    void shouldUpdateExistingPayment() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_update", "user_1", "100.00", "USD");
        repository.save(payment);
        entityManager.flush();

        // When - Update amount
        payment.setAmount(new BigDecimal("200.00"));
        payment.setStatus(PaymentStatus.CANCELED);
        PaymentEntity updated = repository.save(payment);
        entityManager.flush();
        entityManager.clear();

        // Then
        PaymentEntity found = repository.findById("pay_update").orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    // =======================================
    //      FIND OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should find payment by ID")
    void shouldFindPaymentById() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_find_1", "user_1", "75.50", "EUR");
        repository.save(payment);
        entityManager.flush();

        // When
        Optional<PaymentEntity> found = repository.findById("pay_find_1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo("pay_find_1");
        assertThat(found.get().getUserId()).isEqualTo("user_1");
        assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(found.get().getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should return empty when payment not found")
    void shouldReturnEmptyWhenPaymentNotFound() {
        // When
        Optional<PaymentEntity> found = repository.findById("non_existent");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find all payments")
    void shouldFindAllPayments() {
        // Given
        repository.save(createPaymentEntity("pay_1", "user_1", "100.00", "USD"));
        repository.save(createPaymentEntity("pay_2", "user_2", "200.00", "EUR"));
        repository.save(createPaymentEntity("pay_3", "user_3", "300.00", "BRL"));
        entityManager.flush();

        // When
        List<PaymentEntity> payments = repository.findAll();

        // Then
        assertThat(payments).hasSize(3);
        assertThat(payments)
                .extracting(PaymentEntity::getPaymentId)
                .containsExactlyInAnyOrder("pay_1", "pay_2", "pay_3");
    }

    // =======================================
    //      EXISTS OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should return true when payment exists")
    void shouldReturnTrueWhenPaymentExists() {
        // Given
        repository.save(createPaymentEntity("pay_exists", "user_1", "100.00", "USD"));
        entityManager.flush();

        // When
        boolean exists = repository.existsById("pay_exists");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when payment does not exist")
    void shouldReturnFalseWhenPaymentDoesNotExist() {
        // When
        boolean exists = repository.existsById("non_existent");

        // Then
        assertThat(exists).isFalse();
    }

    // =======================================
    //      DELETE OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should delete payment by ID")
    void shouldDeletePaymentById() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_delete", "user_1", "100.00", "USD");
        repository.save(payment);
        entityManager.flush();

        // When
        repository.deleteById("pay_delete");
        entityManager.flush();

        // Then
        assertThat(repository.existsById("pay_delete")).isFalse();
    }

    @Test
    @DisplayName("Should delete payment entity")
    void shouldDeletePaymentEntity() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_delete_2", "user_1", "100.00", "USD");
        repository.save(payment);
        entityManager.flush();

        // When
        repository.delete(payment);
        entityManager.flush();

        // Then
        assertThat(repository.existsById("pay_delete_2")).isFalse();
    }

    @Test
    @DisplayName("Should delete all payments")
    void shouldDeleteAllPayments() {
        // Given
        repository.save(createPaymentEntity("pay_1", "user_1", "100.00", "USD"));
        repository.save(createPaymentEntity("pay_2", "user_2", "200.00", "EUR"));
        entityManager.flush();

        // When
        repository.deleteAll();
        entityManager.flush();

        // Then
        assertThat(repository.findAll()).isEmpty();
    }

    // =======================================
    //      COUNT OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should count all payments")
    void shouldCountAllPayments() {
        // Given
        repository.save(createPaymentEntity("pay_1", "user_1", "100.00", "USD"));
        repository.save(createPaymentEntity("pay_2", "user_2", "200.00", "EUR"));
        repository.save(createPaymentEntity("pay_3", "user_3", "300.00", "BRL"));
        entityManager.flush();

        // When
        long count = repository.count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return zero when no payments exist")
    void shouldReturnZeroWhenNoPaymentsExist() {
        // When
        long count = repository.count();

        // Then
        assertThat(count).isZero();
    }

    // =======================================
    //      EDGE CASES
    // =======================================

    @Test
    @DisplayName("Should handle large amount values")
    void shouldHandleLargeAmountValues() {
        // Given
        PaymentEntity payment = createPaymentEntity(
                "pay_large",
                "user_1",
                "999999999.99",
                "USD"
        );

        // When
        PaymentEntity saved = repository.save(payment);
        entityManager.flush();
        entityManager.clear();

        // Then
        PaymentEntity found = repository.findById("pay_large").orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
    }

    @Test
    @DisplayName("Should handle minimum amount values")
    void shouldHandleMinimumAmountValues() {
        // Given
        PaymentEntity payment = createPaymentEntity("pay_min", "user_1", "0.01", "USD");

        // When
        PaymentEntity saved = repository.save(payment);
        entityManager.flush();
        entityManager.clear();

        // Then
        PaymentEntity found = repository.findById("pay_min").orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("Should persist different payment statuses")
    void shouldPersistDifferentPaymentStatuses() {
        // Given
        PaymentEntity approved = createPaymentEntity("pay_approved", "user_1", "100.00", "USD");
        approved.setStatus(PaymentStatus.APPROVED);

        PaymentEntity pending = createPaymentEntity("pay_pending", "user_2", "200.00", "EUR");
        pending.setStatus(PaymentStatus.PENDING);

        PaymentEntity canceled = createPaymentEntity("pay_canceled", "user_3", "300.00", "BRL");
        canceled.setStatus(PaymentStatus.CANCELED);

        // When
        repository.save(approved);
        repository.save(pending);
        repository.save(canceled);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(repository.findById("pay_approved").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.APPROVED);
        assertThat(repository.findById("pay_pending").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(repository.findById("pay_canceled").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
    }

    // =======================================
    //      HELPER METHODS
    // =======================================

    private PaymentEntity createPaymentEntity(String paymentId, String userId, String amount, String currency) {
        return new PaymentEntity(
                paymentId,
                userId,
                new BigDecimal(amount),
                currency,
                PaymentStatus.APPROVED,
                Instant.now().toEpochMilli()
        );
    }
}