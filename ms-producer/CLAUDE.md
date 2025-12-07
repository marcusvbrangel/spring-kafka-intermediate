# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 3.5.8 microservice responsible for **producing** Kafka events in a payment processing system. This is the **ms-producer** component - it receives HTTP requests and publishes events to Kafka topics. The project implements a **Layered Architecture** with clear separation between application, domain, and infrastructure layers.

**Role:** Event Producer only (does NOT consume events)

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
   - Kafka configurations (producers only)
   - Message producers
   - Events: Kafka event schemas
   - JPA repositories for persistence
   - **Outbox Pattern**: Garantees consistency between DB and Kafka

**Dependency Rule:** Application → Domain ← Infrastructure
(Domain layer has NO dependencies on other layers)

### Outbox Pattern Implementation

This service implements the **Transactional Outbox Pattern** to solve the dual-write problem:

**Components:**
- `OutboxEvent` (JPA Entity): Stores events pending publication
- `OutboxService`: Saves events in the same transaction as business data
- `OutboxPublisher` (Scheduled Job): Publishes events to Kafka asynchronously
- `OutboxEventRepository`: Query interface for outbox events

**Flow:**
1. `@Transactional` method saves Payment + OutboxEvent (atomically)
2. Job runs every 5 seconds, fetches PENDING events
3. Publishes to Kafka with retry logic (max 3 attempts)
4. Marks as PUBLISHED or FAILED

**Guarantees:**
- Atomicity: Both DB write and event save succeed or fail together
- At-least-once delivery: Events will eventually be published
- Ordering: Events processed in creation order (FIFO per aggregate)

See `docs/OUTBOX_PATTERN.md` for detailed documentation.

### Kafka Producer Configuration

The project implements **3 distinct Kafka producer profiles** based on reliability requirements:

#### 1. CRITICAL Profile (Payment Approved Events)
- `acks=all` (leader + all replicas must confirm)
- `enable.idempotence=true` (no duplicates)
- `retries=Integer.MAX_VALUE` with 120s delivery timeout
- `max.in.flight.requests=5` (maintains order with idempotence)
- Compression: snappy
- Bean: `criticalKafkaTemplate`

#### 2. DEFAULT Profile
- `acks=1` (leader only)
- Compression: lz4
- Bean: `defaultKafkaTemplate`

#### 3. FAST/Fire-and-Forget Profile
- `acks=0` (no confirmation)
- Larger batches (32KB, 50ms linger)
- Bean: `fastProducerFactory`

### Kafka Topics & Events

**Topic Naming Convention:** `{entity}.{action}.{version}`

Example:
- Topic: `payment.approved.v1`
- Event: `PaymentApprovedEvent`
- Producer: `PaymentApprovedProducer`

**Event Headers:**
All critical events include headers:
- `event-type`: e.g., "PAYMENT_APPROVED"
- `service`: source service name
- `schema-version`: e.g., "v1"

**Partitioning Strategy:**
Events are partitioned by `userId` to guarantee ordering per user.

### Payment Flow (Producer Side) - WITH OUTBOX PATTERN

1. **POST** `/api/payments/approved` → `PaymentController`
2. Request converted to `Payment` domain model
3. `ApprovePaymentService.approvePayment()` validates and marks as approved
4. **@Transactional** block starts:
   - Payment persisted to PostgreSQL database
   - `OutboxEvent` saved to `outbox_event` table (same transaction!)
5. Returns HTTP response to client
6. **Asynchronously** (every 5s):
   - `OutboxPublisher` fetches PENDING events
   - Publishes to Kafka topic `payment.approved.v1`
   - Marks event as PUBLISHED

**Key benefit:** Database and Kafka are guaranteed to be consistent (eventually)

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

3. **Create DTO** in `application.dto.request` if needed for REST endpoints

4. **Update Service Layer** to call the new producer

### Database Persistence

- Uses Spring Data JPA with PostgreSQL
- Flyway migrations in `src/main/resources/db/migration/`
- Current tables: `payment` (stores payment transactions)
- Repository: `PaymentRepository` for data access

### Configuration Notes

- Application runs on port **5050**
- Database: `msstoreproducer` on PostgreSQL
- Kafka broker: `localhost:9092`

## Testing

- Main test class: `StoreApplicationTests.java`
- Spring Kafka Test dependency available for integration tests
- Consider using `@EmbeddedKafka` for Kafka integration tests

## Microservice Architecture

This is part of a microservices architecture:
- **ms-producer** (this service): Receives HTTP requests, persists data, produces Kafka events
- **ms-consumer** (separate service): Consumes Kafka events, processes them, handles DLQ and idempotency

## What This Service Does NOT Do

- Does NOT consume Kafka events (consumer responsibility)
- Does NOT handle Dead Letter Queues (consumer responsibility)
- Does NOT track processed events for idempotency (consumer responsibility)
- Does NOT have retry logic for event processing (consumer responsibility)