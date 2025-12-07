# Tutorial Prático: Clean Architecture em Produção

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

Clean Architecture (criada por Uncle Bob) é um padrão baseado em **círculos concêntricos** onde:

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  ENTITIES (Regras de Negócio da Empresa)               │
│      ↓ são usadas por ↓                                │
│  USE CASES (Regras de Negócio da Aplicação)            │
│      ↓ são usadas por ↓                                │
│  INTERFACE ADAPTERS (Controllers, Presenters, Gateways)│
│      ↓ são usadas por ↓                                │
│  FRAMEWORKS & DRIVERS (Spring, DB, UI, Web)            │
│                                                         │
│  DEPENDENCY RULE: Apenas para DENTRO!                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Em português claro:**
- **Entities**: Regras de negócio UNIVERSAIS (valem em qualquer sistema)
- **Use Cases**: Regras específicas desta aplicação
- **Interface Adapters**: Tradutores (HTTP → Use Cases, Use Cases → DB)
- **Frameworks & Drivers**: Tecnologias (Spring, JPA, Kafka, REST)

### Por Que Usar em Produção?

| Problema Comum | Solução Clean Architecture |
|----------------|---------------------------|
| Regras de negócio misturadas com framework | Entities isoladas (sem Spring, JPA, etc) |
| Mudar UI (web → mobile) reescreve tudo | Apenas troca adapter (Use Cases intactos) |
| Testes lentos (precisa banco/Kafka) | Testa Entities e Use Cases puros (ms) |
| Difícil evoluir (acoplamento alto) | Dependências apontam SEMPRE para dentro |

### Diagrama Visual Completo

```
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│                   FRAMEWORKS & DRIVERS                            │
│                     (Círculo Externo)                             │
│                                                                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │   Spring     │ │   Kafka      │ │  PostgreSQL  │             │
│  │   Boot       │ │   Producer   │ │  (JPA)       │             │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘             │
│         │                 │                 │                     │
│         └─────────────────┴─────────────────┘                     │
│                           │                                       │
└───────────────────────────┼───────────────────────────────────────┘
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│                   INTERFACE ADAPTERS                              │
│                     (Círculo 3)                                   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              CONTROLLERS (Inbound)                       │    │
│  │  ┌──────────────────┐  ┌──────────────────┐             │    │
│  │  │ REST Controller  │  │ GraphQL Resolver │             │    │
│  │  │ (HTTP → UseCase) │  │ (GraphQL → UseCa)│             │    │
│  │  └──────────────────┘  └──────────────────┘             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                            │                                      │
│                            ▼                                      │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              PRESENTERS (Outbound)                       │    │
│  │  ┌──────────────────┐  ┌──────────────────┐             │    │
│  │  │ Response Builder │  │ DTO Mapper       │             │    │
│  │  │ (UseCase → JSON) │  │ (Entity → DTO)   │             │    │
│  │  └──────────────────┘  └──────────────────┘             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                            │                                      │
│                            ▼                                      │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              GATEWAYS (Data Access)                      │    │
│  │  ┌──────────────────┐  ┌──────────────────┐             │    │
│  │  │ JPA Gateway      │  │ Kafka Gateway    │             │    │
│  │  │ (UseCase → DB)   │  │ (UseCase → Topic)│             │    │
│  │  └──────────────────┘  └──────────────────┘             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
└───────────────────────────┬───────────────────────────────────────┘
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│                      USE CASES                                    │
│                     (Círculo 2)                                   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │         APPLICATION BUSINESS RULES                       │    │
│  │                                                          │    │
│  │  ┌──────────────────┐  ┌──────────────────┐             │    │
│  │  │ Approve Payment  │  │ Cancel Payment   │             │    │
│  │  │ Interactor       │  │ Interactor       │             │    │
│  │  │                  │  │                  │             │    │
│  │  │ 1. Validate      │  │ 1. Find Payment  │             │    │
│  │  │ 2. Execute Entity│  │ 2. Cancel        │             │    │
│  │  │ 3. Save          │  │ 3. Save          │             │    │
│  │  │ 4. Notify        │  │ 4. Notify        │             │    │
│  │  └──────────────────┘  └──────────────────┘             │    │
│  │                                                          │    │
│  │  ┌──────────────────────────────────────────────────┐   │    │
│  │  │         INPUT/OUTPUT PORTS                       │   │    │
│  │  │  - Input Boundary (interface para controllers)  │   │    │
│  │  │  - Output Boundary (interface para gateways)    │   │    │
│  │  └──────────────────────────────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
└───────────────────────────┬───────────────────────────────────────┘
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│                       ENTITIES                                    │
│                     (Círculo 1 - Núcleo)                          │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │         ENTERPRISE BUSINESS RULES                        │    │
│  │                                                          │    │
│  │  ┌──────────────────┐  ┌──────────────────┐             │    │
│  │  │ Payment Entity   │  │ PaymentStatus    │             │    │
│  │  │                  │  │ (Value Object)   │             │    │
│  │  │ - id             │  │                  │             │    │
│  │  │ - amount         │  │ - PENDING        │             │    │
│  │  │ - status         │  │ - APPROVED       │             │    │
│  │  │ - userId         │  │ - CANCELED       │             │    │
│  │  │                  │  └──────────────────┘             │    │
│  │  │ + approve()      │                                   │    │
│  │  │ + cancel()       │  ┌──────────────────┐             │    │
│  │  │ + isValid()      │  │ Money            │             │    │
│  │  │                  │  │ (Value Object)   │             │    │
│  │  └──────────────────┘  │                  │             │    │
│  │                        │ - amount         │             │    │
│  │  ┌──────────────────┐  │ - currency       │             │    │
│  │  │ Business Rules   │  │                  │             │    │
│  │  │ Validators       │  │ + add()          │             │    │
│  │  │                  │  │ + subtract()     │             │    │
│  │  └──────────────────┘  └──────────────────┘             │    │
│  │                                                          │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### Clean Architecture vs Hexagonal

**São COMPLEMENTARES, não concorrentes!**

| Aspecto | Clean Architecture | Hexagonal (Ports & Adapters) |
|---------|-------------------|------------------------------|
| Foco | Separação em CAMADAS concêntricas | Separação entre CORE e ADAPTADORES |
| Dependências | Sempre apontam para DENTRO | Core → Ports ← Adapters |
| Camadas | 4 círculos (Entities, Use Cases, Adapters, Frameworks) | 3 camadas (Domain, Application, Infrastructure) |
| Termos | Interactors, Boundaries, Presenters | Ports, Adapters, Use Cases |
| Origem | Uncle Bob (Robert C. Martin) | Alistair Cockburn |
| Uso Prático | Mais teórica (filosofia) | Mais prática (implementação) |

**NA PRÁTICA:** Você pode (e deve!) usar ambas juntas:
- **Clean Architecture:** Para organizar CONCEITUALMENTE (camadas, dependências)
- **Hexagonal:** Para IMPLEMENTAR (ports, adapters, use cases)

---

## 2. Estrutura Completa do Projeto

### Organização de Pastas (Clean Architecture com Spring Boot)

```
src/main/java/com/empresa/projeto/
│
├── entity/                                       # CAMADA 1: ENTITIES
│   ├── Payment.java                             # Entity pura (regras universais)
│   ├── Order.java
│   ├── Customer.java
│   │
│   ├── valueobject/                             # Value Objects
│   │   ├── Money.java                           # Imutável, sem identidade
│   │   ├── PaymentStatus.java
│   │   ├── Address.java
│   │   └── Email.java
│   │
│   ├── exception/                               # Exceções de negócio
│   │   ├── InvalidPaymentException.java
│   │   ├── InsufficientBalanceException.java
│   │   └── PaymentNotFoundException.java
│   │
│   └── validator/                               # Validadores de regras
│       ├── PaymentValidator.java
│       └── AmountValidator.java
│
├── usecase/                                      # CAMADA 2: USE CASES
│   ├── approvepayment/                          # Use Case: Aprovar Pagamento
│   │   ├── ApprovePaymentInputBoundary.java    # Interface (entrada)
│   │   ├── ApprovePaymentOutputBoundary.java   # Interface (saída)
│   │   ├── ApprovePaymentInteractor.java       # Implementação do Use Case
│   │   ├── ApprovePaymentRequest.java          # Input Data (DTO)
│   │   └── ApprovePaymentResponse.java         # Output Data (DTO)
│   │
│   ├── cancelpayment/                           # Use Case: Cancelar Pagamento
│   │   ├── CancelPaymentInputBoundary.java
│   │   ├── CancelPaymentOutputBoundary.java
│   │   ├── CancelPaymentInteractor.java
│   │   ├── CancelPaymentRequest.java
│   │   └── CancelPaymentResponse.java
│   │
│   ├── findpayment/                             # Use Case: Buscar Pagamento
│   │   ├── FindPaymentInputBoundary.java
│   │   ├── FindPaymentOutputBoundary.java
│   │   ├── FindPaymentInteractor.java
│   │   ├── FindPaymentRequest.java
│   │   └── FindPaymentResponse.java
│   │
│   └── gateway/                                 # Interfaces (Output Boundaries)
│       ├── PaymentGateway.java                 # Interface para persistência
│       ├── NotificationGateway.java            # Interface para notificações
│       └── EventPublisherGateway.java          # Interface para eventos
│
├── adapter/                                      # CAMADA 3: INTERFACE ADAPTERS
│   ├── controller/                              # CONTROLLERS (Inbound)
│   │   ├── PaymentRestController.java          # REST API
│   │   ├── PaymentGraphQLController.java       # GraphQL
│   │   └── dto/                                 # DTOs HTTP
│   │       ├── PaymentRequestDto.java
│   │       ├── PaymentResponseDto.java
│   │       └── ErrorResponseDto.java
│   │
│   ├── presenter/                               # PRESENTERS (Outbound)
│   │   ├── PaymentJsonPresenter.java           # Formata resposta JSON
│   │   ├── PaymentXmlPresenter.java            # Formata resposta XML
│   │   └── mapper/
│   │       └── PaymentResponseMapper.java
│   │
│   └── gateway/                                 # GATEWAYS (Data Access)
│       ├── persistence/                         # Gateway de Persistência
│       │   ├── PaymentJpaGateway.java          # Implementa PaymentGateway
│       │   ├── entity/
│       │   │   └── PaymentJpaEntity.java       # @Entity JPA
│       │   ├── repository/
│       │   │   └── PaymentJpaRepository.java   # Spring Data
│       │   └── mapper/
│       │       └── PaymentEntityMapper.java
│       │
│       ├── messaging/                           # Gateway de Mensageria
│       │   ├── KafkaEventPublisherGateway.java # Implementa EventPublisherGateway
│       │   ├── event/
│       │   │   └── PaymentApprovedEvent.java
│       │   └── producer/
│       │       └── PaymentEventProducer.java
│       │
│       └── notification/                        # Gateway de Notificação
│           └── EmailNotificationGateway.java   # Implementa NotificationGateway
│
└── framework/                                    # CAMADA 4: FRAMEWORKS & DRIVERS
    ├── config/                                  # Configurações Spring
    │   ├── BeanConfiguration.java              # Bean wiring
    │   ├── KafkaConfiguration.java
    │   ├── JpaConfiguration.java
    │   └── SecurityConfiguration.java
    │
    └── exception/                               # Exception handlers
        └── GlobalExceptionHandler.java
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
        ├── V2__add_indexes.sql
        └── V3__add_audit_fields.sql
```

---

## 3. Camadas da Arquitetura

### 3.1 Entities - Regras de Negócio da Empresa

#### O Que São?

Entities contêm as **regras de negócio UNIVERSAIS** - aquelas que valem em QUALQUER sistema da empresa, independente de aplicação específica.

#### Características

```java
// ✅ ENTITIES PODEM TER:
- Regras de negócio universais
- Lógica que NUNCA muda (matemática, física, contabilidade)
- Value Objects (Money, Email, Address)
- Validações de domínio
- Invariantes de negócio

// ❌ ENTITIES NÃO PODEM TER:
- Dependências de frameworks (Spring, JPA, etc)
- Lógica específica de aplicação
- Conhecimento de banco de dados
- Conhecimento de UI ou HTTP
- Imports de javax.*, jakarta.*, org.springframework.*
```

#### Exemplo Completo: Payment Entity

```java
package com.empresa.projeto.entity;

import com.empresa.projeto.entity.exception.InvalidPaymentException;
import com.empresa.projeto.entity.valueobject.Money;
import com.empresa.projeto.entity.valueobject.PaymentStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment Entity - Regras de Negócio UNIVERSAIS.
 *
 * CARACTERÍSTICAS CLEAN ARCHITECTURE:
 * - SEM dependências de frameworks
 * - Contém APENAS regras que valem em QUALQUER sistema de pagamento
 * - Imutável quando possível
 * - Self-validating
 * - Rich domain model
 */
public class Payment {

    // ========== ATRIBUTOS ==========

    private final String id;
    private final String userId;
    private final Money amount;              // Value Object!
    private PaymentStatus status;            // Value Object mutável
    private final Instant createdAt;
    private Instant updatedAt;

    // ========== CONSTRUTORES ==========

    /**
     * Construtor para CRIAR novo pagamento.
     * Valida todas as regras de negócio UNIVERSAIS.
     */
    public Payment(String userId, Money amount) {
        // Validações de regras UNIVERSAIS
        this.id = UUID.randomUUID().toString();
        this.userId = requireNonBlank(userId, "User ID cannot be blank");
        this.amount = requireNonNull(amount, "Amount cannot be null");

        // Validar regras de Money
        if (!amount.isPositive()) {
            throw new InvalidPaymentException("Payment amount must be positive");
        }

        // Estado inicial
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Construtor para RESTAURAR do banco.
     * Usado pelos Gateways ao carregar dados persistidos.
     */
    public Payment(String id, String userId, Money amount,
                   PaymentStatus status, Instant createdAt,
                   Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== BUSINESS RULES (Regras UNIVERSAIS) ==========

    /**
     * Aprova o pagamento.
     *
     * REGRA UNIVERSAL:
     * - Pagamento cancelado NÃO pode ser aprovado
     * - Pagamento já aprovado é idempotente
     */
    public void approve() {
        if (status == PaymentStatus.CANCELED) {
            throw new IllegalStateException(
                "Cannot approve canceled payment: " + id
            );
        }

        if (status == PaymentStatus.APPROVED) {
            return; // Idempotência
        }

        this.status = PaymentStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Cancela o pagamento.
     *
     * REGRA UNIVERSAL:
     * - Pagamento aprovado NÃO pode ser cancelado (precisa refund)
     * - Pagamento já cancelado é idempotente
     */
    public void cancel() {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot cancel approved payment: " + id +
                ". Use refund instead."
            );
        }

        if (status == PaymentStatus.CANCELED) {
            return; // Idempotência
        }

        this.status = PaymentStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    /**
     * Processa reembolso.
     *
     * REGRA UNIVERSAL:
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

    /**
     * Valida se pagamento pode ser modificado.
     *
     * REGRA UNIVERSAL:
     * - Apenas pagamentos PENDING podem ser editados
     */
    public boolean canBeModified() {
        return status == PaymentStatus.PENDING;
    }

    /**
     * Valida integridade completa.
     */
    public boolean isValid() {
        return id != null
            && !id.isBlank()
            && userId != null
            && !userId.isBlank()
            && amount != null
            && amount.isPositive()
            && status != null
            && createdAt != null
            && updatedAt != null;
    }

    // ========== VALIDAÇÕES PRIVADAS ==========

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentException(message);
        }
        return value;
    }

    private <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new InvalidPaymentException(message);
        }
        return value;
    }

    // ========== GETTERS (SOMENTE LEITURA) ==========

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ========== PREDICATES ==========

    public boolean isPending() { return status == PaymentStatus.PENDING; }
    public boolean isApproved() { return status == PaymentStatus.APPROVED; }
    public boolean isCanceled() { return status == PaymentStatus.CANCELED; }
    public boolean isRefunded() { return status == PaymentStatus.REFUNDED; }

    // ========== EQUALS & HASHCODE ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment)) return false;
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
```

