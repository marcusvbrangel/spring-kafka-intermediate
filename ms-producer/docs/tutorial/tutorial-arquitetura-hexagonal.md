# Tutorial Definitivo: Arquitetura Hexagonal (Ports & Adapters) - Isole Seu Domínio

## 📋 Sumário

1. [O que é Arquitetura Hexagonal](#1-o-que-é-arquitetura-hexagonal)
2. [Por Que Hexagonal e Não Camadas](#2-por-que-hexagonal-e-não-camadas)
3. [Ports vs Adapters](#3-ports-vs-adapters)
4. [Implementação Passo a Passo](#4-implementação-passo-a-passo)
5. [Inbound vs Outbound](#5-inbound-vs-outbound)
6. [Testes com Hexagonal](#6-testes-com-hexagonal)
7. [Hexagonal no Dia a Dia](#7-hexagonal-no-dia-a-dia)
8. [Armadilhas Comuns](#8-armadilhas-comuns)
9. [Checklist Hexagonal](#9-checklist-hexagonal)
10. [Exercícios Práticos](#10-exercícios-práticos)

---

## 1. O que é Arquitetura Hexagonal

### Definição em 30 Segundos

**Arquitetura Hexagonal** (também chamada de **Ports & Adapters**) é um padrão arquitetural onde o **DOMÍNIO** está no **CENTRO**, **isolado** de tecnologias externas (frameworks, banco de dados, mensageria).

```
┌────────────────────────────────────────────┐
│   HEXÁGONO = DOMÍNIO ISOLADO               │
│                                            │
│   ┌────────────────────────────────┐       │
│   │                                │       │
│   │        APPLICATION             │       │
│   │      (Use Cases)               │       │
│   │                                │       │
│   │   ┌────────────────────┐       │       │
│   │   │                    │       │       │
│   │   │      DOMAIN        │       │       │
│   │   │   (Entities, VOs)  │       │       │
│   │   │                    │       │       │
│   │   └────────────────────┘       │       │
│   │                                │       │
│   └────────────────────────────────┘       │
│                                            │
└────────────────────────────────────────────┘
         ↑              ↑              ↑
       PORT           PORT           PORT
         ↑              ↑              ↑
      ADAPTER        ADAPTER        ADAPTER
     (REST API)    (PostgreSQL)    (Kafka)
```

**Conceitos-chave:**
- **Hexágono** = Domínio + Application (núcleo da aplicação)
- **Ports** = Interfaces (contratos)
- **Adapters** = Implementações (tecnologias específicas)
- **Dependência** = SEMPRE aponta para DENTRO (para o hexágono)

---

## 2. Por Que Hexagonal e Não Camadas

### Problema com Arquitetura em Camadas Tradicional

```
❌ ARQUITETURA EM CAMADAS TRADICIONAL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌──────────────────────────────────────┐
│        PRESENTATION                  │  ← UI / REST API
├──────────────────────────────────────┤
│        BUSINESS LOGIC                │  ← Regras de negócio
├──────────────────────────────────────┤
│        DATA ACCESS                   │  ← Repository / DAO
├──────────────────────────────────────┤
│        DATABASE                      │  ← PostgreSQL
└──────────────────────────────────────┘

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ BUSINESS LOGIC DEPENDE DE DATA ACCESS
   └─ Business Logic conhece Repository (JPA)
   └─ Trocar banco? Business Logic muda! 💥

2. ❌ DIFÍCIL TESTAR
   └─ Testar Business Logic = precisa Data Access
   └─ Precisa banco de dados para teste!

3. ❌ LÓGICA VAZA PARA CAMADAS
   └─ Validação no Controller
   └─ Cálculo no Repository
   └─ Regra espalhada!

4. ❌ ACOPLAMENTO A TECNOLOGIAS
   └─ Business Logic usa anotações JPA
   └─ Business Logic usa classes do Spring
   └─ Impossível mudar framework!

5. ❌ FLUXO RÍGIDO (sempre de cima para baixo)
   └─ Presentation → Business → Data → DB
   └─ Não há como inverter!


✅ ARQUITETURA HEXAGONAL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        ┌─────────────────────┐
        │   REST API          │  ← Inbound Adapter
        │   (Controller)      │
        └──────────┬──────────┘
                   │ implementa
                   ↓
        ┌──────────────────────┐
        │   Inbound Port       │  ← Interface
        │   (Use Case)         │
        └──────────┬───────────┘
                   │ usa
                   ↓
   ┌───────────────────────────────────┐
   │         HEXÁGONO                  │
   │                                   │
   │   ┌───────────────────────┐       │
   │   │   APPLICATION         │       │
   │   │   (Use Case Service)  │       │
   │   └───────────┬───────────┘       │
   │               │                   │
   │               ↓ depende           │
   │   ┌───────────────────────┐       │
   │   │   DOMAIN              │       │
   │   │   (Entities, VOs)     │       │
   │   │   REGRAS DE NEGÓCIO   │       │
   │   │   (PURO!)             │       │
   │   └───────────────────────┘       │
   │                                   │
   └───────────────────────────────────┘
                   │ define
                   ↓
        ┌──────────────────────┐
        │   Outbound Port      │  ← Interface
        │   (Repository)       │
        └──────────┬───────────┘
                   ↑ implementa
        ┌──────────┴──────────┐
        │   JPA Adapter       │  ← Outbound Adapter
        │   (PostgreSQL)      │
        └─────────────────────┘

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ DOMAIN INDEPENDENTE
   └─ Domain NÃO conhece JPA, Spring, Kafka
   └─ Domain PURO (só Java + lógica de negócio)!

2. ✅ FÁCIL TESTAR
   └─ Testar Domain = ZERO dependências externas
   └─ Testes em milissegundos!

3. ✅ LÓGICA CENTRALIZADA
   └─ TODA regra de negócio no Domain
   └─ Zero lógica nos Adapters!

4. ✅ TECNOLOGIAS SUBSTITUÍVEIS
   └─ Trocar PostgreSQL → MongoDB? Só muda Adapter!
   └─ Domain e Application = INTACTOS!

5. ✅ FLUXO INVERTIDO (Dependency Inversion!)
   └─ Adapters dependem de Ports
   └─ Ports definidas pelo DOMÍNIO!


COMPARAÇÃO LADO A LADO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário: Trocar PostgreSQL por MongoDB

❌ CAMADAS:
   1. Mudar Data Access Layer ✏️
   2. Mudar Business Logic (conhece JPA!) ✏️
   3. Atualizar testes (quebram!) ✏️
   4. Rezar para não ter bugs 🙏
   RESULTADO: 3 camadas mudaram! 💥

✅ HEXAGONAL:
   1. Criar MongoAdapter (implementa Port) ✏️
   2. Configurar Spring para injetar novo Adapter ✏️
   3. FIM! ✅
   RESULTADO: Domain + Application = INTOCADOS! 🎉
```

---

## 3. Ports vs Adapters

### O que são Ports?

```
PORT (PORTA)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Interface (contrato) que define COMO o hexágono
  se comunica com o mundo externo.

CARACTERÍSTICAS:
  ✅ Definida DENTRO do hexágono (Domain/Application)
  ✅ Interface Java (abstração)
  ✅ Vocabulário do DOMÍNIO (não técnico)
  ✅ Não conhece tecnologia (JPA, Kafka, REST)

TIPOS:
  • Inbound Ports (Use Cases): QUEM usa o hexágono
  • Outbound Ports (Dependencies): O QUE o hexágono precisa

EXEMPLO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Inbound Port (Use Case)
package com.mvbr.store.application.port.in;

public interface ApprovePaymentUseCase {
    PaymentResponse execute(ApprovePaymentCommand command);
}

// ✅ Outbound Port (Dependency)
package com.mvbr.store.application.port.out;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId paymentId);
}

LINGUAGEM DO DOMÍNIO!
├─ ApprovePaymentUseCase (não "PaymentController")
├─ PaymentRepository (não "PaymentDAO" ou "PaymentJpaRepository")
└─ save(Payment) (não "persist(PaymentEntity)")
```

### O que são Adapters?

```
ADAPTER (ADAPTADOR)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Implementação CONCRETA de um Port, usando
  tecnologia específica (JPA, Kafka, REST, etc).

CARACTERÍSTICAS:
  ✅ Vive FORA do hexágono (Infrastructure)
  ✅ Classe concreta (implementação)
  ✅ Conhece tecnologia (Spring, JPA, Kafka)
  ✅ ADAPTA tecnologia para Port

TIPOS:
  • Inbound Adapters (Drivers): REST API, GraphQL, CLI
  • Outbound Adapters (Driven): JPA, Kafka, Redis, APIs externas

EXEMPLO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Inbound Adapter (REST)
package com.mvbr.store.infrastructure.adapter.in.rest;

@RestController
public class PaymentController {

    private final ApprovePaymentUseCase useCase;  // ← Port!

    @PostMapping("/api/payments/approve")
    public ResponseEntity<PaymentResponse> approve(
            @RequestBody ApprovePaymentRequest request) {

        ApprovePaymentCommand command = toCommand(request);
        PaymentResponse response = useCase.execute(command);
        return ResponseEntity.ok(response);
    }
}

// ✅ Outbound Adapter (JPA)
package com.mvbr.store.infrastructure.adapter.out.persistence;

@Repository
public class JpaPaymentRepository implements PaymentRepository {  // ← Port!

    private final PaymentJpaRepository jpaRepository;  // Spring Data
    private final PaymentMapper mapper;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapper.toEntity(payment);
        PaymentEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}

ADAPTA TECNOLOGIA PARA O DOMÍNIO!
├─ PaymentController adapta HTTP → Use Case
├─ JpaPaymentRepository adapta Port → JPA
└─ Hexágono NÃO sabe que HTTP ou JPA existem!
```

### Regra de Ouro: Dependências SEMPRE para Dentro

```
DEPENDENCY RULE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Adapters → Ports → Application → Domain

  ❌ Domain NÃO conhece Ports
  ❌ Domain NÃO conhece Adapters
  ❌ Application NÃO conhece Adapters
  ✅ Application conhece Domain
  ✅ Ports definidas por Application
  ✅ Adapters implementam Ports


DIAGRAMA DE DEPENDÊNCIAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌────────────────────────────────────────┐
│   ADAPTERS (Infrastructure)            │
│   - PaymentController.java             │
│   - JpaPaymentRepository.java          │
│   - KafkaEventPublisher.java           │
└─────────────┬──────────────────────────┘
              │ implementa (↓)
              ↓
┌────────────────────────────────────────┐
│   PORTS (Application)                  │
│   - ApprovePaymentUseCase.java         │
│   - PaymentRepository.java             │
│   - EventPublisher.java                │
└─────────────┬──────────────────────────┘
              │ usa (↓)
              ↓
┌────────────────────────────────────────┐
│   APPLICATION (Use Case Services)      │
│   - ApprovePaymentService.java         │
└─────────────┬──────────────────────────┘
              │ usa (↓)
              ↓
┌────────────────────────────────────────┐
│   DOMAIN (Entities, Value Objects)     │
│   - Payment.java (PURO!)               │
│   - Money.java                         │
│   - PaymentId.java                     │
└────────────────────────────────────────┘

✅ Dependências SEMPRE apontam para baixo!
✅ Domain não importa NADA de outras camadas!
```

---

## 4. Implementação Passo a Passo

### PASSO 1: Estrutura de Pastas

```
src/main/java/com/mvbr/store/
│
├── domain/                              ← CENTRO DO HEXÁGONO
│   └── model/
│       ├── payment/
│       │   ├── Payment.java             ← Entity (PURO!)
│       │   ├── PaymentId.java           ← Value Object
│       │   ├── PaymentStatus.java       ← Enum
│       │   └── Money.java               ← Value Object
│       └── ...
│
├── application/                         ← USE CASES (ainda no hexágono)
│   ├── port/
│   │   ├── in/                          ← INBOUND PORTS
│   │   │   └── ApprovePaymentUseCase.java
│   │   └── out/                         ← OUTBOUND PORTS
│   │       ├── PaymentRepository.java
│   │       └── EventPublisher.java
│   │
│   ├── service/                         ← IMPLEMENTAÇÃO DOS USE CASES
│   │   └── ApprovePaymentService.java
│   │
│   └── command/                         ← DTOs de entrada
│       └── ApprovePaymentCommand.java
│
└── infrastructure/                      ← ADAPTERS (FORA do hexágono)
    └── adapter/
        ├── in/                          ← INBOUND ADAPTERS
        │   └── rest/
        │       ├── PaymentController.java
        │       └── dto/
        │           ├── ApprovePaymentRequest.java
        │           └── PaymentResponse.java
        │
        └── out/                         ← OUTBOUND ADAPTERS
            ├── persistence/
            │   ├── JpaPaymentRepository.java
            │   ├── entity/
            │   │   └── PaymentEntity.java
            │   └── mapper/
            │       └── PaymentMapper.java
            │
            └── messaging/
                └── KafkaEventPublisher.java


REGRA DE OURO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ domain/       → Não importa NADA de outras pastas
✅ application/  → Só importa domain/
✅ infrastructure/ → Pode importar domain/ e application/
```

### PASSO 2: Criar Domain (Núcleo do Hexágono)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN - Payment.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.model.payment;

import java.time.Instant;

/**
 * Payment - DOMAIN ENTITY (PURO!).
 *
 * ✅ ZERO dependências de frameworks!
 * ✅ ZERO anotações (@Entity, @Table, @Column)!
 * ✅ ZERO conhecimento de infraestrutura!
 * ✅ SÓ regras de negócio PURAS!
 */
public class Payment {

    private final PaymentId paymentId;
    private final CustomerId customerId;
    private final Money amount;
    private PaymentStatus status;
    private final Instant createdAt;

    /**
     * Construtor com validações.
     */
    public Payment(PaymentId paymentId, CustomerId customerId, Money amount) {
        validatePaymentId(paymentId);
        validateCustomerId(customerId);
        validateAmount(amount);

        this.paymentId = paymentId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * ✅ Comportamento (regra de negócio).
     */
    public void approve() {
        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment: " + paymentId
            );
        }

        if (status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException(
                "Payment already approved: " + paymentId
            );
        }

        this.status = PaymentStatus.APPROVED;
    }

    /**
     * ✅ Validações (regras de domínio).
     */
    private void validatePaymentId(PaymentId paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("PaymentId cannot be null");
        }
    }

    private void validateCustomerId(CustomerId customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("CustomerId cannot be null");
        }
    }

    private void validateAmount(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.isNegativeOrZero()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    // Getters (SEM setters!)
    public PaymentId getPaymentId() { return paymentId; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN - Money.java (Value Object)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.model.payment;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Money - VALUE OBJECT (PURO!).
 */
public class Money {

    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        this.amount = amount;
        this.currency = currency;
    }

    public boolean isNegativeOrZero() {
        return amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException();
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Money money = (Money) obj;
        return amount.compareTo(money.amount) == 0 &&
               currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
}
```

### PASSO 3: Criar Ports (Interfaces)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INBOUND PORT - ApprovePaymentUseCase.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.port.in;

/**
 * ApprovePaymentUseCase - INBOUND PORT.
 *
 * Interface que define O QUE o hexágono FAZ.
 * (Quem CHAMA o hexágono)
 *
 * ✅ Vocabulário do DOMÍNIO (não técnico)!
 * ✅ Independente de tecnologia (REST, gRPC, etc)!
 */
public interface ApprovePaymentUseCase {

    /**
     * Aprovar um pagamento.
     *
     * @param command dados do comando
     * @return Payment aprovado
     */
    Payment execute(ApprovePaymentCommand command);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      OUTBOUND PORT - PaymentRepository.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.payment.Payment;
import com.mvbr.store.domain.model.payment.PaymentId;
import java.util.Optional;

/**
 * PaymentRepository - OUTBOUND PORT.
 *
 * Interface que define O QUE o hexágono PRECISA.
 * (O que o hexágono CHAMA)
 *
 * ✅ Definida pelo HEXÁGONO (Application Layer)!
 * ✅ Vocabulário do DOMÍNIO (save, não persist)!
 * ✅ Retorna Domain Models (Payment, não PaymentEntity)!
 */
public interface PaymentRepository {

    /**
     * Salvar payment.
     */
    Payment save(Payment payment);

    /**
     * Buscar por ID.
     */
    Optional<Payment> findById(PaymentId paymentId);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND - ApprovePaymentCommand.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.command;

import com.mvbr.store.domain.model.payment.PaymentId;
import com.mvbr.store.domain.model.payment.CustomerId;
import com.mvbr.store.domain.model.payment.Money;

/**
 * ApprovePaymentCommand - DTO de entrada (CQRS).
 *
 * Representa a INTENÇÃO de aprovar um pagamento.
 */
public record ApprovePaymentCommand(
    PaymentId paymentId,
    CustomerId customerId,
    Money amount
) {}
```

### PASSO 4: Implementar Use Case (Application Layer)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      USE CASE SERVICE - ApprovePaymentService.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.service;

import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.out.PaymentRepository;
import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.domain.model.payment.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ApprovePaymentService - IMPLEMENTAÇÃO do Use Case.
 *
 * ✅ Implementa Inbound Port (ApprovePaymentUseCase)
 * ✅ Usa Outbound Ports (PaymentRepository)
 * ✅ Orquestra lógica de aplicação
 * ✅ NÃO conhece Adapters (só Ports)!
 */
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepository paymentRepository;  // ← Outbound Port!

    public ApprovePaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public Payment execute(ApprovePaymentCommand command) {

        // 1. Criar Domain Model (validações executam aqui!)
        Payment payment = new Payment(
            command.paymentId(),
            command.customerId(),
            command.amount()
        );

        // 2. Executar lógica de negócio (Domain)
        payment.approve();

        // 3. Persistir usando Port (não sabe se é JPA, Mongo, etc!)
        Payment saved = paymentRepository.save(payment);

        return saved;
    }
}
```

### PASSO 5: Implementar Adapters (Infrastructure)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INBOUND ADAPTER - PaymentController.java (REST)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.in.rest;

import com.mvbr.store.application.port.in.ApprovePaymentUseCase;  // ← Port!
import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.domain.model.payment.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PaymentController - INBOUND ADAPTER (REST API).
 *
 * ✅ ADAPTA HTTP para Use Case!
 * ✅ Depende de Port (ApprovePaymentUseCase), não de Service!
 * ✅ Conhece tecnologia REST (mas hexágono não!)
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentUseCase approvePaymentUseCase;  // ← Port!

    public PaymentController(ApprovePaymentUseCase approvePaymentUseCase) {
        this.approvePaymentUseCase = approvePaymentUseCase;
    }

    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @RequestBody ApprovePaymentRequest request) {

        // 1. Adaptar Request → Command
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            new PaymentId(request.paymentId()),
            new CustomerId(request.customerId()),
            new Money(request.amount(), request.currency())
        );

        // 2. Chamar Use Case (através do Port!)
        Payment payment = approvePaymentUseCase.execute(command);

        // 3. Adaptar Payment → Response
        PaymentResponse response = PaymentResponse.from(payment);

        return ResponseEntity.ok(response);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      OUTBOUND ADAPTER - JpaPaymentRepository.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.application.port.out.PaymentRepository;  // ← Port!
import com.mvbr.store.domain.model.payment.Payment;
import com.mvbr.store.domain.model.payment.PaymentId;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * JpaPaymentRepository - OUTBOUND ADAPTER (JPA).
 *
 * ✅ IMPLEMENTA Outbound Port (PaymentRepository)!
 * ✅ ADAPTA Port para JPA/PostgreSQL!
 * ✅ Conhece tecnologia JPA (mas hexágono não!)
 */
@Repository
public class JpaPaymentRepository implements PaymentRepository {  // ← Implementa Port!

    private final PaymentJpaRepository jpaRepository;  // Spring Data JPA
    private final PaymentMapper mapper;

    public JpaPaymentRepository(PaymentJpaRepository jpaRepository,
                               PaymentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        // Adaptar Domain → Entity (JPA)
        PaymentEntity entity = mapper.toEntity(payment);

        // Salvar usando Spring Data JPA
        PaymentEntity saved = jpaRepository.save(entity);

        // Adaptar Entity → Domain
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return jpaRepository.findById(paymentId.getValue())
            .map(mapper::toDomain);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      JPA ENTITY - PaymentEntity.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentEntity - JPA Entity (INFRASTRUCTURE).
 *
 * ❌ Domain NÃO conhece esta classe!
 * ✅ Detalhe de implementação do Adapter!
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    // Construtores, getters, setters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      MAPPER - PaymentMapper.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence.mapper;

import com.mvbr.store.domain.model.payment.*;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;
import java.util.Currency;

/**
 * PaymentMapper - Converte Domain ↔ Entity.
 */
@Component
public class PaymentMapper {

    /**
     * Domain → Entity (para persistir).
     */
    public PaymentEntity toEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentId(payment.getPaymentId().getValue());
        entity.setCustomerId(payment.getCustomerId().getValue());
        entity.setAmount(payment.getAmount().getAmount());
        entity.setCurrency(payment.getAmount().getCurrency().getCurrencyCode());
        entity.setStatus(payment.getStatus().name());
        entity.setCreatedAt(payment.getCreatedAt());
        return entity;
    }

    /**
     * Entity → Domain (ao buscar).
     */
    public Payment toDomain(PaymentEntity entity) {
        PaymentId paymentId = new PaymentId(entity.getPaymentId());
        CustomerId customerId = new CustomerId(entity.getCustomerId());
        Money amount = new Money(
            entity.getAmount(),
            Currency.getInstance(entity.getCurrency())
        );

        Payment payment = new Payment(paymentId, customerId, amount);

        // Restaurar estado
        if ("APPROVED".equals(entity.getStatus())) {
            payment.approve();
        }

        return payment;
    }
}
```

---

## 5. Inbound vs Outbound

### Inbound Ports & Adapters (Quem USA o hexágono)

```
INBOUND (ENTRADA)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

QUEM CHAMA O HEXÁGONO?
  • REST API (Controller)
  • GraphQL (Resolver)
  • gRPC (Service)
  • CLI (Command Line)
  • Message Consumer (Kafka Consumer)
  • Scheduled Job (Cron)


FLUXO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. INBOUND ADAPTER recebe requisição externa
   └─ Exemplo: PaymentController recebe HTTP POST

2. ADAPTER converte para linguagem do DOMÍNIO
   └─ Exemplo: ApprovePaymentRequest → ApprovePaymentCommand

3. ADAPTER chama INBOUND PORT (Use Case)
   └─ Exemplo: approvePaymentUseCase.execute(command)

4. USE CASE executa lógica de negócio
   └─ Exemplo: payment.approve()

5. ADAPTER converte resposta para formato externo
   └─ Exemplo: Payment → PaymentResponse (JSON)


EXEMPLO COMPLETO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   ┌─────────────────────┐
   │   HTTP Request      │  ← Cliente
   └──────────┬──────────┘
              │
              ↓
   ┌──────────────────────┐
   │   REST Controller    │  ← Inbound Adapter
   │   (Infrastructure)   │
   └──────────┬───────────┘
              │ 1. Converte Request → Command
              │
              ↓
   ┌──────────────────────┐
   │   Use Case Port      │  ← Inbound Port (Interface)
   │   (Application)      │
   └──────────┬───────────┘
              │ 2. Executa
              ↓
   ┌──────────────────────┐
   │   Use Case Service   │  ← Implementação (Application)
   │   (Application)      │
   └──────────┬───────────┘
              │ 3. Chama Domain
              ↓
   ┌──────────────────────┐
   │   Domain Model       │  ← Regras de negócio
   │   (Domain)           │
   └──────────────────────┘


MÚLTIPLOS INBOUND ADAPTERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  REST API Adapter ──┐
                     │
  GraphQL Adapter ───┼──→ ApprovePaymentUseCase → Domain
                     │
  gRPC Adapter ──────┘

✅ MESMA lógica de negócio (Use Case)!
✅ DIFERENTES formas de entrada (Adapters)!
```

### Outbound Ports & Adapters (O que o hexágono PRECISA)

```
OUTBOUND (SAÍDA)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

O QUE O HEXÁGONO PRECISA?
  • Persistência (Database)
  • Mensageria (Kafka, RabbitMQ)
  • Cache (Redis)
  • APIs externas (Payment Gateway, Email Service)
  • Sistema de arquivos


FLUXO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. USE CASE precisa de algo externo
   └─ Exemplo: Precisa salvar Payment

2. USE CASE chama OUTBOUND PORT
   └─ Exemplo: paymentRepository.save(payment)

3. OUTBOUND ADAPTER implementa PORT
   └─ Exemplo: JpaPaymentRepository

4. ADAPTER converte Domain → Tecnologia
   └─ Exemplo: Payment → PaymentEntity

5. ADAPTER executa operação tecnológica
   └─ Exemplo: jpaRepository.save(entity)

6. ADAPTER converte Tecnologia → Domain
   └─ Exemplo: PaymentEntity → Payment


EXEMPLO COMPLETO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   ┌──────────────────────┐
   │   Use Case Service   │  ← Application
   │   (Application)      │
   └──────────┬───────────┘
              │ 1. Chama Port
              ↓
   ┌──────────────────────┐
   │   Repository Port    │  ← Outbound Port (Interface)
   │   (Application)      │
   └──────────┬───────────┘
              │ 2. Implementado por
              ↓
   ┌──────────────────────┐
   │   JPA Adapter        │  ← Outbound Adapter
   │   (Infrastructure)   │
   └──────────┬───────────┘
              │ 3. Converte Domain → Entity
              │
              ↓
   ┌──────────────────────┐
   │   Spring Data JPA    │  ← Tecnologia
   └──────────┬───────────┘
              │
              ↓
   ┌──────────────────────┐
   │   PostgreSQL         │  ← Banco de dados
   └──────────────────────┘


MÚLTIPLOS OUTBOUND ADAPTERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  PaymentRepository (Port)
         ↑
         │ implementado por
         │
    ┌────┴────┬────────┬─────────┐
    │         │        │         │
  JPA     MongoDB   Redis   InMemory
 Adapter   Adapter  Adapter  Adapter
    │         │        │         │
    ↓         ↓        ↓         ↓
PostgreSQL  Mongo    Redis    HashMap
                              (testes!)

✅ MESMA interface (Port)!
✅ DIFERENTES implementações (Adapters)!
✅ Troca fácil (configuração Spring)!
```

---

## 6. Testes com Hexagonal

### Vantagem: Testar SEM Infraestrutura

```java
/**
 * Teste de DOMAIN (sem nenhuma dependência).
 *
 * ✅ ZERO Spring
 * ✅ ZERO banco de dados
 * ✅ ZERO Kafka
 * ✅ POJO puro!
 * ✅ Roda em MILISSEGUNDOS!
 */
class PaymentTest {

    @Test
    @DisplayName("Should approve payment when status is PENDING")
    void shouldApprovePaymentWhenStatusIsPending() {
        // Given
        PaymentId paymentId = new PaymentId("pay-123");
        CustomerId customerId = new CustomerId("cust-456");
        Money amount = new Money(new BigDecimal("100.00"), Currency.USD);

        Payment payment = new Payment(paymentId, customerId, amount);

        // When
        payment.approve();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("Should not approve cancelled payment")
    void shouldNotApproveCancelledPayment() {
        // Given
        Payment payment = new Payment(...);
        payment.cancel();

        // When/Then
        assertThatThrownBy(() -> payment.approve())
            .isInstanceOf(PaymentAlreadyCancelledException.class);
    }
}


/**
 * Teste de USE CASE (com Fake Adapter).
 *
 * ✅ Testa lógica de aplicação
 * ✅ Usa Fake Repository (in-memory)
 * ✅ SEM banco real!
 * ✅ Roda RÁPIDO!
 */
class ApprovePaymentServiceTest {

    private PaymentRepository paymentRepository;  // ← Port!
    private ApprovePaymentService service;

    @BeforeEach
    void setUp() {
        // ✅ Fake Adapter (implementa Port!)
        paymentRepository = new FakePaymentRepository();
        service = new ApprovePaymentService(paymentRepository);
    }

    @Test
    @DisplayName("Should save payment after approval")
    void shouldSavePaymentAfterApproval() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(...);

        // When
        Payment approved = service.execute(command);

        // Then
        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        // Verificar que foi salvo (Fake Repository)
        Payment saved = paymentRepository.findById(approved.getPaymentId()).get();
        assertThat(saved).isNotNull();
    }
}


/**
 * Fake Repository (para testes).
 *
 * ✅ Implementa Port!
 * ✅ In-memory (HashMap)!
 * ✅ Zero dependências!
 */
class FakePaymentRepository implements PaymentRepository {

    private final Map<PaymentId, Payment> storage = new HashMap<>();

    @Override
    public Payment save(Payment payment) {
        storage.put(payment.getPaymentId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return Optional.ofNullable(storage.get(paymentId));
    }
}


/**
 * Teste de ADAPTER (integração real).
 *
 * ✅ Testa JpaPaymentRepository
 * ✅ Usa banco REAL (H2 in-memory)
 * ✅ Mais lento (mas necessário!)
 */
@DataJpaTest
class JpaPaymentRepositoryTest {

    @Autowired
    private PaymentJpaRepository jpaRepository;

    private PaymentMapper mapper = new PaymentMapper();
    private JpaPaymentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaPaymentRepository(jpaRepository, mapper);
    }

    @Test
    @DisplayName("Should save and retrieve payment")
    void shouldSaveAndRetrievePayment() {
        // Given
        Payment payment = new Payment(...);

        // When
        Payment saved = repository.save(payment);

        // Then
        Optional<Payment> found = repository.findById(saved.getPaymentId());
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo(saved.getPaymentId());
    }
}
```

---

## 7. Hexagonal no Dia a Dia

### Situação 1: Trocar Banco de Dados

```
CENÁRIO: Migrar PostgreSQL → MongoDB
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ SEM HEXAGONAL:
   1. Mudar Repository (SQL → Mongo) ✏️
   2. Mudar Service (conhece JPA!) ✏️
   3. Mudar Domain (tem @Entity!) ✏️
   4. Atualizar testes (quebram!) ✏️
   5. Rezar 🙏
   RESULTADO: 3-4 camadas mudaram! 💥


✅ COM HEXAGONAL:
   1. Criar MongoPaymentRepository (implementa Port) ✏️
   2. Atualizar Spring config (injetar novo Adapter) ✏️
   3. FIM! ✅
   RESULTADO: Domain + Application = INTOCADOS! 🎉


PASSO A PASSO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// 1. Criar Mongo Adapter (NOVO arquivo!)
@Repository
public class MongoPaymentRepository implements PaymentRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Payment save(Payment payment) {
        PaymentDocument doc = toDocument(payment);
        mongoTemplate.save(doc);
        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        PaymentDocument doc = mongoTemplate.findById(
            paymentId.getValue(),
            PaymentDocument.class
        );
        return Optional.ofNullable(doc).map(this::toDomain);
    }
}

// 2. Configurar Spring (application.yml)
spring:
  profiles:
    active: mongo  # ← Muda aqui!

// 3. Configuration (Spring decide qual injetar)
@Configuration
public class RepositoryConfig {

    @Bean
    @Profile("postgres")
    public PaymentRepository jpaRepository(
            PaymentJpaRepository jpaRepo,
            PaymentMapper mapper) {
        return new JpaPaymentRepository(jpaRepo, mapper);
    }

    @Bean
    @Profile("mongo")
    public PaymentRepository mongoRepository(MongoTemplate mongo) {
        return new MongoPaymentRepository(mongo);
    }
}

// ✅ Domain NÃO mudou!
// ✅ Application NÃO mudou!
// ✅ Use Case NÃO mudou!
// ✅ APENAS Adapter mudou!
```

### Situação 2: Adicionar Nova Interface (gRPC)

```
CENÁRIO: Adicionar gRPC mantendo REST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ COM HEXAGONAL:
   1. Criar gRPC Adapter (NOVO arquivo!) ✏️
   2. FIM! ✅
   RESULTADO: Use Case reutilizado! 🎉


// 1. Criar gRPC Adapter
@GrpcService
public class PaymentGrpcService
        extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final ApprovePaymentUseCase useCase;  // ← MESMO Use Case!

    @Override
    public void approvePayment(
            ApprovePaymentRequest request,
            StreamObserver<PaymentResponse> responseObserver) {

        // Adaptar gRPC → Command
        ApprovePaymentCommand command = new ApprovePaymentCommand(...);

        // ✅ Chamar MESMO Use Case que REST usa!
        Payment payment = useCase.execute(command);

        // Adaptar Payment → gRPC Response
        PaymentResponse response = toGrpcResponse(payment);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

// ✅ Use Case reutilizado (REST + gRPC)!
// ✅ Domain reutilizado!
// ✅ ZERO duplicação!
```

### Situação 3: Testar SEM Infraestrutura

```
CENÁRIO: Testar lógica de negócio rapidamente
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ COM HEXAGONAL:

// 1. Criar Fake Adapter (in-memory)
class FakePaymentRepository implements PaymentRepository {
    private Map<PaymentId, Payment> storage = new HashMap<>();

    @Override
    public Payment save(Payment payment) {
        storage.put(payment.getPaymentId(), payment);
        return payment;
    }
}

// 2. Testar Use Case SEM banco real
@Test
void shouldApprovePayment() {
    // ✅ Fake Adapter (ZERO banco de dados!)
    PaymentRepository repo = new FakePaymentRepository();
    ApprovePaymentService service = new ApprovePaymentService(repo);

    // Test...
    Payment approved = service.execute(command);

    assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
}

// ✅ Teste roda em MILISSEGUNDOS!
// ✅ CI/CD rápido!
// ✅ Feedback imediato!
```

---

## 8. Armadilhas Comuns

### ❌ Armadilha 1: Domain Conhece Adapter

```java
// ❌ ERRADO - Domain importa Infrastructure

package com.mvbr.store.domain.model.payment;

import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;  // ❌ ERRO!

public class Payment {
    // ❌ Domain conhece JPA Entity!
    public PaymentEntity toEntity() {
        return new PaymentEntity(...);
    }
}

POR QUE ESTÁ ERRADO?
├─ Domain agora depende de Infrastructure!
├─ Trocar JPA por Mongo = quebra Domain!
└─ Violação da Dependency Rule!


// ✅ CORRETO - Adapter converte Domain

package com.mvbr.store.infrastructure.adapter.out.persistence.mapper;

public class PaymentMapper {
    // ✅ ADAPTER converte (não Domain!)
    public PaymentEntity toEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentId(payment.getPaymentId().getValue());
        // ...
        return entity;
    }
}
```

### ❌ Armadilha 2: Use Case Conhece Adapter Concreto

```java
// ❌ ERRADO - Use Case depende de Adapter concreto

package com.mvbr.store.application.service;

import com.mvbr.store.infrastructure.adapter.out.persistence.JpaPaymentRepository;  // ❌ ERRO!

public class ApprovePaymentService {

    private final JpaPaymentRepository repository;  // ❌ Adapter concreto!

    public ApprovePaymentService(JpaPaymentRepository repository) {
        this.repository = repository;
    }
}

POR QUE ESTÁ ERRADO?
├─ Use Case conhece implementação concreta (JPA)!
├─ Trocar Mongo? Use Case precisa mudar!
└─ Impossível testar com Fake!


// ✅ CORRETO - Use Case depende de Port

package com.mvbr.store.application.service;

import com.mvbr.store.application.port.out.PaymentRepository;  // ✅ Port!

public class ApprovePaymentService {

    private final PaymentRepository repository;  // ✅ Interface!

    public ApprovePaymentService(PaymentRepository repository) {
        this.repository = repository;
    }
}
```

### ❌ Armadilha 3: Port Retorna Tipo de Infraestrutura

```java
// ❌ ERRADO - Port retorna PaymentEntity (JPA)

package com.mvbr.store.application.port.out;

import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;  // ❌ ERRO!

public interface PaymentRepository {
    PaymentEntity save(PaymentEntity entity);  // ❌ Tipo de Infrastructure!
}

POR QUE ESTÁ ERRADO?
├─ Port conhece detalhe de implementação (PaymentEntity)!
├─ Application depende de Infrastructure!
└─ Violação da Dependency Rule!


// ✅ CORRETO - Port retorna Domain Model

package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.payment.Payment;  // ✅ Domain!

public interface PaymentRepository {
    Payment save(Payment payment);  // ✅ Domain Model!
}
```

---

## 9. Checklist Hexagonal

```
ANTES DE IMPLEMENTAR:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Identificou o DOMÍNIO (regras de negócio)?
☐ Domínio está ISOLADO (zero frameworks)?
☐ Definiu os PORTS (interfaces)?
☐ Ports estão no VOCABULÁRIO do domínio?
☐ Identificou Inbound vs Outbound?

ESTRUTURA DE PASTAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ domain/ (não importa NADA)
☐ application/port/in/ (Inbound Ports)
☐ application/port/out/ (Outbound Ports)
☐ application/service/ (Use Case Services)
☐ infrastructure/adapter/in/ (Inbound Adapters)
☐ infrastructure/adapter/out/ (Outbound Adapters)

DOMAIN:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Entities têm comportamento (não apenas getters/setters)
☐ Value Objects são imutáveis
☐ Zero anotações de framework (@Entity, @Table, etc)
☐ Zero imports de infrastructure
☐ Validações no construtor

PORTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Interfaces (não classes concretas)
☐ Vocabulário do domínio (save, não persist)
☐ Retornam Domain Models (não Entities JPA)
☐ Definidas por Application (não Infrastructure)

ADAPTERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Implementam Ports
☐ Conhecem tecnologias (JPA, Kafka, REST)
☐ Convertem Domain ↔ Tecnologia
☐ NÃO têm lógica de negócio

TESTES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Domain testado SEM frameworks
☐ Use Case testado com Fake Adapters
☐ Adapters testados com tecnologia real
☐ Testes rápidos (domínio em milissegundos)
```

---

## 10. Exercícios Práticos

### Exercício 1: Identificar Violações

Encontre as violações da Arquitetura Hexagonal neste código:

```java
// Domain
package com.example.domain;

import javax.persistence.Entity;  // ❓ Violação?
import javax.persistence.Id;

@Entity  // ❓ Violação?
public class Product {
    @Id
    private String productId;
    private BigDecimal price;

    // getters e setters...
}

// Use Case
package com.example.application;

import com.example.infrastructure.JpaProductRepository;  // ❓ Violação?

public class UpdatePriceService {

    private final JpaProductRepository repository;  // ❓ Violação?

    public void updatePrice(String productId, BigDecimal newPrice) {
        Product product = repository.findById(productId);  // ❓ Violação?
        product.setPrice(newPrice);
        repository.save(product);
    }
}
```

**Dica:** Há pelo menos 4 violações!

### Exercício 2: Refatorar para Hexagonal

Refatore este código para Arquitetura Hexagonal:

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;  // Spring Data JPA

    public void placeOrder(OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setItems(request.getItems());
        order.setStatus("PLACED");

        orderRepository.save(order);

        // Enviar email
        EmailService.send(order.getCustomerId(), "Order placed!");
    }
}
```

**Tarefas:**
1. Criar Domain Model (Order)
2. Criar Inbound Port (PlaceOrderUseCase)
3. Criar Outbound Ports (OrderRepository, EmailSender)
4. Criar Use Case Service
5. Criar Adapters

### Exercício 3: Adicionar Novo Adapter

Dado este Port:

```java
public interface PaymentGateway {
    PaymentResult process(PaymentRequest request);
}
```

Crie 2 Adapters:
1. `StripePaymentGateway` (integração com Stripe)
2. `FakePaymentGateway` (para testes)

---

## Conclusão

Parabéns! 🎉 Você domina Arquitetura Hexagonal!

**O que você aprendeu:**
✅ Conceitos fundamentais (Hexágono, Ports, Adapters)
✅ Por que Hexagonal é melhor que Camadas
✅ Ports (Inbound e Outbound)
✅ Adapters (Drivers e Driven)
✅ Dependency Rule (sempre para dentro)
✅ Testes isolados e rápidos
✅ Trocar tecnologias sem dor

**Lembre-se:**
> "Arquitetura Hexagonal protege seu domínio de mudanças tecnológicas.
> Tecnologias mudam, negócio permanece."

**Próximos passos:**
1. Refatore código existente para Hexagonal
2. Crie Fake Adapters para testes rápidos
3. Experimente trocar Adapters (JPA → Mongo)
4. Leia: "Hexagonal Architecture" (Alistair Cockburn)

🚀 Agora construa software resiliente a mudanças com Arquitetura Hexagonal!
