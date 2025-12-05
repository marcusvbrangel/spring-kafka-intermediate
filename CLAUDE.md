# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 3.5.8 application demonstrating intermediate Kafka concepts with a focus on payment processing events. The project implements a **Layered Architecture** with clear separation between application, domain, and infrastructure layers.

**Tech Stack:**
- Java 21
- Spring Boot 3.5.8
- Spring Kafka
- Maven (wrapper included)
- Docker Compose (Kafka + Zookeeper + Redpanda Console)

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
# Start Kafka cluster (Zookeeper + Kafka + Redpanda Console)
docker compose up -d

# Stop infrastructure
docker compose down

# View Kafka messages in browser
# http://localhost:8089 (Redpanda Console)
```

### Application
- Application runs on port **5050**
- Kafka broker: **localhost:9092**
- Kafka has **3 partitions** by default (configured in docker-compose.yaml)

## Architecture Overview

### Layered Architecture Pattern

The codebase follows a strict **3-layer architecture**:

1. **Application Layer** (`com.mvbr.store.application`)
   - Controllers: REST endpoints
   - DTOs: Request/Response objects
   - Services: Application business logic and orchestration

2. **Domain Layer** (`com.mvbr.store.domain`)
   - Models: Core business entities with validation rules
   - Rich domain objects (e.g., `Payment` with state transitions)

3. **Infrastructure Layer** (`com.mvbr.store.infrastructure`)
   - Kafka configurations (producers/consumers)
   - Message producers and consumers
   - Events: Kafka event schemas
   - External integrations

**Dependency Rule:** Application → Domain ← Infrastructure
(Domain layer has NO dependencies on other layers)

### Kafka Configuration Strategy

The project implements **3 distinct Kafka producer/consumer profiles** based on reliability requirements:

#### 1. CRITICAL Profile (Payment Approved Events)
**Producer Config:**
- `acks=all` (leader + all replicas must confirm)
- `enable.idempotence=true` (no duplicates)
- `retries=Integer.MAX_VALUE` with 120s delivery timeout
- `max.in.flight.requests=5` (maintains order with idempotence)
- Compression: snappy
- Bean: `criticalKafkaTemplate`

**Consumer Config:**
- Manual commit (`ENABLE_AUTO_COMMIT=false`)
- `MAX_POLL_RECORDS=1` (process one at a time)
- Concurrency: 1
- Exponential backoff retry (5 attempts, 1s→10s)
- Bean: `criticalKafkaListenerContainerFactory`

#### 2. DEFAULT Profile
**Producer:**
- `acks=1` (leader only)
- Compression: lz4
- Bean: `defaultKafkaTemplate`

**Consumer:**
- Auto commit enabled
- Concurrency: 3
- Bean: `defaultKafkaListenerContainerFactory`

#### 3. FAST/Fire-and-Forget Profile
**Producer:**
- `acks=0` (no confirmation)
- Larger batches (32KB, 50ms linger)
- Bean: `fastProducerFactory`

**Consumer:**
- Auto commit
- `MAX_POLL_RECORDS=500`
- Concurrency: 8
- Bean: `fasterKafkaListenerContainerFactory`

### Kafka Topics & Events

**Topic Naming Convention:** `{entity}.{action}.{version}`

Example:
- Topic: `payment.approved.v1`
- Event: `PaymentApprovedEvent`
- Producer: `PaymentApprovedProducer`
- Consumer: `PaymentApprovedConsumer`

**Event Headers:**
All critical events include headers:
- `event-type`: e.g., "PAYMENT_APPROVED"
- `service`: source service name
- `schema-version`: e.g., "v1"

**Partitioning Strategy:**
Events are partitioned by `userId` to guarantee ordering per user.

### Payment Flow

1. **POST** `/api/payments/approved` → `PaymentController`
2. Request converted to `Payment` domain model
3. `PaymentService.approvePayment()` validates and marks as approved
4. Constructs `PaymentApprovedEvent` with metadata
5. `PaymentApprovedProducer` sends to Kafka topic `payment.approved.v1`
6. `PaymentApprovedConsumer` receives and processes event

### Domain Model Notes

**Payment Entity** (`src/main/java/com/mvbr/store/domain/model/Payment.java`):
- Immutable construction (final fields)
- Validation in constructor (fail-fast)
- State machine transitions (`PENDING` → `APPROVED` or `CANCELED`)
- Cannot approve canceled payments or cancel approved ones
- Business logic lives in domain model (e.g., `isValid()`, `markApproved()`)

## Development Conventions

### When Adding New Event Types

1. **Create Event Record** in `infrastructure.messaging.event`
   - Use Java records for immutability
   - Include eventId, timestamp, and all business data

2. **Create Producer** in `infrastructure.messaging.producer`
   - Choose appropriate KafkaTemplate (`@Qualifier`)
   - Set topic name with versioning
   - Add event headers
   - Use appropriate partition key

3. **Create Consumer** in `infrastructure.messaging.consumer`
   - Use `@KafkaListener` with correct containerFactory
   - Handle null events (deserialization errors)
   - Consider manual commit for critical events

4. **Create DTO** in `application.dto.request` if needed for REST endpoints

### Kafka Consumer Error Handling

All consumers use `ErrorHandlingDeserializer` wrapper:
- Bad messages are logged, not crashed
- Check for `null` events in consumer methods
- Retry logic configured via `DefaultErrorHandler` with exponential backoff

### Configuration Notes

- Kafka broker URL is hardcoded to `localhost:9092` (consider externalizing)
- `JsonDeserializer.TRUSTED_PACKAGES` set to `*` (consider restricting in production)
- `AUTO_OFFSET_RESET` set to `latest` (only consume new messages)

## Testing

- Main test class: `StoreApplicationTests.java`
- Spring Kafka Test dependency available for integration tests
- Consider using `@EmbeddedKafka` for Kafka integration tests

## Current Limitations

- No database/repository layer yet (commented in PaymentService)
- Single service architecture (no microservices yet)
- No transaction management (Kafka + DB)
- No dead letter queue (DLQ) implementation