# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 3.5.8 microservice responsible for **consuming** Kafka events in a payment processing system. This is the **ms-consumer** component - it consumes events from Kafka topics, processes them with idempotency guarantees, and handles Dead Letter Queue (DLQ) scenarios. The project implements a **Layered Architecture** with clear separation between application, domain, and infrastructure layers.

**Role:** Event Consumer only (does NOT produce events or expose HTTP endpoints)

**Tech Stack:**
- Java 21
- Spring Boot 3.5.8
- Spring Kafka
- Spring Data JPA
- PostgreSQL
- Flyway (database migrations)
- Maven (wrapper included)

## Build & Run Commands

### Maven Commands (use wrapper)
```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package (create JAR)
./mvnw clean package

# Run application
./mvnw spring-boot:run
```

### Infrastructure
```bash
# Start Kafka cluster + PostgreSQL (from parent directory)
docker compose up -d

# Stop infrastructure
docker compose down

# View Kafka messages in browser
# http://localhost:8089 (Redpanda Console)
```

### Application
- **No HTTP server** (pure Kafka consumer)
- Database: `msstoreconsumer` on PostgreSQL (port 5432)
- Kafka broker: **localhost:9092**

## Architecture Overview

### Layered Architecture Pattern

The codebase follows a strict **3-layer architecture**:

1. **Application Layer** (`com.mvbr.store.application`)
   - Services: Business logic for processing consumed events
   - No Controllers (consumer doesn't expose HTTP endpoints)
   - No DTOs (consumer works directly with domain models and events)

2. **Domain Layer** (`com.mvbr.store.domain`)
   - Models: Core business entities (`Payment`, `ProcessedEvent`)
   - Repositories: Interfaces for data access
   - Rich domain objects with validation rules

3. **Infrastructure Layer** (`com.mvbr.store.infrastructure`)
   - Kafka consumer configurations
   - Kafka consumers (listeners)
   - Events: Kafka event schemas (consumed from topics)
   - JPA repositories for persistence
   - DLQ (Dead Letter Queue) management

**Dependency Rule:** Application → Domain ← Infrastructure
(Domain layer has NO dependencies on other layers)

### Kafka Consumer Configuration

The project implements **3 distinct Kafka consumer profiles** based on reliability requirements:

#### 1. CRITICAL Profile (Payment Approved Events)
- Manual commit (`ENABLE_AUTO_COMMIT=false`)
- `MAX_POLL_RECORDS=1` (process one message at a time)
- Concurrency: 1 (single thread)
- Exponential backoff retry (5 attempts, 1s→10s)
- Bean: `criticalKafkaListenerContainerFactory`
- **Use case:** Financial transactions requiring guaranteed processing

#### 2. DEFAULT Profile
- Auto commit enabled
- Concurrency: 3
- Bean: `defaultKafkaListenerContainerFactory`
- **Use case:** Standard events with moderate reliability needs

#### 3. FAST/High-Throughput Profile
- Auto commit enabled
- `MAX_POLL_RECORDS=500` (batch processing)
- Concurrency: 8
- Bean: `fasterKafkaListenerContainerFactory`
- **Use case:** High-volume events where occasional loss is acceptable

### Idempotency Pattern

The consumer implements **at-least-once delivery with idempotency**:

1. **ProcessedEvent Table:** Tracks all successfully processed events by `event_id`
2. **Check before processing:** Query `ProcessedEventRepository` to see if event was already processed
3. **Atomic save:** Use `@Transactional` to save both business data + processed event record
4. **Duplicate detection:** If event_id exists, skip processing and acknowledge message

**Benefits:**
- Prevents duplicate processing on Kafka retries
- Guarantees exactly-once semantics at application level
- Enables safe manual replay of messages

### Dead Letter Queue (DLQ) Pattern

The consumer implements comprehensive DLQ handling:

#### DLQ Flow
1. Consumer receives message from main topic (e.g., `payment.approved.v1`)
2. Processing fails after all retry attempts (5 retries with exponential backoff)
3. Message is sent to DLQ topic (e.g., `payment.approved.v1.dlq`)
4. Original consumer acknowledges message (prevents infinite loop)
5. DLQ messages can be reprocessed later via `DLQReprocessor`

#### DLQReprocessor
- **Disabled by default** (`dlq.reprocessor.enabled=false`)
- Polls DLQ topic and republishes messages to main topic
- **Only enable after fixing the bug that caused failures!**
- Configuration in `application.yaml`:

```yaml
dlq:
  reprocessor:
    enabled: false  # Set to true to activate automatic reprocessing
```

**When to enable DLQReprocessor:**
- ✅ After fixing bug in consumer code and deploying
- ✅ After external service recovers (e.g., API is back online)
- ✅ To reprocess messages after incident is resolved

**When to keep DLQReprocessor disabled:**
- ❌ While bug still exists (prevents infinite loop!)
- ❌ During incident investigation
- ❌ In dev/test environments (use manual reprocessing)

### Kafka Topics & Events

**Topic Naming Convention:** `{entity}.{action}.{version}`

Consumed topics:
- `payment.approved.v1` - Payment approval events
- `payment.approved.v1.dlq` - Dead letter queue for failed payments

**Event Schemas:**
- `PaymentApprovedEvent` - Consumed from `payment.approved.v1`

**Event Headers (expected):**
- `event-type`: e.g., "PAYMENT_APPROVED"
- `service`: source service name (e.g., "ms-producer")
- `schema-version`: e.g., "v1"

### Payment Processing Flow (Consumer Side)

1. **Kafka message arrives** at `payment.approved.v1` topic
2. **PaymentApprovedConsumer** receives event
3. **Check idempotency:** Query `ProcessedEventRepository` for `event.eventId()`
4. **If already processed:** Skip and acknowledge (idempotent)
5. **If new event:**
   - Parse `PaymentApprovedEvent`
   - Convert to `Payment` domain model
   - Call `PaymentService.processPayment(payment)`
   - Save payment to database
   - Save `ProcessedEvent` record (same transaction)
   - Commit Kafka offset (manual commit)
6. **On error:** Retry with exponential backoff → send to DLQ if all retries fail

### Domain Model Notes

**Payment Entity** (`src/main/java/com/mvbr/store/domain/model/Payment.java`):
- Immutable construction (final fields)
- Validation in constructor (fail-fast)
- State machine transitions (`PENDING` → `APPROVED` or `CANCELED`)
- Business logic lives in domain model

**ProcessedEvent Entity** (`src/main/java/com/mvbr/store/domain/model/ProcessedEvent.java`):
- Tracks processed Kafka events for idempotency
- Unique constraint on `event_id`
- Stores Kafka metadata (partition, offset, topic)
- Enables debugging and audit trail

## Database Schema

Flyway migrations in `src/main/resources/db/migration/`

### Tables

#### `payment`
Stores payment transaction data processed from events.

```sql
CREATE TABLE payment (
    payment_id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL
);
```

**Indexes:**
- `idx_payment_user_id` - Fast lookups by user
- `idx_payment_status` - Fast lookups by status
- `idx_payment_created_at` - Time-based queries

#### `processed_events`
Tracks processed Kafka events for idempotency (critical for consumer).

```sql
CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(100),
    processed_at TIMESTAMP NOT NULL,
    kafka_partition INTEGER,
    kafka_offset BIGINT
);
```

**Indexes:**
- `idx_event_id` - Fast duplicate detection (UNIQUE)
- `idx_processed_at` - Cleanup/retention policies
- `idx_topic_event_type` - Monitoring/debugging

## Development Conventions

### When Adding New Consumer Types

1. **Verify Event Schema** in `infrastructure.messaging.event`
   - Event must match producer's schema
   - Use Java records for immutability

2. **Create Consumer** in `infrastructure.messaging.consumer`
   - Use `@KafkaListener` with appropriate containerFactory
   - Choose factory based on reliability needs (critical/default/fast)
   - **Always check for null events** (deserialization errors)
   - Implement idempotency check via `ProcessedEventRepository`
   - Use `@Transactional` for atomic saves

3. **Handle Errors Gracefully**
   - Log errors with context (event_id, partition, offset)
   - Let `DefaultErrorHandler` retry with backoff
   - Failed messages will go to DLQ automatically

4. **Update Service Layer** if new business logic is needed

### Error Handling Best Practices

**Consumers use `ErrorHandlingDeserializer` wrapper:**
- Bad messages are logged, not crashed
- Always check for `null` events in consumer methods
- Retry logic configured via `DefaultErrorHandler` with exponential backoff

**Example Consumer Pattern:**
```java
@KafkaListener(
    topics = "payment.approved.v1",
    containerFactory = "criticalKafkaListenerContainerFactory"
)
public void consume(PaymentApprovedEvent event, Acknowledgment ack) {
    if (event == null) {
        log.error("Received null event - deserialization failed");
        ack.acknowledge(); // Skip corrupted message
        return;
    }

    // Check idempotency
    if (processedEventRepository.existsByEventId(event.eventId())) {
        log.info("Event already processed: {}", event.eventId());
        ack.acknowledge();
        return;
    }

    try {
        // Process event
        paymentService.processPayment(toPayment(event));

        // Save processed event
        processedEventRepository.save(new ProcessedEvent(event));

        // Manual commit
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Error processing event: {}", event.eventId(), e);
        throw e; // Let DefaultErrorHandler retry
    }
}
```

### Configuration Notes

- Database: `msstoreconsumer` on PostgreSQL
- Kafka broker: `localhost:9092`
- Consumer group IDs are auto-generated from `spring.application.name`
- `AUTO_OFFSET_RESET=latest` (only consume new messages on first start)

## Testing

- Main test class: `StoreApplicationTests.java`
- Spring Kafka Test dependency available for integration tests
- Consider using `@EmbeddedKafka` for consumer integration tests
- Test idempotency by sending duplicate events with same `event_id`

## Microservice Architecture

This is part of a microservices architecture:
- **ms-producer** (separate service): Receives HTTP requests, persists data, produces Kafka events
- **ms-consumer** (this service): Consumes Kafka events, processes them, handles DLQ and idempotency

## What This Service Does NOT Do

- Does NOT expose HTTP endpoints (no REST API)
- Does NOT produce Kafka events (consumer only)
- Does NOT receive HTTP requests
- Does NOT run a web server (spring-boot-starter-web removed)

## Monitoring & Observability

**Key Metrics to Monitor:**
- Consumer lag (how far behind is consumer from producer)
- DLQ message count (sign of processing failures)
- `processed_events` table growth (ensures idempotency works)
- Error rate in consumer logs

**Useful Queries:**
```sql
-- Check recent processed events
SELECT * FROM processed_events ORDER BY processed_at DESC LIMIT 10;

-- Count events by topic
SELECT topic, COUNT(*) FROM processed_events GROUP BY topic;

-- Find duplicate processing attempts (should be none)
SELECT event_id, COUNT(*) FROM processed_events GROUP BY event_id HAVING COUNT(*) > 1;
```

## Troubleshooting

### DLQ Messages Accumulating
1. Check consumer logs for errors
2. Verify database connectivity
3. Check if external dependencies are down
4. Fix the bug causing failures
5. Deploy fixed version
6. Enable `dlq.reprocessor.enabled=true` to reprocess

### Consumer Not Processing Messages
1. Check Kafka connectivity (`localhost:9092`)
2. Verify topic exists and has messages (Redpanda Console)
3. Check consumer group offset (may be at end of topic)
4. Verify database connection (consumer needs DB for persistence)

### Duplicate Processing Detected
1. Check `processed_events` table for duplicate `event_id`
2. Verify `@Transactional` is applied on consumer methods
3. Ensure manual commit happens AFTER saving `ProcessedEvent`
