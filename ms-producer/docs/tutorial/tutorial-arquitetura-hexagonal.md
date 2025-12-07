# Tutorial Prático: Arquitetura Hexagonal em Produção

## 📋 Sumário

1. [O que é e Para Que Serve](#1-o-que-é-e-para-que-serve)
2. [Estrutura Completa do Projeto](#2-estrutura-completa-do-projeto)
3. [Camadas da Arquitetura](#3-camadas-da-arquitetura)
4. [Implementação Passo a Passo](#4-implementação-passo-a-passo)
5. [Padrões de Código](#5-padrões-de-código)
6. [Testes na Prática](#6-testes-na-prática)
7. [Casos de Uso Reais](#7-casos-de-uso-reais)
8. [Checklist de Implementação](#8-checklist-de-implementação)

---

## 1. O que É e Para Que Serve

### Definição Prática

Arquitetura Hexagonal (Ports & Adapters) é um padrão onde:

```
┌─────────────────────────────────────────────┐
│                                             │
│  DOMAIN (Regras de Negócio)                │
│  ↓ depende de ↓                             │
│  PORTS (Interfaces)                         │
│  ↑ implementado por ↑                       │
│  ADAPTERS (JPA, Kafka, REST)                │
│                                             │
└─────────────────────────────────────────────┘
```

**Em português claro:**
- **Domain**: Sua lógica de negócio PURA (sem frameworks)
- **Ports**: Contratos (interfaces) que o domain precisa
- **Adapters**: Implementações técnicas (JPA, Kafka, REST, etc)

### Por Que Usar em Produção?

| Problema Comum | Solução Hexagonal |
|----------------|-------------------|
| Trocar banco (Oracle → Postgres) quebra tudo | Troca apenas o adapter (5 min) |
| Testes lentos (precisa subir banco/Kafka) | Testa domain puro (milissegundos) |
| Migrar REST → gRPC reescreve tudo | Adiciona adapter gRPC mantendo domain |
| Lógica de negócio espalhada | Tudo no domain (fácil de encontrar) |

### Diagrama Visual Completo

```
┌─────────────────────────────────────────────────────────────┐
│                    CAMADA DE APRESENTAÇÃO                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ REST API     │  │ GraphQL      │  │ gRPC         │      │
│  │ Controller   │  │ Resolver     │  │ Service      │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └──────────────────┴──────────────────┘              │
│                            │                                 │
└────────────────────────────┼─────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                   CAMADA DE APLICAÇÃO                       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         INBOUND PORTS (Use Cases)                    │  │
│  │  ┌──────────────────┐  ┌──────────────────┐         │  │
│  │  │ ApprovePayment   │  │ CancelPayment    │         │  │
│  │  │ UseCase          │  │ UseCase          │         │  │
│  │  └──────────────────┘  └──────────────────┘         │  │
│  └──────────────────────────────────────────────────────┘  │
│                            │                                │
│                            ▼                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         USE CASE SERVICES                            │  │
│  │  ┌──────────────────┐  ┌──────────────────┐         │  │
│  │  │ ApprovePayment   │  │ CancelPayment    │         │  │
│  │  │ Service          │  │ Service          │         │  │
│  │  └────────┬─────────┘  └────────┬─────────┘         │  │
│  └───────────┼──────────────────────┼───────────────────┘  │
│              │                      │                       │
│              ▼                      ▼                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         OUTBOUND PORTS (Dependencies)                │  │
│  │  ┌─────────────────┐  ┌─────────────────┐           │  │
│  │  │ PaymentRepo     │  │ EventPublisher  │           │  │
│  │  │ Port            │  │ Port            │           │  │
│  │  └─────────────────┘  └─────────────────┘           │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      CAMADA DE DOMÍNIO                      │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              DOMAIN MODELS (PURO!)                   │  │
│  │  ┌──────────────────┐  ┌──────────────────┐         │  │
│  │  │ PaymentDomain    │  │ PaymentStatus    │         │  │
│  │  │ - paymentId      │  │ - PENDING        │         │  │
│  │  │ - amount         │  │ - APPROVED       │         │  │
│  │  │ - status         │  │ - CANCELED       │         │  │
│  │  │                  │  └──────────────────┘         │  │
│  │  │ + approve()      │                               │  │
│  │  │ + cancel()       │                               │  │
│  │  └──────────────────┘                               │  │
│  │                                                      │  │
│  │  ┌──────────────────────────────────────────────┐   │  │
│  │  │        DOMAIN EXCEPTIONS                     │   │  │
│  │  │  - InvalidPaymentException                   │   │  │
│  │  │  - PaymentNotFoundException                  │   │  │
│  │  └──────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             ▲
                             │ (implementa ports)
┌─────────────────────────────────────────────────────────────┐
│                  CAMADA DE INFRAESTRUTURA                   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              OUTBOUND ADAPTERS                       │  │
│  │  ┌──────────────────┐  ┌──────────────────┐         │  │
│  │  │ JPA Adapter      │  │ Kafka Adapter    │         │  │
│  │  │ ┌──────────────┐ │  │ ┌──────────────┐ │         │  │
│  │  │ │PaymentEntity │ │  │ │PaymentEvent  │ │         │  │
│  │  │ │(@Entity)     │ │  │ │              │ │         │  │
│  │  │ └──────────────┘ │  │ └──────────────┘ │         │  │
│  │  │ ┌──────────────┐ │  │ ┌──────────────┐ │         │  │
│  │  │ │JpaRepository │ │  │ │KafkaTemplate │ │         │  │
│  │  │ └──────────────┘ │  │ └──────────────┘ │         │  │
│  │  │ ┌──────────────┐ │  │ ┌──────────────┐ │         │  │
│  │  │ │Mapper        │ │  │ │Mapper        │ │         │  │
│  │  │ └──────────────┘ │  │ └──────────────┘ │         │  │
│  │  └──────────────────┘  └──────────────────┘         │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              CONFIGURATIONS                          │  │
│  │  - KafkaConfig                                       │  │
│  │  - JpaConfig                                         │  │
│  │  - BeanConfig                                        │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Estrutura Completa do Projeto

### Organização de Pastas (Spring Boot)

```
src/main/java/com/empresa/projeto/
│
├── domain/                                    # CAMADA 1: DOMÍNIO
│   ├── model/                                 # Entidades de negócio
│   │   ├── PaymentDomain.java                # Modelo PURO (sem @Entity)
│   │   ├── PaymentStatus.java                # Enums
│   │   └── OrderDomain.java
│   │
│   ├── exception/                             # Exceções de negócio
│   │   ├── InvalidPaymentException.java
│   │   ├── PaymentNotFoundException.java
│   │   └── InsufficientBalanceException.java
│   │
│   └── service/                               # Serviços de domínio (opcional)
│       └── PaymentCalculationService.java    # Lógicas complexas de cálculo
│
├── application/                               # CAMADA 2: APLICAÇÃO
│   ├── port/
│   │   ├── in/                                # PORTAS DE ENTRADA (Use Cases)
│   │   │   ├── ApprovePaymentUseCase.java    # Interface do caso de uso
│   │   │   ├── CancelPaymentUseCase.java
│   │   │   ├── FindPaymentUseCase.java
│   │   │   └── ProcessRefundUseCase.java
│   │   │
│   │   └── out/                               # PORTAS DE SAÍDA (Dependências)
│   │       ├── PaymentRepositoryPort.java    # Interface para persistência
│   │       ├── PaymentEventPublisherPort.java # Interface para eventos
│   │       ├── NotificationPort.java         # Interface para notificações
│   │       └── AuditPort.java                # Interface para auditoria
│   │
│   ├── service/                               # IMPLEMENTAÇÃO DOS USE CASES
│   │   ├── ApprovePaymentService.java        # Implementa ApprovePaymentUseCase
│   │   ├── CancelPaymentService.java
│   │   ├── FindPaymentService.java
│   │   └── ProcessRefundService.java
│   │
│   └── command/                               # Commands e Responses
│       ├── ApprovePaymentCommand.java        # Input do use case
│       ├── CancelPaymentCommand.java
│       ├── PaymentResponse.java              # Output do use case
│       └── PaymentListResponse.java
│
└── infrastructure/                            # CAMADA 3: INFRAESTRUTURA
    ├── adapter/
    │   ├── in/                                # ADAPTERS DE ENTRADA
    │   │   └── web/                           # REST API
    │   │       ├── controller/
    │   │       │   ├── PaymentController.java
    │   │       │   └── HealthController.java
    │   │       ├── dto/                       # DTOs HTTP
    │   │       │   ├── PaymentRequestDto.java
    │   │       │   ├── PaymentResponseDto.java
    │   │       │   └── ErrorResponseDto.java
    │   │       ├── mapper/                    # Mappers HTTP
    │   │       │   └── PaymentWebMapper.java
    │   │       └── exception/                 # Exception handlers
    │   │           └── GlobalExceptionHandler.java
    │   │
    │   └── out/                               # ADAPTERS DE SAÍDA
    │       ├── persistence/                   # Persistência (JPA)
    │       │   ├── entity/
    │       │   │   ├── PaymentEntity.java    # @Entity (JPA)
    │       │   │   └── AuditEntity.java
    │       │   ├── repository/
    │       │   │   ├── PaymentJpaRepository.java # Spring Data
    │       │   │   └── AuditJpaRepository.java
    │       │   ├── mapper/
    │       │   │   └── PaymentPersistenceMapper.java
    │       │   └── PaymentPersistenceAdapter.java # Implementa Port
    │       │
    │       ├── messaging/                     # Mensageria (Kafka)
    │       │   ├── event/
    │       │   │   ├── PaymentApprovedEvent.java
    │       │   │   └── PaymentCanceledEvent.java
    │       │   ├── producer/
    │       │   │   └── PaymentEventProducer.java
    │       │   ├── mapper/
    │       │   │   └── PaymentEventMapper.java
    │       │   └── KafkaEventPublisherAdapter.java # Implementa Port
    │       │
    │       ├── notification/                  # Notificações externas
    │       │   ├── client/
    │       │   │   └── EmailClient.java
    │       │   └── EmailNotificationAdapter.java # Implementa Port
    │       │
    │       └── audit/                         # Auditoria
    │           └── AuditAdapter.java          # Implementa Port
    │
    └── config/                                # Configurações
        ├── JpaConfig.java
        ├── KafkaConfig.java
        ├── BeanConfig.java
        └── SecurityConfig.java
```

### Arquivos de Recursos

```
src/main/resources/
│
├── application.yaml              # Configurações principais
├── application-dev.yaml          # Perfil desenvolvimento
├── application-prod.yaml         # Perfil produção
│
└── db/
    └── migration/                # Flyway migrations
        ├── V1__create_payment_table.sql
        ├── V2__add_audit_table.sql
        └── V3__add_indexes.sql
```

---

## 3. Camadas da Arquitetura

### 3.1 Domain Layer - O Coração do Sistema

#### O Que É?

A camada de domínio contém **TODA** a lógica de negócio. É o código mais importante e mais protegido do sistema.

#### Regras de Ouro

```java
// ✅ PERMITIDO no Domain
- Regras de negócio
- Validações de dados
- Cálculos de negócio
- Transições de estado
- Domain Events
- Value Objects

// ❌ PROIBIDO no Domain
- @Entity, @Document, @Table (JPA/Mongo)
- @RestController, @RequestMapping (Spring Web)
- @KafkaListener (Kafka)
- Qualquer import de javax.*, jakarta.*, org.springframework.*
- SQL, HTTP, JSON
```

#### Exemplo Completo de Domain Model

```java
package com.empresa.projeto.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * PaymentDomain - Modelo de Domínio PURO
 *
 * CARACTERÍSTICAS:
 * - SEM annotations de frameworks (@Entity, @Table, etc)
 * - Imutável (campos final sempre que possível)
 * - Self-validating (validação no construtor)
 * - Rich behavior (métodos de negócio)
 */
public class PaymentDomain {

    // ========== ATRIBUTOS (IMUTÁVEIS quando possível) ==========

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;              // Mutável (muda com approve/cancel)
    private final Instant createdAt;
    private Instant updatedAt;

    // ========== CONSTRUTORES ==========

    /**
     * Construtor para CRIAR um novo pagamento.
     * Usa este construtor quando RECEBE dados do usuário.
     */
    public PaymentDomain(String paymentId, String userId,
                         BigDecimal amount, String currency) {
        // Validações AQUI - Fail Fast!
        this.paymentId = requireNonBlank(paymentId, "Payment ID is required");
        this.userId = requireNonBlank(userId, "User ID is required");
        this.amount = requirePositive(amount, "Amount must be positive");
        this.currency = requireNonBlank(currency, "Currency is required").toUpperCase();

        // Estado inicial
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Construtor para RESTAURAR do banco de dados.
     * Usa este construtor quando o adapter JPA carrega dados persistidos.
     */
    public PaymentDomain(String paymentId, String userId,
                         BigDecimal amount, String currency,
                         PaymentStatus status, Instant createdAt,
                         Instant updatedAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== LÓGICA DE NEGÓCIO (MÉTODOS PÚBLICOS) ==========

    /**
     * Aprova o pagamento.
     *
     * REGRAS DE NEGÓCIO:
     * - Só pode aprovar se status for PENDING
     * - Pagamento cancelado NÃO pode ser aprovado
     * - Atualiza timestamp
     */
    public void approve() {
        if (status == PaymentStatus.CANCELED) {
            throw new IllegalStateException(
                "Cannot approve canceled payment: " + paymentId
            );
        }

        if (status == PaymentStatus.APPROVED) {
            return; // Já está aprovado (idempotência)
        }

        this.status = PaymentStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Cancela o pagamento.
     *
     * REGRAS DE NEGÓCIO:
     * - Só pode cancelar se status for PENDING
     * - Pagamento aprovado NÃO pode ser cancelado (precisa refund)
     */
    public void cancel() {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot cancel approved payment: " + paymentId +
                ". Use refund instead."
            );
        }

        if (status == PaymentStatus.CANCELED) {
            return; // Já está cancelado (idempotência)
        }

        this.status = PaymentStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    /**
     * Processa reembolso (estorno).
     *
     * REGRAS DE NEGÓCIO:
     * - Só pode estornar pagamento APROVADO
     */
    public void refund() {
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Can only refund approved payments. Current status: " + status
            );
        }

        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    // ========== MÉTODOS DE CONSULTA ==========

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public boolean isCanceled() {
        return status == PaymentStatus.CANCELED;
    }

    public boolean canBeModified() {
        return status == PaymentStatus.PENDING;
    }

    // ========== VALIDAÇÕES PRIVADAS ==========

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentException(message);
        }
        return value;
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException(message);
        }
        return value;
    }

    // ========== GETTERS (SOMENTE LEITURA) ==========

    public String getPaymentId() { return paymentId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ========== EQUALS & HASHCODE (baseado em paymentId) ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentDomain)) return false;
        PaymentDomain that = (PaymentDomain) o;
        return Objects.equals(paymentId, that.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId);
    }

    // ========== TO STRING (para debug) ==========

    @Override
    public String toString() {
        return "PaymentDomain{" +
                "paymentId='" + paymentId + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
```

#### Enum de Status

```java
package com.empresa.projeto.domain.model;

/**
 * Status possíveis de um pagamento.
 *
 * FLUXO:
 * PENDING → APPROVED → (opcional) REFUNDED
 *    ↓
 * CANCELED
 */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    CANCELED,
    REFUNDED
}
```

#### Exceções de Domínio

```java
package com.empresa.projeto.domain.exception;

/**
 * Exceção lançada quando dados de pagamento são inválidos.
 */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }

    public InvalidPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
package com.empresa.projeto.domain.exception;

/**
 * Exceção lançada quando pagamento não é encontrado.
 */
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

### 3.2 Application Layer - Orquestração

#### O Que É?

A camada de aplicação **orquestra** o fluxo de dados entre a apresentação e o domínio. Ela NÃO contém lógica de negócio, apenas coordena.

#### Inbound Ports (Use Cases)

```java
package com.empresa.projeto.application.port.in;

import com.empresa.projeto.application.command.ApprovePaymentCommand;
import com.empresa.projeto.application.command.PaymentResponse;

/**
 * INBOUND PORT - Caso de Uso: Aprovar Pagamento
 *
 * Define O QUE a aplicação faz, não COMO faz.
 * Esta é a "porta de entrada" para este caso de uso.
 */
public interface ApprovePaymentUseCase {

    /**
     * Aprova um pagamento.
     *
     * @param command dados do pagamento a aprovar
     * @return resposta com dados do pagamento aprovado
     * @throws InvalidPaymentException se dados inválidos
     * @throws PaymentNotFoundException se pagamento não existe
     */
    PaymentResponse approve(ApprovePaymentCommand command);
}
```

#### Outbound Ports (Dependências)

```java
package com.empresa.projeto.application.port.out;

import com.empresa.projeto.domain.model.PaymentDomain;
import java.util.Optional;

/**
 * OUTBOUND PORT - Repositório de Pagamentos
 *
 * Define O QUE a aplicação PRECISA da infraestrutura.
 * A implementação será um ADAPTER na camada de infraestrutura.
 */
public interface PaymentRepositoryPort {

    /**
     * Salva um pagamento.
     */
    PaymentDomain save(PaymentDomain payment);

    /**
     * Busca pagamento por ID.
     */
    Optional<PaymentDomain> findById(String paymentId);

    /**
     * Verifica se pagamento existe.
     */
    boolean existsById(String paymentId);

    /**
     * Lista pagamentos de um usuário.
     */
    List<PaymentDomain> findByUserId(String userId);
}
```

```java
package com.empresa.projeto.application.port.out;

import com.empresa.projeto.domain.model.PaymentDomain;

/**
 * OUTBOUND PORT - Publicador de Eventos
 */
public interface PaymentEventPublisherPort {

    /**
     * Publica evento de pagamento aprovado.
     */
    void publishPaymentApproved(PaymentDomain payment);

    /**
     * Publica evento de pagamento cancelado.
     */
    void publishPaymentCanceled(PaymentDomain payment);

    /**
     * Publica evento de reembolso.
     */
    void publishPaymentRefunded(PaymentDomain payment);
}
```

#### Commands e Responses

```java
package com.empresa.projeto.application.command;

import java.math.BigDecimal;

/**
 * Command - Aprovar Pagamento
 *
 * Representa a INTENÇÃO do usuário.
 * Imutável (record).
 */
public record ApprovePaymentCommand(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency
) {}
```

```java
package com.empresa.projeto.application.command;

import com.empresa.projeto.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response - Resposta de Pagamento
 *
 * Representa o RESULTADO do caso de uso.
 * Imutável (record).
 */
public record PaymentResponse(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
```

#### Use Case Service (Implementação)

```java
package com.empresa.projeto.application.service;

import com.empresa.projeto.application.command.ApprovePaymentCommand;
import com.empresa.projeto.application.command.PaymentResponse;
import com.empresa.projeto.application.port.in.ApprovePaymentUseCase;
import com.empresa.projeto.application.port.out.PaymentEventPublisherPort;
import com.empresa.projeto.application.port.out.PaymentRepositoryPort;
import com.empresa.projeto.domain.model.PaymentDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service que implementa o Use Case de Aprovar Pagamento.
 *
 * RESPONSABILIDADES:
 * 1. Receber Command
 * 2. Criar/buscar Domain Object
 * 3. Chamar método de negócio do Domain
 * 4. Persistir via Port
 * 5. Publicar evento via Port
 * 6. Retornar Response
 *
 * NÃO TEM LÓGICA DE NEGÓCIO! Apenas orquestra.
 */
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentEventPublisherPort eventPublisher;

    public ApprovePaymentService(
            PaymentRepositoryPort paymentRepository,
            PaymentEventPublisherPort eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PaymentResponse approve(ApprovePaymentCommand command) {

        // 1. Criar objeto de domínio
        //    (validação acontece no construtor do PaymentDomain)
        PaymentDomain payment = new PaymentDomain(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 2. Executar lógica de negócio
        //    (lógica está NO DOMAIN, não aqui!)
        payment.approve();

        // 3. Persistir
        //    (usa PORTA, não sabe se é JPA, MongoDB, etc)
        PaymentDomain savedPayment = paymentRepository.save(payment);

        // 4. Publicar evento
        //    (usa PORTA, não sabe se é Kafka, RabbitMQ, etc)
        eventPublisher.publishPaymentApproved(savedPayment);

        // 5. Retornar resposta
        return new PaymentResponse(
            savedPayment.getPaymentId(),
            savedPayment.getUserId(),
            savedPayment.getAmount(),
            savedPayment.getCurrency(),
            savedPayment.getStatus(),
            savedPayment.getCreatedAt(),
            savedPayment.getUpdatedAt()
        );
    }
}
```

### 3.3 Infrastructure Layer - Implementações Técnicas

#### Adapter de Persistência (JPA)

**Entidade JPA:**

```java
package com.empresa.projeto.infrastructure.adapter.out.persistence.entity;

import com.empresa.projeto.domain.model.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentEntity - Entidade JPA
 *
 * CONTÉM:
 * - Annotations JPA (@Entity, @Id, etc)
 * - Mapeamento de tabela
 * - Getters/Setters
 *
 * NÃO CONTÉM:
 * - Lógica de negócio
 * - Validações de negócio
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "payment_id", length = 36, nullable = false)
    private String paymentId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Construtor padrão (JPA exige)
    protected PaymentEntity() {}

    // Construtor com todos os campos
    public PaymentEntity(String paymentId, String userId, BigDecimal amount,
                         String currency, PaymentStatus status,
                         Instant createdAt, Instant updatedAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters e Setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

**Mapper (Domain ↔ Entity):**

```java
package com.empresa.projeto.infrastructure.adapter.out.persistence.mapper;

import com.empresa.projeto.domain.model.PaymentDomain;
import com.empresa.projeto.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper - Conversão entre Domain e Entity
 *
 * ANTI-CORRUPTION LAYER:
 * Previne que JPA "vaze" para o domain.
 */
@Component
public class PaymentPersistenceMapper {

    /**
     * Converte Domain → Entity (para salvar no banco)
     */
    public PaymentEntity toEntity(PaymentDomain domain) {
        if (domain == null) return null;

        return new PaymentEntity(
            domain.getPaymentId(),
            domain.getUserId(),
            domain.getAmount(),
            domain.getCurrency(),
            domain.getStatus(),
            domain.getCreatedAt(),
            domain.getUpdatedAt()
        );
    }

    /**
     * Converte Entity → Domain (ao carregar do banco)
     */
    public PaymentDomain toDomain(PaymentEntity entity) {
        if (entity == null) return null;

        // Usa construtor de "restauração"
        return new PaymentDomain(
            entity.getPaymentId(),
            entity.getUserId(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

**Spring Data JPA Repository:**

```java
package com.empresa.projeto.infrastructure.adapter.out.persistence.repository;

import com.empresa.projeto.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository
 *
 * Spring gera implementação automaticamente.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {

    // Métodos automáticos:
    // - save()
    // - findById()
    // - existsById()
    // - delete()

    // Métodos customizados (Spring gera query automaticamente)
    List<PaymentEntity> findByUserId(String userId);
}
```

**Adapter (implementa a Porta):**

```java
package com.empresa.projeto.infrastructure.adapter.out.persistence;

import com.empresa.projeto.application.port.out.PaymentRepositoryPort;
import com.empresa.projeto.domain.model.PaymentDomain;
import com.empresa.projeto.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.empresa.projeto.infrastructure.adapter.out.persistence.mapper.PaymentPersistenceMapper;
import com.empresa.projeto.infrastructure.adapter.out.persistence.repository.PaymentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ADAPTER de Persistência
 *
 * Implementa PaymentRepositoryPort usando JPA.
 *
 * FLUXO:
 * Domain → Mapper → Entity → JPA → Banco
 * Banco → JPA → Entity → Mapper → Domain
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
        // Domain → Entity
        PaymentEntity entity = mapper.toEntity(payment);

        // JPA save
        PaymentEntity savedEntity = jpaRepository.save(entity);

        // Entity → Domain
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentDomain> findById(String paymentId) {
        return jpaRepository.findById(paymentId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(String paymentId) {
        return jpaRepository.existsById(paymentId);
    }

    @Override
    public List<PaymentDomain> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
```

#### Adapter de Mensageria (Kafka)

**Evento Kafka:**

```java
package com.empresa.projeto.infrastructure.adapter.out.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento - Pagamento Aprovado
 *
 * Estrutura do evento publicado no Kafka.
 */
public record PaymentApprovedEvent(
    String eventId,         // UUID único do evento
    String paymentId,       // ID do pagamento
    String userId,          // ID do usuário (chave de partição)
    BigDecimal amount,      // Valor
    String currency,        // Moeda
    String status,          // Status
    Instant timestamp       // Timestamp do evento
) {}
```

**Mapper (Domain → Event):**

```java
package com.empresa.projeto.infrastructure.adapter.out.messaging.mapper;

import com.empresa.projeto.domain.model.PaymentDomain;
import com.empresa.projeto.infrastructure.adapter.out.messaging.event.PaymentApprovedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper - Domain para Eventos Kafka
 */
@Component
public class PaymentEventMapper {

    public PaymentApprovedEvent toPaymentApprovedEvent(PaymentDomain domain) {
        return new PaymentApprovedEvent(
            UUID.randomUUID().toString(),     // eventId único
            domain.getPaymentId(),
            domain.getUserId(),
            domain.getAmount(),
            domain.getCurrency(),
            domain.getStatus().name(),
            Instant.now()
        );
    }
}
```

**Kafka Producer:**

```java
package com.empresa.projeto.infrastructure.adapter.out.messaging.producer;

import com.empresa.projeto.infrastructure.adapter.out.messaging.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Producer Kafka para eventos de pagamento.
 */
@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment-approved}")
    private String paymentApprovedTopic;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica evento de pagamento aprovado.
     *
     * @param event evento a publicar
     */
    public void publishPaymentApproved(PaymentApprovedEvent event) {

        // Criar record com headers
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            paymentApprovedTopic,
            event.userId(),  // Key = userId (para particionamento)
            event            // Value = evento
        );

        // Adicionar headers (metadados)
        record.headers().add(new RecordHeader(
            "event-type",
            "PAYMENT_APPROVED".getBytes(StandardCharsets.UTF_8)
        ));
        record.headers().add(new RecordHeader(
            "event-id",
            event.eventId().getBytes(StandardCharsets.UTF_8)
        ));

        // Enviar com callback
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish PaymentApproved event: {}", event, ex);
            } else {
                log.info("Published PaymentApproved event: paymentId={}, partition={}, offset={}",
                    event.paymentId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            }
        });
    }
}
```

**Adapter Kafka (implementa a Porta):**

```java
package com.empresa.projeto.infrastructure.adapter.out.messaging;

import com.empresa.projeto.application.port.out.PaymentEventPublisherPort;
import com.empresa.projeto.domain.model.PaymentDomain;
import com.empresa.projeto.infrastructure.adapter.out.messaging.event.PaymentApprovedEvent;
import com.empresa.projeto.infrastructure.adapter.out.messaging.mapper.PaymentEventMapper;
import com.empresa.projeto.infrastructure.adapter.out.messaging.producer.PaymentEventProducer;
import org.springframework.stereotype.Component;

/**
 * ADAPTER de Mensageria (Kafka)
 *
 * Implementa PaymentEventPublisherPort usando Kafka.
 */
@Component
public class KafkaEventPublisherAdapter implements PaymentEventPublisherPort {

    private final PaymentEventProducer eventProducer;
    private final PaymentEventMapper eventMapper;

    public KafkaEventPublisherAdapter(
            PaymentEventProducer eventProducer,
            PaymentEventMapper eventMapper) {
        this.eventProducer = eventProducer;
        this.eventMapper = eventMapper;
    }

    @Override
    public void publishPaymentApproved(PaymentDomain payment) {
        PaymentApprovedEvent event = eventMapper.toPaymentApprovedEvent(payment);
        eventProducer.publishPaymentApproved(event);
    }

    @Override
    public void publishPaymentCanceled(PaymentDomain payment) {
        // Implementação similar...
    }

    @Override
    public void publishPaymentRefunded(PaymentDomain payment) {
        // Implementação similar...
    }
}
```

#### Adapter Web (REST Controller)

**DTOs HTTP:**

```java
package com.empresa.projeto.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

/**
 * DTO de entrada - Requisição HTTP
 */
public record PaymentRequestDto(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency
) {}
```

```java
package com.empresa.projeto.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de saída - Resposta HTTP
 */
public record PaymentResponseDto(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Mapper Web:**

```java
package com.empresa.projeto.infrastructure.adapter.in.web.mapper;

import com.empresa.projeto.application.command.ApprovePaymentCommand;
import com.empresa.projeto.application.command.PaymentResponse;
import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentRequestDto;
import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import org.springframework.stereotype.Component;

/**
 * Mapper Web - DTO ↔ Command/Response
 */
@Component
public class PaymentWebMapper {

    /**
     * DTO → Command (entrada)
     */
    public ApprovePaymentCommand toCommand(PaymentRequestDto dto) {
        return new ApprovePaymentCommand(
            dto.paymentId(),
            dto.userId(),
            dto.amount(),
            dto.currency()
        );
    }

    /**
     * Response → DTO (saída)
     */
    public PaymentResponseDto toDto(PaymentResponse response) {
        return new PaymentResponseDto(
            response.paymentId(),
            response.userId(),
            response.amount(),
            response.currency(),
            response.status().name(),
            response.createdAt(),
            response.updatedAt()
        );
    }
}
```

**REST Controller:**

```java
package com.empresa.projeto.infrastructure.adapter.in.web.controller;

import com.empresa.projeto.application.command.ApprovePaymentCommand;
import com.empresa.projeto.application.command.PaymentResponse;
import com.empresa.projeto.application.port.in.ApprovePaymentUseCase;
import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentRequestDto;
import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import com.empresa.projeto.infrastructure.adapter.in.web.mapper.PaymentWebMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller - Pagamentos
 *
 * FLUXO:
 * HTTP Request → DTO → Command → UseCase → Response → DTO → HTTP Response
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final PaymentWebMapper mapper;

    public PaymentController(
            ApprovePaymentUseCase approvePaymentUseCase,
            PaymentWebMapper mapper) {
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.mapper = mapper;
    }

    /**
     * POST /api/v1/payments/approve
     *
     * Aprova um pagamento.
     */
    @PostMapping("/approve")
    public ResponseEntity<PaymentResponseDto> approvePayment(
            @RequestBody PaymentRequestDto request) {

        // 1. DTO → Command
        ApprovePaymentCommand command = mapper.toCommand(request);

        // 2. Executar Use Case
        PaymentResponse response = approvePaymentUseCase.approve(command);

        // 3. Response → DTO
        PaymentResponseDto dto = mapper.toDto(response);

        // 4. Retornar HTTP Response
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }
}
```

---

## 4. Implementação Passo a Passo

### Ordem de Implementação (CRÍTICO!)

```
PASSO 1: Domain Layer
  ├── 1.1 Criar modelos de domínio
  ├── 1.2 Criar exceções de domínio
  └── 1.3 Testar domain (unit tests puros)

PASSO 2: Application Layer - Ports
  ├── 2.1 Criar Inbound Ports (use cases)
  ├── 2.2 Criar Outbound Ports (dependências)
  └── 2.3 Criar Commands e Responses

PASSO 3: Application Layer - Services
  ├── 3.1 Implementar Use Case Services
  └── 3.2 Testar services (com mocks das portas)

PASSO 4: Infrastructure Layer - Adapters
  ├── 4.1 Criar Adapter de Persistência (JPA)
  ├── 4.2 Criar Adapter de Mensageria (Kafka)
  ├── 4.3 Criar Adapter Web (REST)
  └── 4.4 Configurações (Beans, Kafka, JPA)

PASSO 5: Testes de Integração
  └── 5.1 Testar fluxo completo (E2E)
```

### Checklist Detalhado

#### ✅ PASSO 1: Domain Layer

```
[ ] Criar package domain/model
[ ] Criar PaymentDomain.java (SEM @Entity)
    [ ] Campos final quando possível
    [ ] Validações no construtor
    [ ] Métodos de negócio (approve, cancel, etc)
    [ ] Getters (NO setters públicos)
    [ ] equals/hashCode baseado em ID
[ ] Criar PaymentStatus.java (enum)
[ ] Criar package domain/exception
[ ] Criar InvalidPaymentException.java
[ ] Criar PaymentNotFoundException.java
[ ] Escrever testes unitários (sem Spring)
```

#### ✅ PASSO 2: Application Layer - Ports

```
[ ] Criar package application/port/in
[ ] Criar ApprovePaymentUseCase.java (interface)
[ ] Criar CancelPaymentUseCase.java (interface)
[ ] Criar package application/port/out
[ ] Criar PaymentRepositoryPort.java (interface)
[ ] Criar PaymentEventPublisherPort.java (interface)
[ ] Criar package application/command
[ ] Criar ApprovePaymentCommand.java (record)
[ ] Criar PaymentResponse.java (record)
```

#### ✅ PASSO 3: Application Layer - Services

```
[ ] Criar package application/service
[ ] Criar ApprovePaymentService.java
    [ ] Implementa ApprovePaymentUseCase
    [ ] Injeta PaymentRepositoryPort
    [ ] Injeta PaymentEventPublisherPort
    [ ] Método approve() orquestra:
        [ ] Criar PaymentDomain
        [ ] Chamar payment.approve()
        [ ] Salvar via repository port
        [ ] Publicar via event port
        [ ] Retornar PaymentResponse
[ ] Anotar com @Service
[ ] Anotar método com @Transactional
[ ] Escrever testes (mockando portas)
```

#### ✅ PASSO 4: Infrastructure - Persistence Adapter

```
[ ] Criar package infrastructure/adapter/out/persistence
[ ] Criar PaymentEntity.java
    [ ] Anotar com @Entity, @Table
    [ ] Anotar campos com @Column
    [ ] Construtor padrão (protected)
    [ ] Getters e Setters
[ ] Criar PaymentPersistenceMapper.java
    [ ] toEntity(PaymentDomain) → PaymentEntity
    [ ] toDomain(PaymentEntity) → PaymentDomain
[ ] Criar PaymentJpaRepository.java (extends JpaRepository)
[ ] Criar PaymentPersistenceAdapter.java
    [ ] Implementa PaymentRepositoryPort
    [ ] Anotar com @Component
    [ ] Injetar PaymentJpaRepository
    [ ] Injetar PaymentPersistenceMapper
    [ ] Implementar métodos (save, findById, etc)
```

#### ✅ PASSO 5: Infrastructure - Messaging Adapter

```
[ ] Criar package infrastructure/adapter/out/messaging
[ ] Criar PaymentApprovedEvent.java (record)
[ ] Criar PaymentEventMapper.java
    [ ] toPaymentApprovedEvent(PaymentDomain)
[ ] Criar PaymentEventProducer.java
    [ ] Injetar KafkaTemplate
    [ ] publishPaymentApproved(event)
[ ] Criar KafkaEventPublisherAdapter.java
    [ ] Implementa PaymentEventPublisherPort
    [ ] Anotar com @Component
    [ ] Injetar PaymentEventProducer
    [ ] Injetar PaymentEventMapper
```

#### ✅ PASSO 6: Infrastructure - Web Adapter

```
[ ] Criar package infrastructure/adapter/in/web
[ ] Criar PaymentRequestDto.java (record)
[ ] Criar PaymentResponseDto.java (record)
[ ] Criar PaymentWebMapper.java
    [ ] toCommand(dto) → ApprovePaymentCommand
    [ ] toDto(response) → PaymentResponseDto
[ ] Criar PaymentController.java
    [ ] Anotar com @RestController, @RequestMapping
    [ ] Injetar ApprovePaymentUseCase
    [ ] Injetar PaymentWebMapper
    [ ] Endpoint POST /approve
```

#### ✅ PASSO 7: Configurações

```
[ ] application.yaml
    [ ] Configurar Kafka
    [ ] Configurar JPA/Postgres
    [ ] Configurar server port
[ ] KafkaConfig.java (se necessário)
[ ] Migration SQL (Flyway)
    [ ] V1__create_payment_table.sql
```

---

## 5. Padrões de Código

### Padrão: Domain Model

```java
// ✅ CORRETO
public class PaymentDomain {
    private final String id;          // Imutável
    private PaymentStatus status;     // Mutável (estado)

    // Validação no construtor
    public PaymentDomain(String id, BigDecimal amount) {
        if (amount.compareTo(ZERO) <= 0) {
            throw new InvalidPaymentException("Amount must be positive");
        }
        this.id = id;
        this.status = PENDING;
    }

    // Lógica de negócio
    public void approve() {
        if (status == CANCELED) {
            throw new IllegalStateException("Cannot approve canceled payment");
        }
        this.status = APPROVED;
    }
}
```

```java
// ❌ ERRADO
@Entity  // NÃO usar @Entity no domain!
public class PaymentDomain {
    private String id;

    public void setId(String id) {  // NÃO expor setters!
        this.id = id;
    }

    // Sem validações!
}
```

### Padrão: Use Case Service

```java
// ✅ CORRETO
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepositoryPort repository;  // Porta (interface)

    @Transactional
    public PaymentResponse approve(ApprovePaymentCommand cmd) {
        PaymentDomain payment = new PaymentDomain(...);
        payment.approve();  // Lógica NO DOMAIN
        return repository.save(payment);
    }
}
```

```java
// ❌ ERRADO
@Service
public class ApprovePaymentService {

    private final PaymentJpaRepository jpaRepo;  // Implementação concreta!

    public PaymentResponse approve(ApprovePaymentCommand cmd) {
        PaymentDomain payment = new PaymentDomain(...);

        // Lógica NO SERVICE (errado!)
        if (payment.getStatus() == CANCELED) {
            throw new IllegalStateException("...");
        }
        payment.setStatus(APPROVED);  // Setter público (errado!)
    }
}
```

### Padrão: Adapter

```java
// ✅ CORRETO
@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepo;
    private final PaymentPersistenceMapper mapper;

    public PaymentDomain save(PaymentDomain domain) {
        PaymentEntity entity = mapper.toEntity(domain);
        PaymentEntity saved = jpaRepo.save(entity);
        return mapper.toDomain(saved);
    }
}
```

### Padrão: Mapper

```java
// ✅ CORRETO
@Component
public class PaymentPersistenceMapper {

    // Domain → Entity (salvar)
    public PaymentEntity toEntity(PaymentDomain domain) {
        return new PaymentEntity(
            domain.getId(),
            domain.getAmount(),
            domain.getStatus()
        );
    }

    // Entity → Domain (carregar)
    public PaymentDomain toDomain(PaymentEntity entity) {
        return new PaymentDomain(
            entity.getId(),
            entity.getAmount(),
            entity.getStatus(),
            entity.getCreatedAt()  // Construtor de restauração
        );
    }
}
```

---

## 6. Testes na Prática

### Teste de Domain (PURO - sem Spring)

```java
package com.empresa.projeto.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do PaymentDomain.
 *
 * SEM @SpringBootTest!
 * SEM banco!
 * SEM Kafka!
 *
 * Roda em MILISSEGUNDOS!
 */
class PaymentDomainTest {

    @Test
    void shouldCreatePaymentWithPendingStatus() {
        // Arrange & Act
        PaymentDomain payment = new PaymentDomain(
            "pay-123",
            "user-456",
            new BigDecimal("100.00"),
            "BRL"
        );

        // Assert
        assertEquals("pay-123", payment.getPaymentId());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertTrue(payment.isPending());
    }

    @Test
    void shouldApprovePayment() {
        // Arrange
        PaymentDomain payment = new PaymentDomain(
            "pay-123", "user-456", new BigDecimal("100.00"), "BRL"
        );

        // Act
        payment.approve();

        // Assert
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertTrue(payment.isApproved());
    }

    @Test
    void shouldNotApproveCanceledPayment() {
        // Arrange
        PaymentDomain payment = new PaymentDomain(
            "pay-123", "user-456", new BigDecimal("100.00"), "BRL"
        );
        payment.cancel();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            payment.approve();
        });
    }

    @Test
    void shouldThrowExceptionWhenAmountIsZero() {
        // Act & Assert
        assertThrows(InvalidPaymentException.class, () -> {
            new PaymentDomain(
                "pay-123",
                "user-456",
                BigDecimal.ZERO,  // Inválido!
                "BRL"
            );
        });
    }

    @Test
    void shouldThrowExceptionWhenPaymentIdIsBlank() {
        // Act & Assert
        assertThrows(InvalidPaymentException.class, () -> {
            new PaymentDomain(
                "",  // Inválido!
                "user-456",
                new BigDecimal("100.00"),
                "BRL"
            );
        });
    }
}
```

### Teste de Use Case (com Mocks)

```java
package com.empresa.projeto.application.service;

import com.empresa.projeto.application.command.ApprovePaymentCommand;
import com.empresa.projeto.application.command.PaymentResponse;
import com.empresa.projeto.application.port.out.PaymentEventPublisherPort;
import com.empresa.projeto.application.port.out.PaymentRepositoryPort;
import com.empresa.projeto.domain.model.PaymentDomain;
import com.empresa.projeto.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes do ApprovePaymentService.
 *
 * USA @ExtendWith(MockitoExtension) - SEM @SpringBootTest!
 * Mocka as PORTAS (interfaces).
 * Roda RÁPIDO!
 */
@ExtendWith(MockitoExtension.class)
class ApprovePaymentServiceTest {

    @Mock
    private PaymentRepositoryPort paymentRepository;

    @Mock
    private PaymentEventPublisherPort eventPublisher;

    @InjectMocks
    private ApprovePaymentService service;

    @Test
    void shouldApprovePaymentSuccessfully() {
        // Arrange
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "pay-123",
            "user-456",
            new BigDecimal("100.00"),
            "BRL"
        );

        PaymentDomain savedPayment = new PaymentDomain(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency(),
            PaymentStatus.APPROVED,
            Instant.now(),
            Instant.now()
        );

        when(paymentRepository.save(any(PaymentDomain.class)))
            .thenReturn(savedPayment);

        // Act
        PaymentResponse response = service.approve(command);

        // Assert
        assertNotNull(response);
        assertEquals("pay-123", response.paymentId());
        assertEquals(PaymentStatus.APPROVED, response.status());

        // Verify interactions
        verify(paymentRepository, times(1)).save(any(PaymentDomain.class));
        verify(eventPublisher, times(1)).publishPaymentApproved(any(PaymentDomain.class));
    }
}
```

### Teste de Integração (E2E)

```java
package com.empresa.projeto;

import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentRequestDto;
import com.empresa.projeto.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de Integração - Fluxo Completo.
 *
 * USA @SpringBootTest - Sobe contexto completo.
 * Testa HTTP → Controller → Use Case → Adapter → Banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldApprovePaymentEndToEnd() {
        // Arrange
        PaymentRequestDto request = new PaymentRequestDto(
            "pay-" + System.currentTimeMillis(),
            "user-456",
            new BigDecimal("100.00"),
            "BRL"
        );

        // Act
        ResponseEntity<PaymentResponseDto> response = restTemplate.postForEntity(
            "/api/v1/payments/approve",
            request,
            PaymentResponseDto.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("APPROVED", response.getBody().status());
        assertEquals(request.paymentId(), response.getBody().paymentId());
    }
}
```

---

## 7. Casos de Uso Reais

### Caso 1: Trocar Banco de Dados (JPA → MongoDB)

**Problema:** Precisamos migrar de PostgreSQL (JPA) para MongoDB.

**Solução com Hexagonal:**

1. **Criar Adapter MongoDB** (implementa a MESMA porta)

```java
@Component
@ConditionalOnProperty(name = "db.type", havingValue = "mongo")
public class PaymentMongoAdapter implements PaymentRepositoryPort {

    private final PaymentMongoRepository mongoRepo;
    private final PaymentMongoMapper mapper;

    @Override
    public PaymentDomain save(PaymentDomain payment) {
        PaymentDocument doc = mapper.toDocument(payment);
        PaymentDocument saved = mongoRepo.save(doc);
        return mapper.toDomain(saved);
    }
}
```

2. **Configurar application.yaml**

```yaml
# Para JPA
db:
  type: jpa

# Para MongoDB
db:
  type: mongo
```

3. **PRONTO!** Domain e Use Cases NÃO mudam!

### Caso 2: Adicionar gRPC (além de REST)

**Problema:** Clientes querem gRPC além de REST.

**Solução com Hexagonal:**

1. **Criar Adapter gRPC**

```java
@GrpcService
public class PaymentGrpcAdapter extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final ApprovePaymentUseCase approveUseCase;
    private final PaymentGrpcMapper mapper;

    @Override
    public void approvePayment(ApprovePaymentRequest request,
                               StreamObserver<PaymentResponse> responseObserver) {

        ApprovePaymentCommand command = mapper.toCommand(request);
        PaymentResponse response = approveUseCase.approve(command);
        PaymentProto proto = mapper.toProto(response);

        responseObserver.onNext(proto);
        responseObserver.onCompleted();
    }
}
```

2. **REST e gRPC funcionam juntos!** Use Case é o mesmo!

### Caso 3: Testes A/B (Kafka vs RabbitMQ)

**Problema:** Testar performance Kafka vs RabbitMQ.

**Solução:**

```java
// Adapter Kafka
@Component
@ConditionalOnProperty(name = "messaging.type", havingValue = "kafka")
public class KafkaEventPublisherAdapter implements PaymentEventPublisherPort {
    // Implementação Kafka
}

// Adapter RabbitMQ
@Component
@ConditionalOnProperty(name = "messaging.type", havingValue = "rabbitmq")
public class RabbitMqEventPublisherAdapter implements PaymentEventPublisherPort {
    // Implementação RabbitMQ
}
```

**Trocar em runtime:**

```yaml
messaging:
  type: kafka  # ou rabbitmq
```

---

## 8. Checklist de Implementação

### ✅ Checklist Final - Antes de Produção

```
DOMAIN LAYER
[ ] Modelos de domínio SEM annotations de frameworks
[ ] Validações no construtor
[ ] Lógica de negócio nos métodos do domain
[ ] Exceções de domínio criadas
[ ] Testes unitários (sem Spring) com 80%+ cobertura

APPLICATION LAYER
[ ] Inbound Ports (use cases) definidos
[ ] Outbound Ports (dependências) definidos
[ ] Commands e Responses criados (records)
[ ] Services implementam use cases
[ ] Services anotados com @Service e @Transactional
[ ] Services dependem de PORTAS (não de implementações)

INFRASTRUCTURE LAYER - PERSISTENCE
[ ] Entidade JPA com @Entity, @Table
[ ] JPA Repository criado (extends JpaRepository)
[ ] Mapper Domain ↔ Entity
[ ] Adapter implementa PaymentRepositoryPort
[ ] Adapter anotado com @Component

INFRASTRUCTURE LAYER - MESSAGING
[ ] Eventos Kafka criados (records)
[ ] Mapper Domain → Event
[ ] Producer Kafka criado
[ ] Adapter implementa PaymentEventPublisherPort
[ ] Adapter anotado com @Component

INFRASTRUCTURE LAYER - WEB
[ ] DTOs HTTP criados (records)
[ ] Mapper DTO ↔ Command/Response
[ ] Controller criado (@RestController)
[ ] Controller depende de USE CASE (não de service)
[ ] Exception handler configurado

CONFIGURAÇÕES
[ ] application.yaml configurado
[ ] KafkaConfig (se necessário)
[ ] Flyway migration SQL criado
[ ] Testes de integração funcionando

TESTES
[ ] Testes de domain (puros) - 80%+
[ ] Testes de use case (com mocks) - 70%+
[ ] Testes de integração (E2E) - casos principais

DOCUMENTAÇÃO
[ ] README com arquitetura
[ ] Diagrama de camadas
[ ] Exemplos de uso da API
```

---

## Conclusão

Este tutorial mostrou na **PRÁTICA** como implementar Arquitetura Hexagonal em projetos de produção.

### Próximos Passos

1. Explore o código deste projeto (`ms-producer`) - está 100% implementado!
2. Pratique criando novos use cases
3. Adicione novos adapters (ex: MongoDB, Redis)
4. Implemente CQRS e Event Sourcing

### Resumo Rápido

```
┌─────────────────────────────────────────┐
│  HEXAGONAL EM 30 SEGUNDOS:             │
├─────────────────────────────────────────┤
│                                         │
│  1. Domain = Lógica pura (sem @Entity) │
│  2. Ports = Interfaces                  │
│  3. Adapters = Implementações (JPA/Kafka)│
│  4. Use Cases = Orquestração            │
│                                         │
│  REGRA DE OURO:                         │
│  Domain → Ports ← Adapters              │
│  (Dependency Inversion!)                │
│                                         │
└─────────────────────────────────────────┘
```

**Bom trabalho! 🚀**
