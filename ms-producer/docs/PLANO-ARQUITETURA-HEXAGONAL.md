# Plano de Implementação - Arquitetura Hexagonal (MS-Producer)

## 📋 Índice

1. [Fundamentos da Arquitetura Hexagonal](#1-fundamentos-da-arquitetura-hexagonal)
2. [Análise do Estado Atual](#2-análise-do-estado-atual)
3. [Estrutura Final Proposta](#3-estrutura-final-proposta)
4. [Passos de Implementação](#4-passos-de-implementação)
5. [Código de Exemplo](#5-código-de-exemplo)
6. [Testes](#6-testes)
7. [Checklist de Validação](#7-checklist-de-validação)

---

## 1. Fundamentos da Arquitetura Hexagonal

### O que é?

**Arquitetura Hexagonal** (também conhecida como **Ports & Adapters**) é um padrão arquitetural que visa:

- ✅ **Desacoplar** a lógica de negócio da infraestrutura
- ✅ **Facilitar testes** (fácil mockar dependências externas)
- ✅ **Permitir trocar implementações** sem afetar o domínio
- ✅ **Isolar regras de negócio** de frameworks e bibliotecas

### Conceitos Principais:

#### 🔷 **Domínio (Hexágono Central)**
- **O QUE** o sistema faz (regras de negócio)
- **NÃO depende** de nada externo (frameworks, bancos, APIs)
- Contém: Entidades, Value Objects, Domain Services

#### 🔌 **Portas (Ports)**
Interfaces que definem **COMO** o domínio se comunica com o mundo externo.

- **Inbound Ports (Driving)**: Casos de uso expostos para o mundo externo
  - Ex: `ApprovePaymentUseCase`, `CreatePaymentUseCase`
  - Quem chama: Controllers, APIs, Schedulers

- **Outbound Ports (Driven)**: Dependências que o domínio precisa
  - Ex: `PaymentRepository`, `PaymentEventPublisher`, `NotificationSender`
  - Quem implementa: Adapters (infraestrutura)

#### 🔌 **Adaptadores (Adapters)**
Implementações concretas das portas.

- **Inbound Adapters (Driving)**: Recebem chamadas externas e invocam casos de uso
  - Ex: `PaymentController` (REST), `PaymentCliAdapter` (CLI)

- **Outbound Adapters (Driven)**: Implementam as portas de saída
  - Ex: `JpaPaymentRepository`, `KafkaPaymentEventPublisher`, `EmailNotificationSender`

### Diagrama Conceitual:

```
┌─────────────────────────────────────────────────────────────────┐
│                     MUNDO EXTERNO                               │
│  (HTTP, CLI, Schedulers, Kafka, Database, APIs, Email, etc.)   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    INBOUND ADAPTERS                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │PaymentREST   │  │PaymentCLI    │  │EventListener │         │
│  │Controller    │  │Adapter       │  │Adapter       │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     INBOUND PORTS                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ interface ApprovePaymentUseCase {                        │  │
│  │   PaymentResponse execute(ApprovePaymentCommand cmd);    │  │
│  │ }                                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN (HEXÁGONO)                            │
│  ┌─────────────────────────────────────────────────────┐       │
│  │  UseCases (Application Logic)                       │       │
│  │  - ApprovePaymentService                            │       │
│  │  - CreatePaymentService                             │       │
│  └─────────────────────────────────────────────────────┘       │
│  ┌─────────────────────────────────────────────────────┐       │
│  │  Domain Models (Business Logic)                     │       │
│  │  - Payment (entity with business rules)             │       │
│  │  - PaymentStatus (value object)                     │       │
│  └─────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     OUTBOUND PORTS                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ interface PaymentRepository {                            │  │
│  │   Payment save(Payment payment);                         │  │
│  │   Optional<Payment> findById(String id);                 │  │
│  │ }                                                         │  │
│  │                                                           │  │
│  │ interface PaymentEventPublisher {                        │  │
│  │   void publish(PaymentEvent event);                      │  │
│  │ }                                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    OUTBOUND ADAPTERS                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │JpaPayment    │  │KafkaPayment  │  │EmailSender   │         │
│  │Repository    │  │EventPublisher│  │Adapter       │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     MUNDO EXTERNO                               │
│      (PostgreSQL, Kafka, SMTP, External APIs, etc.)            │
└─────────────────────────────────────────────────────────────────┘
```

### Regra de Dependência (Dependency Rule):

**TUDO depende do Domínio, mas o Domínio não depende de NADA!**

```
Adapters → Ports → Domain
   ↓         ↓       ↑
  Só conhecem     Não conhece
  interfaces      nada externo
```

---

## 2. Análise do Estado Atual

### 🔍 Estrutura Atual (Layered Architecture):

```
ms-producer/
├── src/main/java/com/mvbr/store/
│   ├── StoreApplication.java
│   ├── application/
│   │   ├── controller/
│   │   │   └── PaymentController.java ⚠️ (acoplado com DTO HTTP)
│   │   ├── dto/request/
│   │   │   ├── PaymentApprovedRequest.java ⚠️ (vazando para domínio)
│   │   │   └── PaymentNotificationRequest.java
│   │   ├── mapper/
│   │   │   ├── PaymentRequestMapper.java ⚠️ (acoplamento)
│   │   │   └── PaymentEventMapper.java ⚠️ (acoplamento)
│   │   └── service/
│   │       └── PaymentService.java ⚠️ (depende de Kafka, JPA)
│   ├── domain/
│   │   └── model/
│   │       ├── Payment.java ✅ (boa lógica de domínio)
│   │       └── PaymentStatus.java ✅
│   └── infrastructure/
│       ├── config/kafka/
│       │   └── KafkaProducerConfig.java
│       ├── messaging/
│       │   ├── producer/
│       │   │   ├── PaymentApprovedProducer.java ⚠️ (implementação concreta)
│       │   │   └── PaymentNotificationProducer.java
│       │   └── event/
│       │       ├── PaymentApprovedEvent.java
│       │       └── PaymentNotificationEvent.java
│       └── persistence/
│           └── PaymentRepository.java ⚠️ (Spring Data JPA)
```

### ❌ Problemas Identificados:

1. **PaymentService depende diretamente de:**
   - `PaymentRepository` (Spring Data JPA)
   - `PaymentApprovedProducer` (Kafka)
   - DTOs HTTP (`PaymentApprovedRequest`)
   - Mappers específicos

2. **Domínio vazando para fora:**
   - `Payment` é usado diretamente no Controller
   - Não há separação clara entre modelo de domínio e modelo de persistência

3. **Difícil de testar:**
   - Testar `PaymentService` requer Kafka e JPA mockados
   - Testes unitários puros são impossíveis

4. **Difícil de trocar implementações:**
   - Mudar de Kafka para RabbitMQ = refatorar `PaymentService`
   - Mudar de JPA para JDBC = refatorar `PaymentService`

---

## 3. Estrutura Final Proposta

### 🎯 Nova Estrutura (Hexagonal):

```
ms-producer/
├── src/main/java/com/mvbr/store/
│   ├── StoreApplication.java
│   │
│   ├── domain/ ──────────────────────────────────┐
│   │   ├── model/                                │ HEXÁGONO
│   │   │   ├── Payment.java                      │ (Núcleo)
│   │   │   └── PaymentStatus.java                │
│   │   ├── exception/                            │ Não depende
│   │   │   ├── PaymentNotFoundException.java    │ de NADA!
│   │   │   └── InvalidPaymentException.java     │
│   │   └── service/ (Domain Services)            │
│   │       └── PaymentValidator.java             │
│   │                                              │
│   ├── application/ ─────────────────────────────┤
│   │   ├── port/in/ (Inbound Ports - Use Cases)  │ PORTAS
│   │   │   ├── ApprovePaymentUseCase.java        │ (Interfaces)
│   │   │   ├── CreatePaymentUseCase.java         │
│   │   │   └── GetPaymentUseCase.java            │
│   │   │                                          │
│   │   ├── port/out/ (Outbound Ports)            │
│   │   │   ├── PaymentRepository.java            │
│   │   │   ├── PaymentEventPublisher.java        │
│   │   │   └── NotificationSender.java           │
│   │   │                                          │
│   │   ├── service/ (Use Case Implementations)   │
│   │   │   ├── ApprovePaymentService.java        │
│   │   │   ├── CreatePaymentService.java         │
│   │   │   └── GetPaymentService.java            │
│   │   │                                          │
│   │   └── command/ (DTOs desacoplados)          │
│   │       ├── ApprovePaymentCommand.java        │
│   │       ├── CreatePaymentCommand.java         │
│   │       └── PaymentResponse.java              │
│   │                                              │
│   └── infrastructure/ ──────────────────────────┤
│       ├── adapter/in/web/ (Inbound Adapters)    │ ADAPTADORES
│       │   ├── PaymentController.java            │ (Implementações)
│       │   ├── dto/                               │
│       │   │   ├── PaymentApprovedRequest.java   │
│       │   │   └── PaymentResponse.java          │
│       │   └── mapper/                            │
│       │       └── PaymentWebMapper.java         │
│       │                                          │
│       ├── adapter/out/persistence/ (Outbound)   │
│       │   ├── PaymentJpaRepository.java         │
│       │   ├── PaymentPersistenceAdapter.java    │
│       │   ├── entity/                            │
│       │   │   └── PaymentEntity.java            │
│       │   └── mapper/                            │
│       │       └── PaymentPersistenceMapper.java │
│       │                                          │
│       ├── adapter/out/messaging/ (Outbound)     │
│       │   ├── KafkaPaymentEventPublisher.java   │
│       │   ├── event/                             │
│       │   │   ├── PaymentApprovedEvent.java     │
│       │   │   └── PaymentNotificationEvent.java │
│       │   └── mapper/                            │
│       │       └── PaymentEventMapper.java       │
│       │                                          │
│       └── config/                                │
│           ├── KafkaConfig.java                   │
│           ├── DatabaseConfig.java                │
│           └── BeanConfiguration.java             │
└──────────────────────────────────────────────────┘
```

### 📦 Responsabilidades de Cada Camada:

| Camada | Responsabilidade | Depende de | Exemplos |
|--------|-----------------|------------|----------|
| **Domain** | Regras de negócio puras | Nada! | `Payment.markApproved()`, `PaymentValidator` |
| **Application (Ports)** | Definir contratos (interfaces) | Domain | `ApprovePaymentUseCase`, `PaymentRepository` |
| **Application (Services)** | Orquestrar casos de uso | Domain + Ports | `ApprovePaymentService implements ApprovePaymentUseCase` |
| **Infrastructure (Adapters)** | Implementar portas, integrar frameworks | Application + Frameworks | `KafkaPaymentEventPublisher implements PaymentEventPublisher` |

---

## 4. Passos de Implementação

### 📅 Timeline Estimada: 3-4 dias

---

### **FASE 1: Preparação e Planejamento (2-3 horas)**

#### Passo 1.1: Criar nova estrutura de pastas

```bash
cd ms-producer/src/main/java/com/mvbr/store/

# Criar estrutura de domínio
mkdir -p domain/exception
mkdir -p domain/service

# Criar estrutura de application (portas)
mkdir -p application/port/in
mkdir -p application/port/out
mkdir -p application/service
mkdir -p application/command

# Criar estrutura de infrastructure (adapters)
mkdir -p infrastructure/adapter/in/web/dto
mkdir -p infrastructure/adapter/in/web/mapper
mkdir -p infrastructure/adapter/out/persistence/entity
mkdir -p infrastructure/adapter/out/persistence/mapper
mkdir -p infrastructure/adapter/out/messaging/event
mkdir -p infrastructure/adapter/out/messaging/mapper
mkdir -p infrastructure/config
```

#### Passo 1.2: Documentar decisões arquiteturais

Criar arquivo `docs/ADR-001-hexagonal-architecture.md` (Architecture Decision Record)

---

### **FASE 2: Criar Camada de Domínio (3-4 horas)**

#### Passo 2.1: Mover modelos de domínio

**Ação:** Mover `Payment.java` e `PaymentStatus.java` já estão no lugar certo!

Verificar se contêm apenas lógica de negócio (sem anotações JPA).

#### Passo 2.2: Criar exceções de domínio

**Arquivo:** `domain/exception/PaymentNotFoundException.java`

```java
package com.mvbr.store.domain.exception;

public class PaymentNotFoundException extends RuntimeException {
    private final String paymentId;

    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
```

**Arquivo:** `domain/exception/InvalidPaymentException.java`

```java
package com.mvbr.store.domain.exception;

public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String message) {
        super(message);
    }

    public InvalidPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### Passo 2.3: Criar Domain Service (opcional, se necessário)

**Arquivo:** `domain/service/PaymentValidator.java`

```java
package com.mvbr.store.domain.service;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.domain.exception.InvalidPaymentException;

/**
 * Domain Service for payment validation.
 *
 * Use domain services when business logic:
 * - Doesn't naturally fit in a single entity
 * - Involves multiple entities
 * - Is complex enough to deserve its own class
 */
public class PaymentValidator {

    public void validateForApproval(Payment payment) {
        if (payment == null) {
            throw new InvalidPaymentException("Payment cannot be null");
        }

        if (payment.getAmount().signum() <= 0) {
            throw new InvalidPaymentException(
                "Payment amount must be positive: " + payment.getAmount()
            );
        }

        // Add more business rules here
        // Ex: Check if user has enough credit, anti-fraud rules, etc.
    }

    public void validateForCreation(String userId, String amount, String currency) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidPaymentException("UserId cannot be null or empty");
        }

        if (currency == null || !isValidCurrency(currency)) {
            throw new InvalidPaymentException("Invalid currency: " + currency);
        }

        // More validation...
    }

    private boolean isValidCurrency(String currency) {
        // Simplified - in real world, use java.util.Currency or external service
        return currency != null && (currency.equals("BRL") || currency.equals("USD") || currency.equals("EUR"));
    }
}
```

#### Passo 2.4: Limpar modelos de domínio (remover anotações JPA)

**Ação:** Se `Payment.java` tiver `@Entity`, `@Id`, etc., REMOVER!

Domínio puro não deve conhecer JPA.

**Antes:**
```java
@Entity
public class Payment {
    @Id
    private String paymentId;
    // ...
}
```

**Depois:**
```java
public class Payment {
    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final long createdAt;

    // Constructor, getters, business methods
}
```

---

### **FASE 3: Criar Portas (Application Layer) (4-5 horas)**

#### Passo 3.1: Criar Commands (Input DTOs desacoplados)

**Arquivo:** `application/command/ApprovePaymentCommand.java`

```java
package com.mvbr.store.application.command;

import java.math.BigDecimal;

/**
 * Command to approve a payment.
 *
 * Commands are immutable DTOs used to communicate with use cases.
 * They are independent of HTTP/web layer (not the same as REST DTOs).
 */
public record ApprovePaymentCommand(
    String userId,
    BigDecimal amount,
    String currency
) {
    public ApprovePaymentCommand {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency cannot be null or empty");
        }
    }
}
```

**Arquivo:** `application/command/PaymentResponse.java`

```java
package com.mvbr.store.application.command;

import com.mvbr.store.domain.model.PaymentStatus;
import java.math.BigDecimal;

/**
 * Response DTO returned by use cases.
 */
public record PaymentResponse(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    long createdAt
) {}
```

#### Passo 3.2: Criar Inbound Ports (Use Cases)

**Arquivo:** `application/port/in/ApprovePaymentUseCase.java`

```java
package com.mvbr.store.application.port.in;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;

/**
 * Inbound Port (Driving Port) - Use Case.
 *
 * Defines WHAT the application can do (business capabilities).
 * Controllers/Adapters call this interface.
 */
public interface ApprovePaymentUseCase {

    /**
     * Approves a payment and publishes an event.
     *
     * @param command the payment approval command
     * @return payment response with approval details
     * @throws com.mvbr.store.domain.exception.InvalidPaymentException if payment is invalid
     */
    PaymentResponse execute(ApprovePaymentCommand command);
}
```

**Arquivo:** `application/port/in/GetPaymentUseCase.java`

```java
package com.mvbr.store.application.port.in;

import com.mvbr.store.application.command.PaymentResponse;

public interface GetPaymentUseCase {
    PaymentResponse execute(String paymentId);
}
```

#### Passo 3.3: Criar Outbound Ports (Dependencies)

**Arquivo:** `application/port/out/PaymentRepository.java`

```java
package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.Payment;
import java.util.Optional;

/**
 * Outbound Port (Driven Port) - Repository.
 *
 * Defines HOW the domain persists data (contract).
 * JPA/JDBC/NoSQL adapters implement this interface.
 */
public interface PaymentRepository {

    /**
     * Saves a payment.
     *
     * @param payment the payment to save
     * @return the saved payment (with generated ID if new)
     */
    Payment save(Payment payment);

    /**
     * Finds a payment by ID.
     *
     * @param paymentId the payment ID
     * @return Optional containing the payment if found
     */
    Optional<Payment> findById(String paymentId);

    /**
     * Checks if a payment exists.
     *
     * @param paymentId the payment ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String paymentId);
}
```

**Arquivo:** `application/port/out/PaymentEventPublisher.java`

```java
package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.Payment;

/**
 * Outbound Port (Driven Port) - Event Publisher.
 *
 * Defines HOW the domain publishes events (contract).
 * Kafka/RabbitMQ/SQS adapters implement this interface.
 */
public interface PaymentEventPublisher {

    /**
     * Publishes a payment approved event.
     *
     * @param payment the approved payment
     */
    void publishPaymentApproved(Payment payment);

    /**
     * Publishes a payment notification event.
     *
     * @param payment the payment to notify about
     */
    void publishPaymentNotification(Payment payment);
}
```

---

### **FASE 4: Implementar Services (Use Case Implementations) (4-5 horas)**

#### Passo 4.1: Criar ApprovePaymentService

**Arquivo:** `application/service/ApprovePaymentService.java`

```java
package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.out.PaymentEventPublisher;
import com.mvbr.store.application.port.out.PaymentRepository;
import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.domain.service.PaymentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case Implementation: Approve Payment.
 *
 * Orchestrates business logic using domain objects and ports.
 * Does NOT depend on concrete implementations (Kafka, JPA, etc.)!
 */
@Service
@Transactional
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentValidator paymentValidator;

    public ApprovePaymentService(
            PaymentRepository paymentRepository,
            PaymentEventPublisher eventPublisher,
            PaymentValidator paymentValidator
    ) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.paymentValidator = paymentValidator;
    }

    @Override
    public PaymentResponse execute(ApprovePaymentCommand command) {
        // 1. Validate command (can also use Bean Validation)
        paymentValidator.validateForCreation(
            command.userId(),
            command.amount().toString(),
            command.currency()
        );

        // 2. Create domain object
        Payment payment = new Payment(
            generatePaymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 3. Apply business logic (domain method)
        payment.markApproved();

        // 4. Validate payment for approval (domain service)
        paymentValidator.validateForApproval(payment);

        // 5. Persist (via port - don't know if it's JPA, JDBC, etc.)
        Payment savedPayment = paymentRepository.save(payment);

        // 6. Publish event (via port - don't know if it's Kafka, RabbitMQ, etc.)
        eventPublisher.publishPaymentApproved(savedPayment);

        // 7. Return response
        return toResponse(savedPayment);
    }

    private String generatePaymentId() {
        return "pgto-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }
}
```

#### Passo 4.2: Criar GetPaymentService

**Arquivo:** `application/service/GetPaymentService.java`

```java
package com.mvbr.store.application.service;

import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.GetPaymentUseCase;
import com.mvbr.store.application.port.out.PaymentRepository;
import com.mvbr.store.domain.exception.PaymentNotFoundException;
import com.mvbr.store.domain.model.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetPaymentService implements GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentResponse execute(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }
}
```

---

### **FASE 5: Criar Adapters de Saída (Outbound) (5-6 horas)**

#### Passo 5.1: Criar PaymentEntity (JPA)

**Arquivo:** `infrastructure/adapter/out/persistence/entity/PaymentEntity.java`

```java
package com.mvbr.store.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA Entity for Payment persistence.
 *
 * This is an INFRASTRUCTURE concern, not domain!
 * Domain model (Payment.java) is separate and clean.
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private String paymentId;

    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Long createdAt;

    // JPA requires default constructor
    protected PaymentEntity() {}

    public PaymentEntity(String paymentId, String userId, BigDecimal amount,
                         String currency, String status, Long createdAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
```

#### Passo 5.2: Criar PaymentJpaRepository (Spring Data)

**Arquivo:** `infrastructure/adapter/out/persistence/PaymentJpaRepository.java`

```java
package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository interface.
 * This is infrastructure-specific (Spring Data).
 */
interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    // Spring Data generates implementation automatically
}
```

#### Passo 5.3: Criar Mapper de Persistência

**Arquivo:** `infrastructure/adapter/out/persistence/mapper/PaymentPersistenceMapper.java`

```java
package com.mvbr.store.infrastructure.adapter.out.persistence.mapper;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper between Domain Model (Payment) and Persistence Model (PaymentEntity).
 *
 * This separation allows:
 * - Domain to remain clean (no JPA annotations)
 * - Database schema to evolve independently
 * - Easy migration to other databases (NoSQL, etc.)
 */
@Component
public class PaymentPersistenceMapper {

    public PaymentEntity toEntity(Payment payment) {
        if (payment == null) {
            return null;
        }

        return new PaymentEntity(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );
    }

    public Payment toDomain(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }

        // Reconstruct domain object
        Payment payment = new Payment(
            entity.getPaymentId(),
            entity.getUserId(),
            entity.getAmount(),
            entity.getCurrency()
        );

        // Restore status (using reflection or setter if available)
        // For now, assuming Payment has a way to restore state
        // In real project, you might use a factory method or builder

        return payment;
    }
}
```

**⚠️ IMPORTANTE:** Como `Payment` tem campos `final`, você precisará ajustar:

**Opção 1:** Adicionar construtor completo em `Payment`:

```java
// Em Payment.java (domínio)
public Payment(String paymentId, String userId, BigDecimal amount,
               String currency, PaymentStatus status, long createdAt) {
    this.paymentId = paymentId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.createdAt = createdAt;
}
```

**Opção 2:** Usar um Factory no domínio:

```java
// Em domain/factory/PaymentFactory.java
public class PaymentFactory {
    public static Payment restore(String id, String userId, BigDecimal amount,
                                   String currency, PaymentStatus status, long createdAt) {
        // Use reflection or package-private constructor
    }
}
```

#### Passo 5.4: Criar Adapter de Persistência (Implementa Port)

**Arquivo:** `infrastructure/adapter/out/persistence/PaymentPersistenceAdapter.java`

```java
package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.application.port.out.PaymentRepository;
import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.mvbr.store.infrastructure.adapter.out.persistence.mapper.PaymentPersistenceMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Outbound Adapter: JPA implementation of PaymentRepository port.
 *
 * This adapter:
 * - Implements the domain port (PaymentRepository interface)
 * - Uses Spring Data JPA internally
 * - Maps between domain (Payment) and persistence (PaymentEntity)
 * - Can be easily replaced with JDBC, MongoDB, etc. without affecting domain
 */
@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    public PaymentPersistenceAdapter(
            PaymentJpaRepository jpaRepository,
            PaymentPersistenceMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapper.toEntity(payment);
        PaymentEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findById(paymentId)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(String paymentId) {
        return jpaRepository.existsById(paymentId);
    }
}
```

#### Passo 5.5: Criar Adapter de Mensageria (Kafka)

**Arquivo:** `infrastructure/adapter/out/messaging/event/PaymentApprovedEvent.java`

Já existe! Manter como está.

**Arquivo:** `infrastructure/adapter/out/messaging/mapper/PaymentEventMapper.java`

```java
package com.mvbr.store.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.adapter.out.messaging.event.PaymentApprovedEvent;
import com.mvbr.store.infrastructure.adapter.out.messaging.event.PaymentNotificationEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentEventMapper {

    public PaymentApprovedEvent toPaymentApprovedEvent(Payment payment) {
        return new PaymentApprovedEvent(
            UUID.randomUUID().toString(), // eventId
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus().name(),
            System.currentTimeMillis()
        );
    }

    public PaymentNotificationEvent toPaymentNotificationEvent(Payment payment) {
        return new PaymentNotificationEvent(
            UUID.randomUUID().toString(),
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus().name(),
            System.currentTimeMillis()
        );
    }
}
```

**Arquivo:** `infrastructure/adapter/out/messaging/KafkaPaymentEventPublisher.java`

```java
package com.mvbr.store.infrastructure.adapter.out.messaging;

import com.mvbr.store.application.port.out.PaymentEventPublisher;
import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.adapter.out.messaging.event.PaymentApprovedEvent;
import com.mvbr.store.infrastructure.adapter.out.messaging.event.PaymentNotificationEvent;
import com.mvbr.store.infrastructure.adapter.out.messaging.mapper.PaymentEventMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound Adapter: Kafka implementation of PaymentEventPublisher port.
 *
 * This adapter:
 * - Implements the domain port (PaymentEventPublisher interface)
 * - Uses Kafka internally
 * - Can be replaced with RabbitMQ, SQS, etc. without affecting domain
 */
@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentEventMapper eventMapper;

    @Value("${spring.kafka.topics.payment-approved}")
    private String paymentApprovedTopic;

    @Value("${spring.kafka.topics.payment-notification:payment.notification.v1}")
    private String paymentNotificationTopic;

    public KafkaPaymentEventPublisher(
            @Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            PaymentEventMapper eventMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventMapper = eventMapper;
    }

    @Override
    public void publishPaymentApproved(Payment payment) {
        PaymentApprovedEvent event = eventMapper.toPaymentApprovedEvent(payment);

        kafkaTemplate.send(paymentApprovedTopic, payment.getUserId(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("✅ Payment approved event published: " + event.eventId());
                } else {
                    System.err.println("❌ Failed to publish payment approved event: " + ex.getMessage());
                }
            });
    }

    @Override
    public void publishPaymentNotification(Payment payment) {
        PaymentNotificationEvent event = eventMapper.toPaymentNotificationEvent(payment);

        kafkaTemplate.send(paymentNotificationTopic, payment.getUserId(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("✅ Payment notification event published: " + event.eventId());
                } else {
                    System.err.println("❌ Failed to publish notification event: " + ex.getMessage());
                }
            });
    }
}
```

---

### **FASE 6: Criar Adapters de Entrada (Inbound) (3-4 horas)**

#### Passo 6.1: Criar DTOs Web (separados do domínio)

**Arquivo:** `infrastructure/adapter/in/web/dto/PaymentApprovedRequest.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * HTTP Request DTO for payment approval.
 *
 * This is a WEB concern, not a domain concern!
 * Separated from domain to allow different representations (REST, GraphQL, gRPC, etc.)
 */
public record PaymentApprovedRequest(
    @NotBlank(message = "userId is required")
    String userId,

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    String currency
) {}
```

**Arquivo:** `infrastructure/adapter/in/web/dto/PaymentResponse.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record PaymentResponse(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency,
    String status,
    long createdAt
) {}
```

#### Passo 6.2: Criar Mapper Web

**Arquivo:** `infrastructure/adapter/in/web/mapper/PaymentWebMapper.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web.mapper;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequest;
import org.springframework.stereotype.Component;

/**
 * Mapper between Web DTOs and Application Commands.
 */
@Component
public class PaymentWebMapper {

    public ApprovePaymentCommand toCommand(PaymentApprovedRequest request) {
        return new ApprovePaymentCommand(
            request.userId(),
            request.amount(),
            request.currency()
        );
    }

    public com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse toWebResponse(
            com.mvbr.store.application.command.PaymentResponse response
    ) {
        return new com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse(
            response.paymentId(),
            response.userId(),
            response.amount(),
            response.currency(),
            response.status().name(),
            response.createdAt()
        );
    }
}
```

#### Passo 6.3: Refatorar Controller (Inbound Adapter)

**Arquivo:** `infrastructure/adapter/in/web/PaymentController.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.in.GetPaymentUseCase;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequest;
import com.mvbr.store.infrastructure.adapter.in.web.mapper.PaymentWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound Adapter: REST Controller.
 *
 * Responsibilities:
 * - Receive HTTP requests
 * - Validate input (Bean Validation)
 * - Map web DTOs to commands
 * - Call use case (port)
 * - Map response back to web DTO
 * - Return HTTP response
 *
 * Does NOT contain business logic!
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final PaymentWebMapper mapper;

    public PaymentController(
            ApprovePaymentUseCase approvePaymentUseCase,
            GetPaymentUseCase getPaymentUseCase,
            PaymentWebMapper mapper
    ) {
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/approved")
    public ResponseEntity<com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse> approvePayment(
            @Valid @RequestBody PaymentApprovedRequest request
    ) {
        // 1. Map web DTO to command
        ApprovePaymentCommand command = mapper.toCommand(request);

        // 2. Execute use case (business logic in application layer)
        PaymentResponse response = approvePaymentUseCase.execute(command);

        // 3. Map response to web DTO
        com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse webResponse =
            mapper.toWebResponse(response);

        // 4. Return HTTP response
        return ResponseEntity.status(HttpStatus.CREATED).body(webResponse);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse> getPayment(
            @PathVariable String paymentId
    ) {
        PaymentResponse response = getPaymentUseCase.execute(paymentId);
        com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponse webResponse =
            mapper.toWebResponse(response);

        return ResponseEntity.ok(webResponse);
    }
}
```

#### Passo 6.4: Criar Exception Handler

**Arquivo:** `infrastructure/adapter/in/web/GlobalExceptionHandler.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web;

import com.mvbr.store.domain.exception.InvalidPaymentException;
import com.mvbr.store.domain.exception.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of(
                "error", "Payment not found",
                "message", ex.getMessage(),
                "paymentId", ex.getPaymentId(),
                "timestamp", LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPayment(InvalidPaymentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of(
                "error", "Invalid payment",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            Map.of(
                "error", "Internal server error",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
            )
        );
    }
}
```

---

### **FASE 7: Configuração (1-2 horas)**

#### Passo 7.1: Criar Bean Configuration

**Arquivo:** `infrastructure/config/BeanConfiguration.java`

```java
package com.mvbr.store.infrastructure.config;

import com.mvbr.store.domain.service.PaymentValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean configuration for domain services and other non-Spring beans.
 */
@Configuration
public class BeanConfiguration {

    @Bean
    public PaymentValidator paymentValidator() {
        return new PaymentValidator();
    }
}
```

#### Passo 7.2: Atualizar application.yaml

Adicionar topic de notificação se não existir:

```yaml
spring:
  kafka:
    topics:
      payment-approved: ${KAFKA_TOPIC_PAYMENT_APPROVED:payment.approved.v1}
      payment-notification: ${KAFKA_TOPIC_PAYMENT_NOTIFICATION:payment.notification.v1}
```

#### Passo 7.3: Criar Flyway Migration (se necessário)

Se precisar ajustar schema de banco:

**Arquivo:** `src/main/resources/db/migration/V2__adjust_payment_table_for_hexagonal.sql`

```sql
-- Migration para ajustes se necessário
-- Por exemplo, adicionar campos, índices, etc.

-- Criar índice por userId se não existir
CREATE INDEX IF NOT EXISTS idx_payment_user_id ON payment(user_id);

-- Adicionar comentários nas colunas
COMMENT ON COLUMN payment.payment_id IS 'Unique payment identifier';
COMMENT ON COLUMN payment.user_id IS 'User who made the payment';
COMMENT ON COLUMN payment.status IS 'Payment status: PENDING, APPROVED, CANCELED';
```

---

### **FASE 8: Remover Código Antigo (1-2 horas)**

#### Passo 8.1: Deletar arquivos antigos

```bash
# Deletar controllers antigos
rm -rf src/main/java/com/mvbr/store/application/controller/

# Deletar DTOs antigos
rm -rf src/main/java/com/mvbr/store/application/dto/

# Deletar mappers antigos
rm -rf src/main/java/com/mvbr/store/application/mapper/

# Deletar service antigo
rm -rf src/main/java/com/mvbr/store/application/service/PaymentService.java

# Deletar producers antigos
rm -rf src/main/java/com/mvbr/store/infrastructure/messaging/producer/

# Deletar repository antigo
rm -rf src/main/java/com/mvbr/store/infrastructure/persistence/PaymentRepository.java
```

#### Passo 8.2: Mover configuração Kafka

Mover `KafkaProducerConfig.java` para `infrastructure/config/KafkaConfig.java`

---

## 5. Código de Exemplo

### Exemplo Completo de Fluxo:

```
1. Cliente HTTP POST /api/payments/approved
   ↓
2. PaymentController (Inbound Adapter)
   - Valida PaymentApprovedRequest
   - Mapeia para ApprovePaymentCommand
   ↓
3. ApprovePaymentUseCase (Port - Interface)
   ↓
4. ApprovePaymentService (Use Case Implementation)
   - Valida command (PaymentValidator)
   - Cria Payment (domain model)
   - Chama payment.markApproved() (business logic)
   - Chama paymentRepository.save() (port)
   - Chama eventPublisher.publishPaymentApproved() (port)
   ↓
5. PaymentPersistenceAdapter (Outbound Adapter)
   - Mapeia Payment → PaymentEntity
   - Salva no banco via JPA
   - Retorna Payment salvo
   ↓
6. KafkaPaymentEventPublisher (Outbound Adapter)
   - Mapeia Payment → PaymentApprovedEvent
   - Publica no Kafka
   ↓
7. Retorna PaymentResponse para Controller
   ↓
8. Controller retorna HTTP 201 com PaymentResponse
```

### Diagrama de Dependências:

```
PaymentController (infra/web)
    ↓ depends on
ApprovePaymentUseCase (interface)
    ↑ implemented by
ApprovePaymentService (application)
    ↓ depends on (interfaces)
    ├─ PaymentRepository (port)
    │    ↑ implemented by
    │    PaymentPersistenceAdapter (infra/persistence)
    │
    └─ PaymentEventPublisher (port)
         ↑ implemented by
         KafkaPaymentEventPublisher (infra/messaging)
```

---

## 6. Testes

### 6.1: Teste Unitário de Domain Model

**Arquivo:** `src/test/java/com/mvbr/store/domain/model/PaymentTest.java`

```java
package com.mvbr.store.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void shouldCreatePaymentWithPendingStatus() {
        // When
        Payment payment = new Payment(
            "pgto-123",
            "user-456",
            new BigDecimal("100.00"),
            "BRL"
        );

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void shouldApprovePayment() {
        // Given
        Payment payment = new Payment("pgto-123", "user-456", new BigDecimal("100.00"), "BRL");

        // When
        payment.markApproved();

        // Then
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    @Test
    void shouldNotApproveCanceledPayment() {
        // Given
        Payment payment = new Payment("pgto-123", "user-456", new BigDecimal("100.00"), "BRL");
        payment.cancel();

        // When & Then
        assertThrows(IllegalStateException.class, payment::markApproved);
    }
}
```

### 6.2: Teste Unitário de Use Case (com Mocks)

**Arquivo:** `src/test/java/com/mvbr/store/application/service/ApprovePaymentServiceTest.java`

```java
package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.out.PaymentEventPublisher;
import com.mvbr.store.application.port.out.PaymentRepository;
import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.domain.service.PaymentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovePaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentValidator paymentValidator;
    private ApprovePaymentService service;

    @BeforeEach
    void setUp() {
        paymentValidator = new PaymentValidator();
        service = new ApprovePaymentService(paymentRepository, eventPublisher, paymentValidator);
    }

    @Test
    void shouldApprovePaymentSuccessfully() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "user-123",
            new BigDecimal("100.00"),
            "BRL"
        );

        Payment savedPayment = new Payment("pgto-abc", "user-123", new BigDecimal("100.00"), "BRL");
        savedPayment.markApproved();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // When
        PaymentResponse response = service.execute(command);

        // Then
        assertNotNull(response);
        assertEquals("user-123", response.userId());
        assertEquals(PaymentStatus.APPROVED, response.status());

        // Verify interactions
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(eventPublisher, times(1)).publishPaymentApproved(any(Payment.class));
    }

    @Test
    void shouldThrowExceptionForInvalidAmount() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "user-123",
            new BigDecimal("-10.00"),
            "BRL"
        );

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> service.execute(command));

        // Verify no interactions
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentApproved(any());
    }

    @Test
    void shouldCaptureAndVerifyPublishedEvent() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "user-456",
            new BigDecimal("250.00"),
            "USD"
        );

        Payment savedPayment = new Payment("pgto-xyz", "user-456", new BigDecimal("250.00"), "USD");
        savedPayment.markApproved();

        when(paymentRepository.save(any())).thenReturn(savedPayment);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

        // When
        service.execute(command);

        // Then
        verify(eventPublisher).publishPaymentApproved(paymentCaptor.capture());
        Payment capturedPayment = paymentCaptor.getValue();

        assertEquals("user-456", capturedPayment.getUserId());
        assertEquals(new BigDecimal("250.00"), capturedPayment.getAmount());
        assertEquals(PaymentStatus.APPROVED, capturedPayment.getStatus());
    }
}
```

### 6.3: Teste de Integração (Controller → Use Case → Adapters)

**Arquivo:** `src/test/java/com/mvbr/store/infrastructure/adapter/in/web/PaymentControllerIntegrationTest.java`

```java
package com.mvbr.store.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldApprovePaymentViaHttpRequest() throws Exception {
        // Given
        PaymentApprovedRequest request = new PaymentApprovedRequest(
            "user-integration-test",
            new BigDecimal("99.99"),
            "BRL"
        );

        // When & Then
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentId").exists())
            .andExpect(jsonPath("$.userId").value("user-integration-test"))
            .andExpect(jsonPath("$.amount").value(99.99))
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        // Given (invalid - negative amount)
        PaymentApprovedRequest request = new PaymentApprovedRequest(
            "user-test",
            new BigDecimal("-50.00"),
            "BRL"
        );

        // When & Then
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
```

### 6.4: Teste de Adapter (Persistence)

**Arquivo:** `src/test/java/com/mvbr/store/infrastructure/adapter/out/persistence/PaymentPersistenceAdapterTest.java`

```java
package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentPersistenceAdapterTest {

    @Autowired
    private PaymentPersistenceAdapter adapter;

    @Test
    void shouldSaveAndRetrievePayment() {
        // Given
        Payment payment = new Payment(
            "pgto-adapter-test",
            "user-adapter",
            new BigDecimal("150.00"),
            "EUR"
        );
        payment.markApproved();

        // When
        Payment savedPayment = adapter.save(payment);
        Optional<Payment> retrieved = adapter.findById(savedPayment.getPaymentId());

        // Then
        assertTrue(retrieved.isPresent());
        assertEquals("user-adapter", retrieved.get().getUserId());
        assertEquals(PaymentStatus.APPROVED, retrieved.get().getStatus());
    }

    @Test
    void shouldReturnEmptyWhenPaymentNotFound() {
        // When
        Optional<Payment> result = adapter.findById("non-existent-id");

        // Then
        assertFalse(result.isPresent());
    }
}
```

---

## 7. Checklist de Validação

### ✅ Estrutura:

- [ ] Pastas domain/application/infrastructure criadas
- [ ] Domain não tem dependências externas (sem `@Entity`, `@Autowired`)
- [ ] Portas (interfaces) separadas em `port/in` e `port/out`
- [ ] Adapters implementam portas
- [ ] DTOs web separados de commands/domain

### ✅ Código:

- [ ] PaymentValidator (domain service) criado
- [ ] ApprovePaymentUseCase (port in) criado
- [ ] PaymentRepository (port out) criado
- [ ] PaymentEventPublisher (port out) criado
- [ ] ApprovePaymentService implementa ApprovePaymentUseCase
- [ ] PaymentPersistenceAdapter implementa PaymentRepository
- [ ] KafkaPaymentEventPublisher implementa PaymentEventPublisher
- [ ] PaymentController usa use cases (não services diretamente)
- [ ] Código antigo removido

### ✅ Testes:

- [ ] Testes unitários de domínio (Payment, PaymentValidator)
- [ ] Testes unitários de use cases (com mocks)
- [ ] Testes de integração (controller → use case → adapters)
- [ ] Testes de adapters (persistence, messaging)
- [ ] Cobertura de testes > 80%

### ✅ Funcionalidade:

- [ ] POST /api/payments/approved funciona
- [ ] GET /api/payments/{id} funciona
- [ ] Evento Kafka é publicado
- [ ] Payment é salvo no banco
- [ ] Exception handling funciona (404, 400, 500)

### ✅ Documentação:

- [ ] CLAUDE.md atualizado com nova arquitetura
- [ ] README com diagrama hexagonal
- [ ] ADR (Architecture Decision Record) criado
- [ ] Comentários em código explicando conceitos

---

## 8. Próximos Passos (Após Hexagonal)

Depois de implementar Hexagonal, você estará pronto para:

1. **Outbox Pattern** (consistência DB + Kafka)
2. **CQRS** (separar comandos de queries)
3. **Event Sourcing** (armazenar eventos ao invés de estado)
4. **Feature Toggles** (ativar/desativar funcionalidades)
5. **Multi-tenant** (vários clientes no mesmo sistema)

---

## 9. Recursos Adicionais

### 📚 Leitura Recomendada:

1. **Clean Architecture** - Robert C. Martin
2. **Domain-Driven Design** - Eric Evans
3. **Implementing Domain-Driven Design** - Vaughn Vernon
4. **Get Your Hands Dirty on Clean Architecture** - Tom Hombergs

### 🎥 Vídeos:

1. [Hexagonal Architecture - Netflix Tech Blog](https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749)
2. [Spring Boot + Hexagonal Architecture (Baeldung)](https://www.baeldung.com/hexagonal-architecture-ddd-spring)

### 🔗 Exemplos:

1. [GitHub - Hexagonal Architecture Example](https://github.com/thombergs/buckpal)
2. [GitHub - DDD + Hexagonal](https://github.com/ddd-by-examples/library)

---

## 10. Conclusão

Arquitetura Hexagonal traz:

✅ **Código limpo e desacoplado**
✅ **Fácil de testar** (mocks nas portas)
✅ **Fácil de evoluir** (trocar adapters sem afetar domínio)
✅ **Regras de negócio isoladas** (domain puro)
✅ **Base sólida para Outbox Pattern** e outros padrões

**Tempo estimado:** 3-4 dias de trabalho focado.

**Resultado:** Código profissional, testável e pronto para produção!

---

Boa sorte na implementação! 🚀

Se tiver dúvidas durante a execução, consulte este plano ou peça ajuda com exemplos de código específicos.
