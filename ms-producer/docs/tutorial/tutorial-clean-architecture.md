# Tutorial Definitivo: Clean Architecture em Produção

---

## 📋 Sumário

1. [O que é Clean Architecture](#1-o-que-é-clean-architecture)
2. [Por Que Clean Architecture vs Arquitetura em Camadas](#2-por-que-clean-architecture-vs-arquitetura-em-camadas)
3. [Os 4 Círculos Concêntricos](#3-os-4-círculos-concêntricos)
4. [Implementação Passo a Passo](#4-implementação-passo-a-passo)
5. [A Regra de Dependência](#5-a-regra-de-dependência)
6. [Testando com Clean Architecture](#6-testando-com-clean-architecture)
7. [Cenários do Dia a Dia](#7-cenários-do-dia-a-dia)
8. [Armadilhas Comuns](#8-armadilhas-comuns)
9. [Checklist Clean Architecture](#9-checklist-clean-architecture)
10. [Exercícios Práticos](#10-exercícios-práticos)

---

## 1. O que é Clean Architecture

### Definição em 30 Segundos

**Clean Architecture** (criada por Uncle Bob) é um padrão arquitetural baseado em **círculos concêntricos** onde:

- O **DOMÍNIO** está no **CENTRO** (círculo interno)
- As **TECNOLOGIAS** estão na **PERIFERIA** (círculo externo)
- A **DEPENDÊNCIA** aponta SEMPRE **DE FORA PARA DENTRO**

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│              CLEAN ARCHITECTURE - VISÃO GERAL                │
│                                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │                                                    │     │
│  │  Frameworks & Drivers (UI, DB, Kafka, etc)        │     │
│  │                   ⬇ depende de ⬇                  │     │
│  │  ┌──────────────────────────────────────────┐     │     │
│  │  │                                          │     │     │
│  │  │  Interface Adapters (Controllers, etc)  │     │     │
│  │  │             ⬇ depende de ⬇              │     │     │
│  │  │  ┌────────────────────────────────┐     │     │     │
│  │  │  │                                │     │     │     │
│  │  │  │  Use Cases (Regras da App)     │     │     │     │
│  │  │  │       ⬇ depende de ⬇          │     │     │     │
│  │  │  │  ┌──────────────────────┐      │     │     │     │
│  │  │  │  │                      │      │     │     │     │
│  │  │  │  │  Entities            │      │     │     │     │
│  │  │  │  │  (Regras de Negócio) │      │     │     │     │
│  │  │  │  │                      │      │     │     │     │
│  │  │  │  └──────────────────────┘      │     │     │     │
│  │  │  │                                │     │     │     │
│  │  │  └────────────────────────────────┘     │     │     │
│  │  │                                          │     │     │
│  │  └──────────────────────────────────────────┘     │     │
│  │                                                    │     │
│  └────────────────────────────────────────────────────┘     │
│                                                              │
│  REGRA DE OURO:                                              │
│  ➡️  Dependências apontam SEMPRE para DENTRO                 │
│  ➡️  Círculo interno NÃO conhece círculo externo             │
│  ➡️  Frameworks não ditam a arquitetura                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**Conceitos-chave:**

- **Entities** = Regras de negócio UNIVERSAIS (valem em qualquer sistema)
- **Use Cases** = Regras específicas DESTA aplicação
- **Interface Adapters** = Tradutores (HTTP → Use Cases, Use Cases → DB)
- **Frameworks & Drivers** = Tecnologias (Spring, JPA, Kafka, REST)

**Em português claro:**

Clean Architecture organiza o código em círculos, onde o **mais importante** (regras de negócio) fica no **centro**, e o **menos importante** (tecnologias) fica na **borda**.

---

## 2. Por Que Clean Architecture vs Arquitetura em Camadas

### Comparação Lado a Lado

#### ❌ ARQUITETURA EM CAMADAS TRADICIONAL

```
┌────────────────────────────────────────────────────────────┐
│                     PRESENTATION                           │
│              (Controllers, REST, GraphQL)                  │
└────────────────────┬───────────────────────────────────────┘
                     │ depende de ⬇
┌────────────────────▼───────────────────────────────────────┐
│                       BUSINESS                             │
│                 (Services, Use Cases)                      │
└────────────────────┬───────────────────────────────────────┘
                     │ depende de ⬇
┌────────────────────▼───────────────────────────────────────┐
│                    DATA ACCESS                             │
│             (Repositories, JPA, SQL)                       │
└────────────────────────────────────────────────────────────┘
```

**PROBLEMAS:**

1. ❌ **BUSINESS depende de DATA ACCESS**
   - Se mudar o banco (PostgreSQL → MongoDB), o Business quebra
   - Não consegue testar Business sem banco

2. ❌ **LÓGICA DE NEGÓCIO VAZA para camadas**
   - Controllers fazem validações
   - Repositories fazem cálculos
   - Lógica espalhada por todo canto

3. ❌ **FRAMEWORK DITA A ARQUITETURA**
   - Spring @Service, @Repository, @Entity por todo lado
   - Código acoplado ao framework

4. ❌ **DIFÍCIL TROCAR TECNOLOGIAS**
   - Quer trocar REST por gRPC? Reescreve tudo
   - Quer trocar JPA por MongoDB? Reescreve tudo

**Exemplo do problema:**

```java
// ❌ BUSINESS LAYER ACOPLADO A DATA ACCESS
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository; // ← JPA Repository

    public void approvePayment(Long paymentId) {
        // ❌ Service depende de JPA Entity
        PaymentEntity entity = paymentRepository.findById(paymentId)
            .orElseThrow();

        entity.setStatus("APPROVED"); // ❌ Lógica na camada de dados
        paymentRepository.save(entity);
    }
}
```

---

#### ✅ CLEAN ARCHITECTURE

```
┌────────────────────────────────────────────────────────────┐
│                 FRAMEWORKS & DRIVERS                       │
│              (Spring, JPA, Kafka, REST)                    │
└────────────────────┬───────────────────────────────────────┘
                     │ depende de ⬇
┌────────────────────▼───────────────────────────────────────┐
│              INTERFACE ADAPTERS                            │
│        (Controllers, Presenters, Gateways)                 │
└────────────────────┬───────────────────────────────────────┘
                     │ depende de ⬇
┌────────────────────▼───────────────────────────────────────┐
│                    USE CASES                               │
│            (Regras da Aplicação - Ports)                   │
└────────────────────┬───────────────────────────────────────┘
                     │ depende de ⬇
┌────────────────────▼───────────────────────────────────────┐
│                     ENTITIES                               │
│              (Regras de Negócio Puras)                     │
└────────────────────────────────────────────────────────────┘
```

**BENEFÍCIOS:**

1. ✅ **BUSINESS INDEPENDENTE de TECNOLOGIAS**
   - Entities não conhecem Spring, JPA, Kafka
   - Use Cases não conhecem HTTP, JSON, SQL

2. ✅ **LÓGICA CENTRALIZADA no DOMÍNIO**
   - Toda regra de negócio vive nas Entities
   - Use Cases apenas orquestram

3. ✅ **FRAMEWORK é um DETALHE**
   - Spring pode ser trocado
   - JPA pode ser trocado
   - Código de negócio não muda

4. ✅ **FÁCIL TROCAR TECNOLOGIAS**
   - Trocar REST por gRPC? Só o Adapter muda
   - Trocar PostgreSQL por MongoDB? Só o Gateway muda

**Exemplo da solução:**

```java
// ✅ ENTITY - ZERO dependências
public class Payment {
    private final PaymentId paymentId;
    private PaymentStatus status;

    // ✅ LÓGICA DE NEGÓCIO centralizada
    public void approve() {
        if (this.status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment"
            );
        }
        this.status = PaymentStatus.APPROVED;
    }
}

// ✅ USE CASE - depende SÓ de abstrações (Ports)
public class ApprovePaymentUseCase {
    private final PaymentGateway paymentGateway; // ← Interface!

    public Payment execute(ApprovePaymentCommand command) {
        Payment payment = Payment.create(command);
        payment.approve(); // ← Lógica na Entity
        return paymentGateway.save(payment);
    }
}

// ✅ GATEWAY - implementa a abstração
@Repository
public class JpaPaymentGateway implements PaymentGateway {
    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        jpaRepository.save(entity);
        return payment;
    }
}
```

---

### Tabela Comparativa

| Aspecto | Arquitetura em Camadas | Clean Architecture |
|---------|------------------------|-------------------|
| **Dependência** | Business → Data Access | Frameworks → Adapters → Use Cases → Entities |
| **Lógica de Negócio** | Espalhada (Service, Repository) | Centralizada (Entities) |
| **Acoplamento** | Alto (Spring, JPA por todo lado) | Baixo (Domain puro) |
| **Testabilidade** | Difícil (precisa de banco) | Fácil (testa Entities puras) |
| **Troca de Framework** | Reescreve tudo | Troca só Adapters |
| **Troca de UI** | Reescreve Service | Troca só Controller |
| **Troca de DB** | Reescreve Business | Troca só Gateway |

---

## 3. Os 4 Círculos Concêntricos

### Círculo 1: Entities (Centro)

**O QUE É:**
- Regras de negócio **UNIVERSAIS**
- Valem em **QUALQUER** sistema da empresa
- **ZERO** dependências de frameworks

**CARACTERÍSTICAS:**
- Classes Java puras (POJOs)
- Sem anotações (@Entity, @Table, @Column)
- Sem dependência de Spring, JPA, Kafka
- Comportamento rico (métodos de negócio)

**EXEMPLO:**

```java
package com.mvbr.store.domain.entity;

// ✅ Entity PURA - sem anotações, sem frameworks
public class Payment {

    private final PaymentId paymentId;
    private final CustomerId customerId;
    private final Money amount;
    private PaymentStatus status;
    private final LocalDateTime createdAt;

    // ✅ Construtor com validações
    public Payment(
            PaymentId paymentId,
            CustomerId customerId,
            Money amount
    ) {
        if (amount.isNegativeOrZero()) {
            throw new InvalidPaymentException("Amount must be positive");
        }

        this.paymentId = paymentId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // ✅ REGRA DE NEGÓCIO: aprovar pagamento
    public void approve() {
        if (this.status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment: " + paymentId
            );
        }
        this.status = PaymentStatus.APPROVED;
    }

    // ✅ REGRA DE NEGÓCIO: cancelar pagamento
    public void cancel() {
        if (this.status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException(
                "Cannot cancel approved payment: " + paymentId
            );
        }
        this.status = PaymentStatus.CANCELLED;
    }

    // ✅ REGRA DE NEGÓCIO: verificar se é válido
    public boolean isValid() {
        return amount.isPositive() && status != PaymentStatus.CANCELLED;
    }

    // Getters (sem setters - imutabilidade)
    public PaymentId getPaymentId() { return paymentId; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

---

### Círculo 2: Use Cases (Aplicação)

**O QUE É:**
- Regras de negócio **ESPECÍFICAS** desta aplicação
- Orquestram Entities
- Definem **PORTS** (contratos/interfaces)

**CARACTERÍSTICAS:**
- Classes de serviço (mas SEM @Service do Spring!)
- Dependem APENAS de interfaces (Ports)
- Coordenam Entities
- Retornam Domain Models

**EXEMPLO:**

```java
package com.mvbr.store.application.usecase;

// ✅ USE CASE - orquestra Entities
public class ApprovePaymentUseCase {

    // ✅ Depende de ABSTRAÇÃO (Port)
    private final PaymentGateway paymentGateway;
    private final NotificationGateway notificationGateway;

    public ApprovePaymentUseCase(
            PaymentGateway paymentGateway,
            NotificationGateway notificationGateway
    ) {
        this.paymentGateway = paymentGateway;
        this.notificationGateway = notificationGateway;
    }

    // ✅ Executa o caso de uso
    public Payment execute(ApprovePaymentCommand command) {

        // 1. Criar Entity
        Payment payment = Payment.create(
            command.paymentId(),
            command.customerId(),
            command.amount()
        );

        // 2. Executar regra de negócio (na Entity!)
        payment.approve();

        // 3. Persistir via Gateway
        Payment savedPayment = paymentGateway.save(payment);

        // 4. Notificar via Gateway
        notificationGateway.sendPaymentApproved(savedPayment);

        return savedPayment;
    }
}
```

**PORTS (Interfaces):**

```java
package com.mvbr.store.application.port;

// ✅ PORT - contrato abstrato
public interface PaymentGateway {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId paymentId);
    List<Payment> findByCustomerId(CustomerId customerId);
}

// ✅ PORT - contrato abstrato
public interface NotificationGateway {
    void sendPaymentApproved(Payment payment);
    void sendPaymentCancelled(Payment payment);
}
```

---

### Círculo 3: Interface Adapters

**O QUE É:**
- **TRADUTORES** entre Use Cases e Frameworks
- Convertem dados: HTTP → Use Cases, Use Cases → DB
- Implementam os **PORTS**

**TIPOS:**

1. **Controllers** (Inbound Adapters)
   - Recebem requisições (HTTP, gRPC, CLI)
   - Convertem para Commands
   - Chamam Use Cases

2. **Presenters** (Outbound Adapters - Resposta)
   - Convertem Domain Models em DTOs
   - Formatam respostas (JSON, XML, GraphQL)

3. **Gateways** (Outbound Adapters - Persistência)
   - Implementam Ports de persistência
   - Convertem Domain Models em Entities JPA
   - Salvam no banco

**EXEMPLO - CONTROLLER:**

```java
package com.mvbr.store.adapter.in.web;

// ✅ CONTROLLER - Adapter Inbound
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentUseCase approvePaymentUseCase;

    public PaymentController(ApprovePaymentUseCase approvePaymentUseCase) {
        this.approvePaymentUseCase = approvePaymentUseCase;
    }

    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @RequestBody @Valid ApprovePaymentRequest request
    ) {
        // 1. Converter Request → Command
        ApprovePaymentCommand command = ApprovePaymentCommand.from(request);

        // 2. Executar Use Case
        Payment payment = approvePaymentUseCase.execute(command);

        // 3. Converter Domain Model → Response
        PaymentResponse response = PaymentResponse.from(payment);

        return ResponseEntity.ok(response);
    }
}
```

**EXEMPLO - GATEWAY:**

```java
package com.mvbr.store.adapter.out.persistence;

// ✅ GATEWAY - Adapter Outbound (implementa Port)
@Repository
public class JpaPaymentGateway implements PaymentGateway {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentMapper mapper;

    public JpaPaymentGateway(
            PaymentJpaRepository jpaRepository,
            PaymentMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        // 1. Converter Domain Model → JPA Entity
        PaymentEntity entity = mapper.toEntity(payment);

        // 2. Salvar no banco
        PaymentEntity savedEntity = jpaRepository.save(entity);

        // 3. Converter JPA Entity → Domain Model
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return jpaRepository.findById(paymentId.getValue())
            .map(mapper::toDomain);
    }
}
```

---

### Círculo 4: Frameworks & Drivers

**O QUE É:**
- Tecnologias específicas (Spring, JPA, Kafka, PostgreSQL)
- Configurações de frameworks
- Detalhes de infraestrutura

**EXEMPLOS:**

1. **Spring Configuration:**

```java
package com.mvbr.store.config;

@Configuration
public class UseCaseConfig {

    @Bean
    public ApprovePaymentUseCase approvePaymentUseCase(
            PaymentGateway paymentGateway,
            NotificationGateway notificationGateway
    ) {
        return new ApprovePaymentUseCase(
            paymentGateway,
            notificationGateway
        );
    }
}
```

2. **JPA Repository:**

```java
package com.mvbr.store.adapter.out.persistence;

// ✅ Framework específico (Spring Data JPA)
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findByCustomerId(UUID customerId);
}
```

---

## 4. Implementação Passo a Passo

### Estrutura de Pastas

```
src/main/java/com/mvbr/store/
│
├── domain/                        ← CÍRCULO 1: ENTITIES
│   ├── entity/
│   │   ├── Payment.java          ← Entity rica
│   │   └── Customer.java
│   ├── valueobject/
│   │   ├── PaymentId.java        ← Value Object
│   │   ├── CustomerId.java
│   │   ├── Money.java
│   │   └── PaymentStatus.java
│   └── exception/
│       ├── PaymentAlreadyCancelledException.java
│       └── InvalidPaymentException.java
│
├── application/                   ← CÍRCULO 2: USE CASES
│   ├── usecase/
│   │   ├── ApprovePaymentUseCase.java
│   │   ├── CancelPaymentUseCase.java
│   │   └── GetPaymentUseCase.java
│   ├── port/
│   │   ├── PaymentGateway.java   ← Port (Interface)
│   │   └── NotificationGateway.java
│   └── command/
│       ├── ApprovePaymentCommand.java
│       └── CancelPaymentCommand.java
│
├── adapter/                       ← CÍRCULO 3: INTERFACE ADAPTERS
│   ├── in/                        ← Inbound Adapters
│   │   └── web/
│   │       ├── PaymentController.java
│   │       ├── dto/
│   │       │   ├── ApprovePaymentRequest.java
│   │       │   └── PaymentResponse.java
│   │       └── mapper/
│   │           └── PaymentWebMapper.java
│   │
│   └── out/                       ← Outbound Adapters
│       ├── persistence/
│       │   ├── JpaPaymentGateway.java      ← Implementa Port
│       │   ├── PaymentJpaRepository.java   ← Spring Data JPA
│       │   ├── entity/
│       │   │   └── PaymentEntity.java      ← JPA Entity
│       │   └── mapper/
│       │       └── PaymentMapper.java
│       └── messaging/
│           ├── KafkaNotificationGateway.java
│           └── event/
│               └── PaymentApprovedEvent.java
│
└── config/                        ← CÍRCULO 4: FRAMEWORKS & DRIVERS
    ├── UseCaseConfig.java         ← Configura Use Cases
    ├── KafkaConfig.java
    └── DatabaseConfig.java
```

---

### Passo 1: Domain (Entities)

**Crie as Entities PURAS (sem frameworks):**

```java
// ✅ src/main/java/com/mvbr/store/domain/entity/Payment.java
package com.mvbr.store.domain.entity;

public class Payment {

    private final PaymentId paymentId;
    private final CustomerId customerId;
    private final Money amount;
    private PaymentStatus status;
    private final LocalDateTime createdAt;

    // Construtor
    public Payment(
            PaymentId paymentId,
            CustomerId customerId,
            Money amount
    ) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();

        validate();
    }

    // ✅ REGRA DE NEGÓCIO
    public void approve() {
        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment: " + paymentId
            );
        }
        this.status = PaymentStatus.APPROVED;
    }

    // ✅ REGRA DE NEGÓCIO
    public void cancel() {
        if (status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException(
                "Cannot cancel approved payment: " + paymentId
            );
        }
        this.status = PaymentStatus.CANCELLED;
    }

    // ✅ VALIDAÇÃO DE NEGÓCIO
    private void validate() {
        if (amount.isNegativeOrZero()) {
            throw new InvalidPaymentException(
                "Payment amount must be positive: " + amount
            );
        }
    }

    // ✅ REGRA DE NEGÓCIO
    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    // Getters
    public PaymentId getPaymentId() { return paymentId; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**Value Objects:**

```java
// ✅ src/main/java/com/mvbr/store/domain/valueobject/Money.java
package com.mvbr.store.domain.valueobject;

public record Money(BigDecimal value) {

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("Money value cannot be null");
        }
    }

    public boolean isPositive() {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegativeOrZero() {
        return value.compareTo(BigDecimal.ZERO) <= 0;
    }

    public Money add(Money other) {
        return new Money(this.value.add(other.value));
    }
}
```

---

### Passo 2: Application (Use Cases + Ports)

**Defina os PORTS (Interfaces):**

```java
// ✅ src/main/java/com/mvbr/store/application/port/PaymentGateway.java
package com.mvbr.store.application.port;

public interface PaymentGateway {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId paymentId);
    List<Payment> findByCustomerId(CustomerId customerId);
}
```

**Crie os USE CASES:**

```java
// ✅ src/main/java/com/mvbr/store/application/usecase/ApprovePaymentUseCase.java
package com.mvbr.store.application.usecase;

public class ApprovePaymentUseCase {

    private final PaymentGateway paymentGateway;
    private final NotificationGateway notificationGateway;

    public ApprovePaymentUseCase(
            PaymentGateway paymentGateway,
            NotificationGateway notificationGateway
    ) {
        this.paymentGateway = paymentGateway;
        this.notificationGateway = notificationGateway;
    }

    public Payment execute(ApprovePaymentCommand command) {

        // 1. Criar Entity
        Payment payment = new Payment(
            command.paymentId(),
            command.customerId(),
            command.amount()
        );

        // 2. Executar regra de negócio
        payment.approve();

        // 3. Salvar via Port
        Payment savedPayment = paymentGateway.save(payment);

        // 4. Notificar via Port
        notificationGateway.sendPaymentApproved(savedPayment);

        return savedPayment;
    }
}
```

**Commands:**

```java
// ✅ src/main/java/com/mvbr/store/application/command/ApprovePaymentCommand.java
package com.mvbr.store.application.command;

public record ApprovePaymentCommand(
    PaymentId paymentId,
    CustomerId customerId,
    Money amount
) {}
```

---

### Passo 3: Adapters Inbound (Controllers)

**REST Controller:**

```java
// ✅ src/main/java/com/mvbr/store/adapter/in/web/PaymentController.java
package com.mvbr.store.adapter.in.web;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentUseCase approvePaymentUseCase;

    public PaymentController(ApprovePaymentUseCase approvePaymentUseCase) {
        this.approvePaymentUseCase = approvePaymentUseCase;
    }

    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @RequestBody @Valid ApprovePaymentRequest request
    ) {
        // 1. Request → Command
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            new PaymentId(request.paymentId()),
            new CustomerId(request.customerId()),
            new Money(request.amount())
        );

        // 2. Executar Use Case
        Payment payment = approvePaymentUseCase.execute(command);

        // 3. Domain → Response
        PaymentResponse response = new PaymentResponse(
            payment.getPaymentId().getValue(),
            payment.getCustomerId().getValue(),
            payment.getAmount().value(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}
```

**DTOs:**

```java
// ✅ src/main/java/com/mvbr/store/adapter/in/web/dto/ApprovePaymentRequest.java
package com.mvbr.store.adapter.in.web.dto;

public record ApprovePaymentRequest(
    @NotNull UUID paymentId,
    @NotNull UUID customerId,
    @NotNull @Positive BigDecimal amount
) {}
```

---

### Passo 4: Adapters Outbound (Gateways)

**JPA Gateway (implementa Port):**

```java
// ✅ src/main/java/com/mvbr/store/adapter/out/persistence/JpaPaymentGateway.java
package com.mvbr.store.adapter.out.persistence;

@Repository
public class JpaPaymentGateway implements PaymentGateway {

    private final PaymentJpaRepository jpaRepository;

    public JpaPaymentGateway(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        // Domain → JPA Entity
        PaymentEntity entity = new PaymentEntity(
            payment.getPaymentId().getValue(),
            payment.getCustomerId().getValue(),
            payment.getAmount().value(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );

        // Salvar
        jpaRepository.save(entity);

        // Retornar Domain
        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return jpaRepository.findById(paymentId.getValue())
            .map(entity -> new Payment(
                new PaymentId(entity.getId()),
                new CustomerId(entity.getCustomerId()),
                new Money(entity.getAmount())
            ));
    }
}
```

**JPA Entity (infraestrutura):**

```java
// ✅ src/main/java/com/mvbr/store/adapter/out/persistence/entity/PaymentEntity.java
package com.mvbr.store.adapter.out.persistence.entity;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Construtores, getters, setters
}
```

**Spring Data JPA Repository:**

```java
// ✅ src/main/java/com/mvbr/store/adapter/out/persistence/PaymentJpaRepository.java
package com.mvbr.store.adapter.out.persistence;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findByCustomerId(UUID customerId);
}
```

---

### Passo 5: Configuration (Frameworks)

**Use Case Configuration:**

```java
// ✅ src/main/java/com/mvbr/store/config/UseCaseConfig.java
package com.mvbr.store.config;

@Configuration
public class UseCaseConfig {

    @Bean
    public ApprovePaymentUseCase approvePaymentUseCase(
            PaymentGateway paymentGateway,
            NotificationGateway notificationGateway
    ) {
        return new ApprovePaymentUseCase(
            paymentGateway,
            notificationGateway
        );
    }

    @Bean
    public CancelPaymentUseCase cancelPaymentUseCase(
            PaymentGateway paymentGateway,
            NotificationGateway notificationGateway
    ) {
        return new CancelPaymentUseCase(
            paymentGateway,
            notificationGateway
        );
    }
}
```

---

## 5. A Regra de Dependência

### A Regra de Ouro

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│              A REGRA DE DEPENDÊNCIA                          │
│                                                              │
│  ➡️  As dependências apontam SEMPRE para DENTRO              │
│  ➡️  Código externo DEPENDE de código interno                │
│  ➡️  Código interno NÃO conhece código externo               │
│                                                              │
│  CÍRCULO EXTERNO → CÍRCULO INTERNO  ✅                       │
│  CÍRCULO INTERNO → CÍRCULO EXTERNO  ❌                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Fluxo Completo

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  1. REQUEST HTTP                                               │
│     POST /api/payments/approve                                 │
│     {                                                          │
│       "paymentId": "uuid",                                     │
│       "customerId": "uuid",                                    │
│       "amount": 100.00                                         │
│     }                                                          │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  2. CONTROLLER (Adapter Inbound)                               │
│     - Recebe ApprovePaymentRequest                             │
│     - Converte para ApprovePaymentCommand                      │
│     - Chama approvePaymentUseCase.execute(command)             │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │ depende de ⬇
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  3. USE CASE (Application)                                     │
│     - Cria Payment entity                                      │
│     - Chama payment.approve()                                  │
│     - Chama paymentGateway.save(payment)                       │
│     - Chama notificationGateway.send(payment)                  │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │ depende de ⬇
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  4. ENTITY (Domain)                                            │
│     - approve() { this.status = APPROVED; }                    │
│     - LÓGICA DE NEGÓCIO executada                              │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  5. GATEWAY (Adapter Outbound)                                 │
│     - Converte Payment → PaymentEntity (JPA)                   │
│     - jpaRepository.save(entity)                               │
│     - Salva no PostgreSQL                                      │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  6. DATABASE (Framework)                                       │
│     INSERT INTO payment VALUES (...)                           │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Inversão de Dependência (DIP)

**PROBLEMA SEM DIP:**

```java
// ❌ Use Case dependendo de classe CONCRETA
public class ApprovePaymentUseCase {

    // ❌ DEPENDE de implementação específica!
    private final JpaPaymentGateway jpaPaymentGateway;

    public Payment execute(ApprovePaymentCommand command) {
        // ...
        return jpaPaymentGateway.save(payment); // ❌ Acoplado a JPA!
    }
}
```

**SOLUÇÃO COM DIP:**

```java
// ✅ Use Case dependendo de ABSTRAÇÃO (Port)
public class ApprovePaymentUseCase {

    // ✅ DEPENDE de interface!
    private final PaymentGateway paymentGateway;

    public Payment execute(ApprovePaymentCommand command) {
        // ...
        return paymentGateway.save(payment); // ✅ Desacoplado!
    }
}

// ✅ Gateway IMPLEMENTA a abstração
@Repository
public class JpaPaymentGateway implements PaymentGateway {
    // ...
}
```

**DIAGRAMA:**

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  SEM DIP (Errado):                                         │
│                                                            │
│  ApprovePaymentUseCase  ──────────►  JpaPaymentGateway    │
│      (Use Case)                       (Infraestrutura)    │
│                                                            │
│  ❌ Use Case depende de Infraestrutura                     │
│                                                            │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                                                            │
│  COM DIP (Correto):                                        │
│                                                            │
│  ApprovePaymentUseCase  ──────────►  PaymentGateway       │
│      (Use Case)                       (Interface)         │
│                                            ▲              │
│                                            │              │
│                                            │              │
│                                  JpaPaymentGateway        │
│                                  (Infraestrutura)         │
│                                                            │
│  ✅ Ambos dependem da ABSTRAÇÃO                            │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 6. Testando com Clean Architecture

### Por Que é Fácil Testar?

1. ✅ **Entities** são POJOs puros → teste sem frameworks
2. ✅ **Use Cases** dependem de interfaces → use Mocks/Fakes
3. ✅ **Adapters** são isolados → teste cada um separadamente

---

### Teste 1: Entity (Domain)

```java
// ✅ TESTE DE ENTITY - rápido, sem frameworks
class PaymentTest {

    @Test
    void shouldApprovePaymentWhenStatusIsPending() {
        // Given
        Payment payment = new Payment(
            new PaymentId(UUID.randomUUID()),
            new CustomerId(UUID.randomUUID()),
            new Money(new BigDecimal("100.00"))
        );

        // When
        payment.approve();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void shouldThrowExceptionWhenApprovingCancelledPayment() {
        // Given
        Payment payment = new Payment(
            new PaymentId(UUID.randomUUID()),
            new CustomerId(UUID.randomUUID()),
            new Money(new BigDecimal("100.00"))
        );
        payment.cancel();

        // When & Then
        assertThatThrownBy(() -> payment.approve())
            .isInstanceOf(PaymentAlreadyCancelledException.class)
            .hasMessageContaining("Cannot approve cancelled payment");
    }

    @Test
    void shouldThrowExceptionWhenAmountIsNegative() {
        // When & Then
        assertThatThrownBy(() -> new Payment(
            new PaymentId(UUID.randomUUID()),
            new CustomerId(UUID.randomUUID()),
            new Money(new BigDecimal("-100.00"))
        ))
        .isInstanceOf(InvalidPaymentException.class)
        .hasMessageContaining("Payment amount must be positive");
    }
}
```

---

### Teste 2: Use Case (com Fake)

```java
// ✅ TESTE DE USE CASE - com Fake Gateway
class ApprovePaymentUseCaseTest {

    private FakePaymentGateway paymentGateway;
    private FakeNotificationGateway notificationGateway;
    private ApprovePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        paymentGateway = new FakePaymentGateway();
        notificationGateway = new FakeNotificationGateway();
        useCase = new ApprovePaymentUseCase(
            paymentGateway,
            notificationGateway
        );
    }

    @Test
    void shouldApprovePaymentAndSendNotification() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            new PaymentId(UUID.randomUUID()),
            new CustomerId(UUID.randomUUID()),
            new Money(new BigDecimal("100.00"))
        );

        // When
        Payment payment = useCase.execute(command);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentGateway.wasSaved(payment)).isTrue();
        assertThat(notificationGateway.wasNotified(payment)).isTrue();
    }
}
```

**Fake Gateway:**

```java
// ✅ FAKE GATEWAY - implementação em memória
class FakePaymentGateway implements PaymentGateway {

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

    public boolean wasSaved(Payment payment) {
        return storage.containsKey(payment.getPaymentId());
    }
}

// ✅ FAKE NOTIFICATION GATEWAY
class FakeNotificationGateway implements NotificationGateway {

    private final List<Payment> notifiedPayments = new ArrayList<>();

    @Override
    public void sendPaymentApproved(Payment payment) {
        notifiedPayments.add(payment);
    }

    public boolean wasNotified(Payment payment) {
        return notifiedPayments.contains(payment);
    }
}
```

---

### Teste 3: Adapter (Integration Test)

```java
// ✅ TESTE DE ADAPTER - com banco real
@SpringBootTest
@Transactional
class JpaPaymentGatewayIntegrationTest {

    @Autowired
    private PaymentGateway paymentGateway;

    @Test
    void shouldSaveAndRetrievePayment() {
        // Given
        Payment payment = new Payment(
            new PaymentId(UUID.randomUUID()),
            new CustomerId(UUID.randomUUID()),
            new Money(new BigDecimal("100.00"))
        );
        payment.approve();

        // When
        Payment savedPayment = paymentGateway.save(payment);
        Optional<Payment> foundPayment = paymentGateway.findById(
            savedPayment.getPaymentId()
        );

        // Then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getStatus())
            .isEqualTo(PaymentStatus.APPROVED);
    }
}
```

---

## 7. Cenários do Dia a Dia

### Cenário 1: Adicionar Novo Endpoint REST

**Situação:**
Cliente quer endpoint GET para buscar pagamentos por cliente.

**Passos:**

1. **Criar Use Case:**

```java
// ✅ 1. Novo Use Case
public class GetPaymentsByCustomerUseCase {

    private final PaymentGateway paymentGateway;

    public List<Payment> execute(GetPaymentsByCustomerQuery query) {
        return paymentGateway.findByCustomerId(query.customerId());
    }
}
```

2. **Adicionar método no Port:**

```java
// ✅ 2. Adicionar no Port existente
public interface PaymentGateway {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId paymentId);
    List<Payment> findByCustomerId(CustomerId customerId); // ← NOVO
}
```

3. **Implementar no Gateway:**

```java
// ✅ 3. Implementar no Gateway
@Repository
public class JpaPaymentGateway implements PaymentGateway {

    @Override
    public List<Payment> findByCustomerId(CustomerId customerId) {
        return jpaRepository.findByCustomerId(customerId.getValue())
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }
}
```

4. **Criar Controller:**

```java
// ✅ 4. Novo endpoint
@GetMapping("/customer/{customerId}")
public ResponseEntity<List<PaymentResponse>> getPaymentsByCustomer(
        @PathVariable UUID customerId
) {
    GetPaymentsByCustomerQuery query = new GetPaymentsByCustomerQuery(
        new CustomerId(customerId)
    );

    List<Payment> payments = getPaymentsByCustomerUseCase.execute(query);

    List<PaymentResponse> responses = payments.stream()
        .map(PaymentResponse::from)
        .collect(Collectors.toList());

    return ResponseEntity.ok(responses);
}
```

**Impacto:**
- ✅ Domain NÃO mudou
- ✅ Use Case isolado
- ✅ Adapter isolado

---

### Cenário 2: Trocar REST por gRPC

**Situação:**
Empresa decide migrar de REST para gRPC.

**Passos:**

1. **Criar novo Adapter Inbound (gRPC):**

```java
// ✅ Novo Adapter - gRPC
@GrpcService
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final ApprovePaymentUseCase approvePaymentUseCase;

    @Override
    public void approvePayment(
            ApprovePaymentGrpcRequest request,
            StreamObserver<PaymentGrpcResponse> responseObserver
    ) {
        // 1. gRPC Request → Command
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            new PaymentId(UUID.fromString(request.getPaymentId())),
            new CustomerId(UUID.fromString(request.getCustomerId())),
            new Money(new BigDecimal(request.getAmount()))
        );

        // 2. Executar Use Case (MESMO Use Case do REST!)
        Payment payment = approvePaymentUseCase.execute(command);

        // 3. Domain → gRPC Response
        PaymentGrpcResponse response = PaymentGrpcResponse.newBuilder()
            .setPaymentId(payment.getPaymentId().getValue().toString())
            .setStatus(payment.getStatus().name())
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

**Impacto:**
- ✅ Domain NÃO mudou
- ✅ Use Cases NÃO mudaram
- ✅ Gateways NÃO mudaram
- ✅ Apenas NOVO Adapter Inbound (gRPC)

---

### Cenário 3: Trocar PostgreSQL por MongoDB

**Situação:**
Empresa decide migrar de PostgreSQL para MongoDB.

**Passos:**

1. **Criar novo Gateway (MongoDB):**

```java
// ✅ Novo Gateway - MongoDB
@Repository
public class MongoPaymentGateway implements PaymentGateway {

    private final MongoTemplate mongoTemplate;

    @Override
    public Payment save(Payment payment) {
        // Domain → MongoDB Document
        PaymentDocument document = new PaymentDocument(
            payment.getPaymentId().getValue().toString(),
            payment.getCustomerId().getValue().toString(),
            payment.getAmount().value(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );

        // Salvar no MongoDB
        mongoTemplate.save(document, "payments");

        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        PaymentDocument document = mongoTemplate.findById(
            paymentId.getValue().toString(),
            PaymentDocument.class,
            "payments"
        );

        if (document == null) {
            return Optional.empty();
        }

        // MongoDB Document → Domain
        Payment payment = new Payment(
            new PaymentId(UUID.fromString(document.id())),
            new CustomerId(UUID.fromString(document.customerId())),
            new Money(document.amount())
        );

        return Optional.of(payment);
    }
}
```

2. **Trocar a configuração:**

```java
// ✅ Trocar o Bean
@Configuration
public class GatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(MongoTemplate mongoTemplate) {
        return new MongoPaymentGateway(mongoTemplate); // ← MongoDB agora!
    }
}
```

**Impacto:**
- ✅ Domain NÃO mudou
- ✅ Use Cases NÃO mudaram
- ✅ Controllers NÃO mudaram
- ✅ Apenas NOVO Gateway (MongoDB)

---

## 8. Armadilhas Comuns

### Armadilha 1: Entity Conhecendo Framework

```java
// ❌ ERRADO - Entity com anotações JPA
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID paymentId;

    @Column(name = "amount")
    private BigDecimal amount;

    // ❌ Entity ACOPLADA ao JPA!
}
```

**PROBLEMA:**
- Entity conhece JPA
- Não consegue trocar JPA por outro framework
- Violação da Clean Architecture

**SOLUÇÃO:**

```java
// ✅ CORRETO - Entity PURA
public class Payment {

    private final PaymentId paymentId;
    private final Money amount;

    // ✅ ZERO anotações!
    // ✅ ZERO dependências de frameworks!
}

// ✅ JPA Entity SEPARADA (no Adapter)
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "amount")
    private BigDecimal amount;

    // ✅ JPA Entity ISOLADA no círculo externo
}
```

---

### Armadilha 2: Use Case Dependendo de Classe Concreta

```java
// ❌ ERRADO - Use Case dependendo de implementação
public class ApprovePaymentUseCase {

    private final JpaPaymentGateway jpaPaymentGateway; // ❌ Classe concreta!

    public Payment execute(ApprovePaymentCommand command) {
        // ...
        return jpaPaymentGateway.save(payment); // ❌ Acoplado a JPA!
    }
}
```

**PROBLEMA:**
- Use Case acoplado a JPA
- Não consegue trocar JPA por MongoDB
- Violação do DIP

**SOLUÇÃO:**

```java
// ✅ CORRETO - Use Case dependendo de abstração
public class ApprovePaymentUseCase {

    private final PaymentGateway paymentGateway; // ✅ Interface!

    public Payment execute(ApprovePaymentCommand command) {
        // ...
        return paymentGateway.save(payment); // ✅ Desacoplado!
    }
}
```

---

### Armadilha 3: Controller Fazendo Lógica de Negócio

```java
// ❌ ERRADO - Controller com lógica de negócio
@PostMapping("/approve")
public ResponseEntity<PaymentResponse> approvePayment(
        @RequestBody ApprovePaymentRequest request
) {
    // ❌ Validação de negócio no Controller!
    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidPaymentException("Amount must be positive");
    }

    // ❌ Criação de Entity no Controller!
    Payment payment = new Payment(
        new PaymentId(request.paymentId()),
        new CustomerId(request.customerId()),
        new Money(request.amount())
    );

    // ❌ Chamada direta ao Gateway!
    paymentGateway.save(payment);

    return ResponseEntity.ok(response);
}
```

**PROBLEMA:**
- Lógica de negócio no Controller
- Controller acoplado ao Domain
- Não consegue reutilizar lógica (gRPC, CLI, etc)

**SOLUÇÃO:**

```java
// ✅ CORRETO - Controller SÓ traduz
@PostMapping("/approve")
public ResponseEntity<PaymentResponse> approvePayment(
        @RequestBody ApprovePaymentRequest request
) {
    // ✅ Apenas converte Request → Command
    ApprovePaymentCommand command = ApprovePaymentCommand.from(request);

    // ✅ Delega para Use Case
    Payment payment = approvePaymentUseCase.execute(command);

    // ✅ Apenas converte Domain → Response
    PaymentResponse response = PaymentResponse.from(payment);

    return ResponseEntity.ok(response);
}
```

---

## 9. Checklist Clean Architecture

### ☐ ANTES DE IMPLEMENTAR

#### Identificação
- [ ] Identificou as **Entities** (regras universais)?
- [ ] Identificou os **Use Cases** (regras da aplicação)?
- [ ] Identificou os **Adapters** (REST, gRPC, JPA, Kafka)?

#### Estrutura de Pastas
- [ ] Criou pasta `domain/entity/` para Entities?
- [ ] Criou pasta `application/usecase/` para Use Cases?
- [ ] Criou pasta `application/port/` para Ports?
- [ ] Criou pasta `adapter/in/` para Inbound Adapters?
- [ ] Criou pasta `adapter/out/` para Outbound Adapters?

---

### ☐ DOMAIN (ENTITIES)

#### Pureza
- [ ] Entities são POJOs puros (sem anotações)?
- [ ] Entities NÃO têm `@Entity`, `@Table`, `@Column`?
- [ ] Entities NÃO importam Spring, JPA, Kafka?
- [ ] Entities NÃO conhecem Adapters?

#### Comportamento
- [ ] Entities têm métodos de negócio (`approve()`, `cancel()`)?
- [ ] Validações estão nas Entities?
- [ ] Entities usam Value Objects (`Money`, `PaymentId`)?
- [ ] Entities são imutáveis (campos `final`)?

---

### ☐ APPLICATION (USE CASES)

#### Dependências
- [ ] Use Cases dependem APENAS de Ports (interfaces)?
- [ ] Use Cases NÃO conhecem Adapters concretos?
- [ ] Use Cases NÃO importam JPA, Kafka, HTTP?

#### Orquestração
- [ ] Use Cases orquestram Entities?
- [ ] Use Cases delegam persistência para Ports?
- [ ] Use Cases retornam Domain Models (não DTOs)?

#### Ports
- [ ] Criou interfaces (Ports) para dependências externas?
- [ ] Ports usam vocabulário do domínio?
- [ ] Ports retornam Domain Models?

---

### ☐ ADAPTERS (INTERFACE ADAPTERS)

#### Inbound Adapters
- [ ] Controllers SÓ traduzem (Request → Command)?
- [ ] Controllers chamam Use Cases?
- [ ] Controllers convertem Domain → Response?
- [ ] Controllers NÃO fazem lógica de negócio?

#### Outbound Adapters
- [ ] Gateways IMPLEMENTAM Ports?
- [ ] Gateways convertem Domain ↔ Infraestrutura?
- [ ] Gateways isolam JPA, MongoDB, Kafka?

---

### ☐ FRAMEWORKS & DRIVERS

#### Configuração
- [ ] Criou `@Configuration` para injetar Use Cases?
- [ ] Use Cases são `@Bean`?
- [ ] Adapters implementam Ports?

---

### ☐ TESTES

#### Domain
- [ ] Testou Entities (POJOs puros)?
- [ ] Testes rodam SEM frameworks (rápidos)?

#### Application
- [ ] Testou Use Cases com Fakes/Mocks?
- [ ] Testes NÃO dependem de banco/Kafka?

#### Adapters
- [ ] Testou Adapters com integração real?
- [ ] Usou `@SpringBootTest` para Adapters?

---

### ☐ REGRA DE DEPENDÊNCIA

#### Verificação
- [ ] Domain NÃO importa Application?
- [ ] Domain NÃO importa Adapters?
- [ ] Application NÃO importa Adapters?
- [ ] Adapters importam Application e Domain?
- [ ] Dependências apontam SEMPRE para DENTRO?

---

## 10. Exercícios Práticos

### Exercício 1: Identificar Violações

Analise o código abaixo e identifique as violações:

```java
// Entity
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "amount")
    private BigDecimal amount;

    @Transient
    private PaymentService paymentService;

    public void approve() {
        this.status = "APPROVED";
        paymentService.sendNotification(this);
    }
}

// Use Case
@Service
public class ApprovePaymentUseCase {

    @Autowired
    private JpaPaymentRepository jpaRepository;

    public void execute(UUID paymentId) {
        PaymentEntity entity = jpaRepository.findById(paymentId).get();
        entity.setStatus("APPROVED");
        jpaRepository.save(entity);
    }
}
```

<details>
<summary><strong>📝 Resposta</strong></summary>

**Violações encontradas:**

1. ❌ **Entity com anotações JPA** (`@Entity`, `@Table`, `@Column`)
   - Entity deve ser POJO puro

2. ❌ **Entity dependendo de Service** (`PaymentService paymentService`)
   - Entity não pode conhecer camadas externas

3. ❌ **Use Case com `@Service`** (anotação do Spring)
   - Use Case não deve ter anotações de framework

4. ❌ **Use Case dependendo de classe concreta** (`JpaPaymentRepository`)
   - Use Case deve depender de interface (Port)

5. ❌ **Use Case manipulando Entity JPA diretamente**
   - Use Case deve trabalhar com Domain Model

**Solução:**

```java
// ✅ Entity PURA
public class Payment {
    private final PaymentId paymentId;
    private PaymentStatus status;

    public void approve() {
        this.status = PaymentStatus.APPROVED;
    }
}

// ✅ Use Case dependendo de Port
public class ApprovePaymentUseCase {
    private final PaymentGateway paymentGateway;
    private final NotificationGateway notificationGateway;

    public Payment execute(ApprovePaymentCommand command) {
        Payment payment = paymentGateway.findById(command.paymentId())
            .orElseThrow();
        payment.approve();
        Payment savedPayment = paymentGateway.save(payment);
        notificationGateway.send(savedPayment);
        return savedPayment;
    }
}
```

</details>

---

### Exercício 2: Implementar Novo Use Case

Implemente o caso de uso "Cancelar Pagamento" seguindo Clean Architecture:

**Requisitos:**
1. Criar Entity com método `cancel()`
2. Criar Use Case `CancelPaymentUseCase`
3. Criar Port `PaymentGateway` (se necessário)
4. Criar Controller REST
5. Criar Gateway JPA

<details>
<summary><strong>📝 Resposta</strong></summary>

```java
// 1. ✅ ENTITY - adicionar método cancel()
public class Payment {
    public void cancel() {
        if (this.status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException(
                "Cannot cancel approved payment: " + paymentId
            );
        }
        this.status = PaymentStatus.CANCELLED;
    }
}

// 2. ✅ USE CASE
public class CancelPaymentUseCase {
    private final PaymentGateway paymentGateway;

    public Payment execute(CancelPaymentCommand command) {
        Payment payment = paymentGateway.findById(command.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));

        payment.cancel();

        return paymentGateway.save(payment);
    }
}

// 3. ✅ COMMAND
public record CancelPaymentCommand(PaymentId paymentId) {}

// 4. ✅ CONTROLLER
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final CancelPaymentUseCase cancelPaymentUseCase;

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @PathVariable UUID paymentId
    ) {
        CancelPaymentCommand command = new CancelPaymentCommand(
            new PaymentId(paymentId)
        );

        Payment payment = cancelPaymentUseCase.execute(command);

        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}

// 5. ✅ GATEWAY - já existe (reutiliza PaymentGateway)
```

</details>

---

### Exercício 3: Adicionar Novo Adapter

Adicione um novo Adapter para publicar eventos no Kafka quando um pagamento for aprovado.

**Requisitos:**
1. Criar Port `EventPublisher`
2. Criar Adapter `KafkaEventPublisher`
3. Integrar ao Use Case existente

<details>
<summary><strong>📝 Resposta</strong></summary>

```java
// 1. ✅ PORT
package com.mvbr.store.application.port;

public interface EventPublisher {
    void publishPaymentApproved(Payment payment);
    void publishPaymentCancelled(Payment payment);
}

// 2. ✅ ADAPTER - Kafka
package com.mvbr.store.adapter.out.messaging;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, PaymentApprovedEvent> kafkaTemplate;

    @Override
    public void publishPaymentApproved(Payment payment) {
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            payment.getPaymentId().getValue(),
            payment.getCustomerId().getValue(),
            payment.getAmount().value(),
            payment.getCreatedAt()
        );

        kafkaTemplate.send("payment.approved.v1", event);
    }

    @Override
    public void publishPaymentCancelled(Payment payment) {
        // Implementação similar
    }
}

// 3. ✅ INTEGRAR ao Use Case
public class ApprovePaymentUseCase {
    private final PaymentGateway paymentGateway;
    private final EventPublisher eventPublisher; // ← NOVO

    public Payment execute(ApprovePaymentCommand command) {
        Payment payment = new Payment(...);
        payment.approve();

        Payment savedPayment = paymentGateway.save(payment);

        // ✅ Publicar evento
        eventPublisher.publishPaymentApproved(savedPayment);

        return savedPayment;
    }
}

// 4. ✅ CONFIGURATION
@Configuration
public class UseCaseConfig {

    @Bean
    public ApprovePaymentUseCase approvePaymentUseCase(
            PaymentGateway paymentGateway,
            EventPublisher eventPublisher
    ) {
        return new ApprovePaymentUseCase(paymentGateway, eventPublisher);
    }
}
```

</details>

---

## 🎯 Conclusão

**Clean Architecture** garante que:

1. ✅ **Domain** está no centro, isolado de tecnologias
2. ✅ **Dependências** apontam SEMPRE para dentro
3. ✅ **Frameworks** são detalhes substituíveis
4. ✅ **Testabilidade** é alta (Entities puras, Use Cases com Fakes)
5. ✅ **Manutenibilidade** é alta (lógica centralizada, baixo acoplamento)

**Lembre-se:**
- Entities = Regras universais (POJOs puros)
- Use Cases = Regras da aplicação (orquestram Entities)
- Adapters = Tradutores (HTTP, JPA, Kafka)
- Frameworks = Detalhes (Spring, PostgreSQL, etc)

**Regra de Ouro:**
```
DEPENDÊNCIAS APONTAM SEMPRE PARA DENTRO!
```

---

**Próximos Passos:**
1. Implemente um Use Case real no seu projeto
2. Teste Entities isoladamente (sem frameworks)
3. Crie Fake Gateways para testar Use Cases
4. Refatore código existente para Clean Architecture

**Dúvidas Comuns:**

| Pergunta | Resposta |
|----------|----------|
| Entity pode ter `@Entity`? | ❌ NÃO! Crie uma JPA Entity separada no Adapter |
| Use Case pode ter `@Service`? | ❌ NÃO! Use Case é POJO, injete via `@Configuration` |
| Port é interface ou classe? | ✅ SEMPRE interface! |
| Gateway fica em qual círculo? | ✅ Círculo 3 (Adapters) |
| Domain pode importar Spring? | ❌ NUNCA! Domain é puro |

---

**Boa sorte na sua jornada com Clean Architecture! 🚀**
