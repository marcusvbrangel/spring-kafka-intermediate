# Tutorial Definitivo: Domain-Driven Design (DDD) - Modelando o Coração do Software

## 📋 Sumário

1. [O que é DDD](#1-o-que-é-ddd)
2. [Building Blocks Táticos](#2-building-blocks-táticos)
3. [Entities vs Value Objects](#3-entities-vs-value-objects)
4. [Aggregates e Aggregate Roots](#4-aggregates-e-aggregate-roots)
5. [Domain Services](#5-domain-services)
6. [Repositories](#6-repositories)
7. [Domain Events](#7-domain-events)
8. [Ubiquitous Language](#8-ubiquitous-language)
9. [Bounded Contexts](#9-bounded-contexts)
10. [DDD na Prática (Projeto Real)](#10-ddd-na-prática-projeto-real)

---

## 1. O que é DDD

### Definição em 30 Segundos

**Domain-Driven Design (DDD)** é uma abordagem de desenvolvimento de software que coloca o **DOMÍNIO DO NEGÓCIO** no centro de tudo, usando uma **linguagem ubíqua** compartilhada entre desenvolvedores e especialistas do domínio.

```
DDD NÃO É:
❌ Framework ou biblioteca
❌ Arquitetura específica (Hexagonal, Clean)
❌ Tecnologia ou ferramenta
❌ Apenas código

DDD É:
✅ Filosofia de design
✅ Forma de pensar o software
✅ Colaboração entre dev e domínio
✅ Modelagem rica do negócio
✅ Linguagem compartilhada
```

### Comparação Visual

```
❌ SEM DDD - DRIVEN BY TECHNOLOGY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Reunião de Planning:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Dev: "Vamos criar uma tabela 'payment' com FK para 'user'..."
PM: "O que é FK?"
Dev: "Foreign Key! E vamos usar Redis para cache..."
Domain Expert: "Mas... e as regras de pagamento?"
Dev: "Depois a gente adiciona no Service!"

Código Resultante:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
public class Payment {
    @Id
    private Long id;  // ❌ Termo técnico (database)

    @Column(name = "amt")
    private BigDecimal amt;  // ❌ Termo abreviado (banco)

    @ManyToOne
    @JoinColumn(name = "user_fk")
    private User user;  // ❌ FK (termo de DB)

    // SEM regras de negócio!
    // Getters e setters apenas!
}

@Service
public class PaymentService {
    public void process(Payment payment) {
        // ❌ Lógica de negócio espalhada
        if (payment.getAmt().compareTo(BigDecimal.ZERO) > 0) {
            payment.setStatus("OK");
        }
    }
}

PROBLEMAS:
├─ ❌ Linguagem técnica (FK, Column, JoinColumn)
├─ ❌ Domínio anêmico (sem regras de negócio)
├─ ❌ Lógica espalhada (Service tem regras)
├─ ❌ Domain Expert não entende código
├─ ❌ Dev não entende negócio
└─ ❌ COMUNICAÇÃO QUEBRADA! 💥


✅ COM DDD - DRIVEN BY DOMAIN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Reunião de Planning (Event Storming):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Domain Expert: "Quando um pagamento é APROVADO, precisamos
                validar se o MONTANTE não excede o LIMITE DO
                CLIENTE, e então CONFIRMAR o pagamento."

Dev: "Entendi! Vou modelar assim:
      - Payment (Aggregate Root)
      - Money (Value Object para montante)
      - CustomerLimit (Value Object para limite)
      - PaymentApproved (Domain Event)"

Domain Expert: "Perfeito! É exatamente isso!"

Código Resultante (UBIQUITOUS LANGUAGE):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ✅ Linguagem do DOMÍNIO!
public class Payment {  // Aggregate Root

    private final PaymentId paymentId;  // ✅ Identity (não Long!)
    private final CustomerId customerId;
    private final Money amount;  // ✅ Value Object (não BigDecimal!)
    private PaymentStatus status;  // ✅ Enum (linguagem do negócio)

    /**
     * ✅ Método do NEGÓCIO (não "setStatus"!)
     * LINGUAGEM DO DOMAIN EXPERT!
     */
    public void approve(CustomerLimit customerLimit) {
        // ✅ Regra de negócio NO DOMÍNIO!
        if (amount.exceedsLimit(customerLimit)) {
            throw new PaymentExceedsLimitException(
                "Payment amount exceeds customer limit"
            );
        }

        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment"
            );
        }

        this.status = PaymentStatus.APPROVED;

        // ✅ Domain Event (comunicação)
        registerEvent(new PaymentApprovedEvent(this.paymentId));
    }

    /**
     * ✅ Método do NEGÓCIO (linguagem ubíqua)
     */
    public void cancel(CancellationReason reason) {
        if (status == PaymentStatus.CONFIRMED) {
            throw new PaymentAlreadyConfirmedException(
                "Cannot cancel confirmed payment"
            );
        }

        this.status = PaymentStatus.CANCELLED;
        registerEvent(new PaymentCancelledEvent(this.paymentId, reason));
    }
}

// ✅ Value Object (conceito do domínio)
public class Money {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        validate(amount, currency);
        this.amount = amount;
        this.currency = currency;
    }

    public boolean exceedsLimit(CustomerLimit limit) {
        return this.amount.compareTo(limit.getMaxAmount()) > 0;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException();
        }
        return new Money(
            this.amount.add(other.amount),
            this.currency
        );
    }
}

BENEFÍCIOS:
├─ ✅ Linguagem compartilhada (dev + domain expert)
├─ ✅ Domínio rico (regras de negócio no lugar certo)
├─ ✅ Código expressa o negócio
├─ ✅ Domain Expert ENTENDE o código!
├─ ✅ Dev ENTENDE o negócio!
└─ ✅ COMUNICAÇÃO PERFEITA! ✨
```

---

## 2. Building Blocks Táticos

### Visão Geral dos Building Blocks

```
BUILDING BLOCKS TÁTICOS DO DDD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌────────────────────────────────────────────────────┐
│             DOMAIN LAYER                           │
├────────────────────────────────────────────────────┤
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  ENTITIES                                    │ │
│  │  - Identidade única                          │ │
│  │  - Continuidade ao longo do tempo            │ │
│  │  - Mutável                                   │ │
│  │  Exemplo: Payment, Order, Customer           │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  VALUE OBJECTS                               │ │
│  │  - SEM identidade própria                    │ │
│  │  - Imutável                                  │ │
│  │  - Substituível                              │ │
│  │  Exemplo: Money, Address, Email              │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  AGGREGATES                                  │ │
│  │  - Grupo de Entities + Value Objects         │ │
│  │  - Aggregate Root (ponto de entrada)         │ │
│  │  - Fronteira transacional                    │ │
│  │  Exemplo: Order (root) + OrderItem           │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  DOMAIN SERVICES                             │ │
│  │  - Lógica que NÃO pertence a Entity/VO      │ │
│  │  - Operação entre múltiplos Aggregates      │ │
│  │  - Stateless                                 │ │
│  │  Exemplo: TransferMoneyService               │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  DOMAIN EVENTS                               │ │
│  │  - Algo que aconteceu no passado             │ │
│  │  - Imutável                                  │ │
│  │  - Comunicação entre Bounded Contexts        │ │
│  │  Exemplo: PaymentApproved, OrderPlaced       │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  REPOSITORIES (Interface)                    │ │
│  │  - Abstração de persistência                 │ │
│  │  - Acesso a Aggregate Roots                  │ │
│  │  - Interface no Domain                       │ │
│  │  Exemplo: PaymentRepository                  │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  FACTORIES                                   │ │
│  │  - Criação complexa de Aggregates            │ │
│  │  - Encapsula lógica de construção            │ │
│  │  Exemplo: OrderFactory                       │ │
│  └──────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

---

## 3. Entities vs Value Objects

### Quando Usar Entity vs Value Object

```
DECISÃO: ENTITY ou VALUE OBJECT?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PERGUNTAS MÁGICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. "Preciso IDENTIFICAR este objeto de forma ÚNICA?"
   ✅ SIM → ENTITY
   ❌ NÃO → VALUE OBJECT

2. "Preciso RASTREAR mudanças ao longo do TEMPO?"
   ✅ SIM → ENTITY
   ❌ NÃO → VALUE OBJECT

3. "Dois objetos com MESMOS ATRIBUTOS são IGUAIS?"
   ✅ SIM → VALUE OBJECT
   ❌ NÃO → ENTITY

4. "Posso SUBSTITUIR este objeto por outro igual?"
   ✅ SIM → VALUE OBJECT
   ❌ NÃO → ENTITY


EXEMPLOS PRÁTICOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Payment = ENTITY ✅
  └─ Tem identidade (paymentId)
  └─ Rastreia mudanças (PENDING → APPROVED → COMPLETED)
  └─ Payment #123 ≠ Payment #456 (mesmo que valores iguais)

Money = VALUE OBJECT ✅
  └─ SEM identidade própria
  └─ Money(100, USD) == Money(100, USD) (sempre!)
  └─ Imutável (não muda, cria novo)

Customer = ENTITY ✅
  └─ Tem identidade (customerId)
  └─ Rastreia mudanças (endereço, email muda)
  └─ Customer #1 ≠ Customer #2 (mesmo nome/email)

Address = VALUE OBJECT ✅
  └─ SEM identidade própria
  └─ Address("Rua X") == Address("Rua X") (sempre!)
  └─ Imutável

Email = VALUE OBJECT ✅
  └─ SEM identidade
  └─ Email("john@example.com") == Email("john@example.com")
  └─ Imutável
```

### Entity - Identidade e Continuidade

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ENTITY - Payment
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Payment - ENTITY (DDD).
 *
 * Características:
 * - TEM identidade única (PaymentId)
 * - Rastreia mudanças de estado (PENDING → APPROVED)
 * - Mutável (status muda)
 * - Igualdade baseada em ID (não em atributos)
 */
public class Payment {

    // ✅ Identidade (única e imutável)
    private final PaymentId paymentId;

    // ✅ Value Objects (conceitos do domínio)
    private final CustomerId customerId;
    private final Money amount;

    // ✅ Estado (pode mudar ao longo do tempo)
    private PaymentStatus status;

    // ✅ Auditoria (rastrear mudanças)
    private final Instant createdAt;
    private Instant approvedAt;

    /**
     * Construtor (validações + estado inicial).
     */
    public Payment(PaymentId paymentId, CustomerId customerId, Money amount) {
        validatePaymentId(paymentId);
        validateCustomerId(customerId);
        validateAmount(amount);

        this.paymentId = paymentId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;  // Estado inicial
        this.createdAt = Instant.now();
    }

    /**
     * ✅ Comportamento (não apenas getters/setters!)
     * Transição de estado com regras de negócio.
     */
    public void approve() {
        // Regra: não pode aprovar se já cancelado
        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException(
                "Cannot approve cancelled payment: " + paymentId
            );
        }

        // Regra: não pode aprovar duas vezes
        if (status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException(
                "Payment already approved: " + paymentId
            );
        }

        this.status = PaymentStatus.APPROVED;
        this.approvedAt = Instant.now();
    }

    /**
     * ✅ Igualdade baseada em IDENTIDADE (não em atributos!)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Payment payment = (Payment) obj;

        // ✅ Compara apenas ID!
        return paymentId.equals(payment.paymentId);
    }

    @Override
    public int hashCode() {
        return paymentId.hashCode();  // ✅ Hash baseado em ID!
    }

    // Getters (sem setters - imutabilidade parcial)
    public PaymentId getPaymentId() { return paymentId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
}


CARACTERÍSTICAS DA ENTITY:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Identidade única (PaymentId)
✅ Continuidade (mesmo objeto ao longo do tempo)
✅ Mutável (status muda)
✅ Igualdade por ID (não por atributos)
✅ Rastreável (createdAt, approvedAt)
✅ Comportamento rico (approve(), cancel())


EXEMPLO DE USO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Payment payment1 = new Payment(
    new PaymentId("pay-123"),
    new CustomerId("cust-456"),
    new Money(new BigDecimal("100.00"), Currency.USD)
);

Payment payment2 = new Payment(
    new PaymentId("pay-123"),  // ← MESMO ID!
    new CustomerId("cust-999"),  // ← Valores DIFERENTES!
    new Money(new BigDecimal("999.00"), Currency.EUR)
);

// ✅ São IGUAIS! (mesmo ID)
assertThat(payment1.equals(payment2)).isTrue();
// Porque Payment é ENTITY (igualdade por ID)!
```

### Value Object - Conceito sem Identidade

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECT - Money
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Money - VALUE OBJECT (DDD).
 *
 * Características:
 * - SEM identidade própria
 * - Imutável (final fields, sem setters)
 * - Igualdade por atributos (não por referência)
 * - Substituível
 */
public class Money {

    // ✅ Imutável (final)
    private final BigDecimal amount;
    private final Currency currency;

    /**
     * Construtor (validações).
     */
    public Money(BigDecimal amount, Currency currency) {
        validateAmount(amount);
        validateCurrency(currency);

        this.amount = amount;
        this.currency = currency;
    }

    // ✅ Operações retornam NOVO objeto (não modifica this!)
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                "Cannot add " + other.currency + " to " + this.currency
            );
        }

        // ✅ Retorna NOVO Money (imutabilidade!)
        return new Money(
            this.amount.add(other.amount),
            this.currency
        );
    }

    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                "Cannot subtract " + other.currency + " from " + this.currency
            );
        }

        return new Money(
            this.amount.subtract(other.amount),
            this.currency
        );
    }

    public Money multiply(int factor) {
        return new Money(
            this.amount.multiply(BigDecimal.valueOf(factor)),
            this.currency
        );
    }

    // ✅ Métodos de consulta (não mudam estado)
    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException();
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    // ✅ Igualdade baseada em ATRIBUTOS (não em referência!)
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Money money = (Money) obj;

        // ✅ Compara TODOS os atributos!
        return amount.compareTo(money.amount) == 0 &&
               currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount;
    }

    // Getters (SEM setters - imutabilidade!)
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
}


CARACTERÍSTICAS DO VALUE OBJECT:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ SEM identidade (não tem ID)
✅ Imutável (não muda, cria novo)
✅ Igualdade por valor (amount + currency)
✅ Substituível (pode trocar por outro igual)
✅ Side-effect free (operações não mudam estado)
✅ Conceito do domínio (Money, não BigDecimal!)


EXEMPLO DE USO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Money money1 = new Money(new BigDecimal("100.00"), Currency.USD);
Money money2 = new Money(new BigDecimal("100.00"), Currency.USD);

// ✅ São IGUAIS! (mesmos atributos)
assertThat(money1.equals(money2)).isTrue();

// ✅ Operações retornam NOVO objeto (imutabilidade)
Money total = money1.add(money2);
// money1 = 100 USD (não mudou!)
// money2 = 100 USD (não mudou!)
// total  = 200 USD (novo objeto!)

// ✅ Substituível
Payment payment = new Payment(..., money1, ...);
// Posso trocar money1 por money2 (são iguais!)
Payment payment2 = new Payment(..., money2, ...);
```

### Outros Value Objects Comuns

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECT - Email
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Email {
    private final String value;

    public Email(String value) {
        validateFormat(value);
        this.value = value.toLowerCase();  // Normalizar
    }

    private void validateFormat(String value) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!value.matches(emailRegex)) {
            throw new InvalidEmailException("Invalid email format: " + value);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Email email = (Email) obj;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public String getValue() { return value; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECT - Address
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Address {
    private final String street;
    private final String city;
    private final String state;
    private final String zipCode;
    private final String country;

    public Address(String street, String city, String state,
                   String zipCode, String country) {
        validateStreet(street);
        validateCity(city);
        validateZipCode(zipCode);

        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.country = country;
    }

    public String getFullAddress() {
        return String.format("%s, %s, %s %s, %s",
            street, city, state, zipCode, country);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Address address = (Address) obj;
        return street.equals(address.street) &&
               city.equals(address.city) &&
               state.equals(address.state) &&
               zipCode.equals(address.zipCode) &&
               country.equals(address.country);
    }

    // Getters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECT - DateRange
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class DateRange {
    private final LocalDate startDate;
    private final LocalDate endDate;

    public DateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                "Start date must be before end date"
            );
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean includes(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlaps(DateRange other) {
        return !this.endDate.isBefore(other.startDate) &&
               !other.endDate.isBefore(this.startDate);
    }

    public int getDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    // equals(), hashCode(), getters...
}
```

---

## 4. Aggregates e Aggregate Roots

### O que é um Aggregate?

```
AGGREGATE - DEFINIÇÃO
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggregate é um GRUPO de objetos (Entities + Value Objects)
que são tratados como uma UNIDADE para mudanças de dados.

┌─────────────────────────────────────────────┐
│         AGGREGATE: ORDER                    │
├─────────────────────────────────────────────┤
│                                             │
│  ┌───────────────────────────────────────┐ │
│  │  ORDER (Aggregate Root)               │ │  ← Ponto de entrada
│  │  - orderId                            │ │
│  │  - customerId                         │ │
│  │  - status                             │ │
│  │  - total                              │ │
│  └───────────────────────────────────────┘ │
│                ↓ tem                        │
│  ┌───────────────────────────────────────┐ │
│  │  ORDER ITEM (Entity)                  │ │  ← Interna ao aggregate
│  │  - productId                          │ │
│  │  - quantity                           │ │
│  │  - price                              │ │
│  └───────────────────────────────────────┘ │
│                ↓ tem                        │
│  ┌───────────────────────────────────────┐ │
│  │  SHIPPING ADDRESS (Value Object)      │ │  ← Interna ao aggregate
│  │  - street                             │ │
│  │  - city                               │ │
│  │  - zipCode                            │ │
│  └───────────────────────────────────────┘ │
│                                             │
└─────────────────────────────────────────────┘

REGRAS DO AGGREGATE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ Sempre acessar via AGGREGATE ROOT
   ❌ Não acessar OrderItem diretamente!
   ✅ order.addItem(...) ← através do root!

2. ✅ Fronteira transacional
   └─ Salvar Order = salvar Order + Items + Address
   └─ Tudo ou nada (atomicidade)

3. ✅ Invariantes sempre válidas
   └─ Order.total = soma de todos OrderItems
   └─ Aggregate Root garante isso!

4. ✅ Referências EXTERNAS só ao Aggregate Root
   ❌ Payment não referencia OrderItem diretamente
   ✅ Payment referencia Order (root)

5. ✅ Um Repository por Aggregate Root
   └─ OrderRepository (não OrderItemRepository!)
```

### Exemplo: Aggregate Order

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE ROOT - Order
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Order - AGGREGATE ROOT (DDD).
 *
 * Responsabilidades:
 * - Ponto de entrada para o Aggregate
 * - Garantir invariantes (total sempre correto)
 * - Controlar acesso aos objetos internos (OrderItem)
 * - Fronteira transacional
 */
public class Order {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      IDENTIDADE (Aggregate Root)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final OrderId orderId;
    private final CustomerId customerId;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      OBJETOS INTERNOS (parte do Aggregate)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // ✅ Encapsulado (private!) - só acessa via root!
    private final List<OrderItem> items;

    // ✅ Value Object
    private final ShippingAddress shippingAddress;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      ESTADO (Aggregate Root)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private OrderStatus status;
    private Money total;  // ✅ Invariante: sempre correto!

    /**
     * Construtor.
     */
    public Order(OrderId orderId, CustomerId customerId,
                ShippingAddress shippingAddress) {
        validateOrderId(orderId);
        validateCustomerId(customerId);
        validateShippingAddress(shippingAddress);

        this.orderId = orderId;
        this.customerId = customerId;
        this.shippingAddress = shippingAddress;
        this.items = new ArrayList<>();
        this.status = OrderStatus.DRAFT;
        this.total = Money.ZERO;  // Invariante: começa zerado
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      COMPORTAMENTO (Aggregate Root)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * ✅ Adicionar item (ATRAVÉS DO ROOT!)
     * Garante INVARIANTE: total sempre correto.
     */
    public void addItem(ProductId productId, int quantity, Money price) {
        // Validar se pode adicionar (regra de negócio)
        if (status != OrderStatus.DRAFT) {
            throw new OrderAlreadyConfirmedException(
                "Cannot add items to confirmed order"
            );
        }

        validateQuantity(quantity);
        validatePrice(price);

        // Criar OrderItem
        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);

        // ✅ MANTER INVARIANTE: recalcular total!
        recalculateTotal();
    }

    /**
     * ✅ Remover item (ATRAVÉS DO ROOT!)
     */
    public void removeItem(ProductId productId) {
        if (status != OrderStatus.DRAFT) {
            throw new OrderAlreadyConfirmedException(
                "Cannot remove items from confirmed order"
            );
        }

        boolean removed = items.removeIf(
            item -> item.getProductId().equals(productId)
        );

        if (!removed) {
            throw new OrderItemNotFoundException(
                "Item not found: " + productId
            );
        }

        // ✅ MANTER INVARIANTE!
        recalculateTotal();
    }

    /**
     * ✅ Confirmar pedido.
     */
    public void confirm() {
        if (items.isEmpty()) {
            throw new EmptyOrderException(
                "Cannot confirm empty order"
            );
        }

        if (status != OrderStatus.DRAFT) {
            throw new OrderAlreadyConfirmedException(
                "Order already confirmed"
            );
        }

        this.status = OrderStatus.CONFIRMED;

        // ✅ Domain Event (comunicação)
        registerEvent(new OrderConfirmedEvent(this.orderId));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      INVARIANTES (Aggregate Root garante!)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * ✅ Recalcular total (manter invariante).
     * PRIVADO! Só o root pode chamar!
     */
    private void recalculateTotal() {
        this.total = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      GETTERS (Aggregate Root)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public OrderId getOrderId() { return orderId; }
    public CustomerId getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public Money getTotal() { return total; }

    /**
     * ✅ Retorna CÓPIA DEFENSIVA (não expõe lista interna!)
     */
    public List<OrderItem> getItems() {
        return List.copyOf(items);  // Imutável!
    }

    /**
     * ❌ NÃO TEM setter para items!
     * ❌ NÃO TEM setter para total!
     * ✅ Só métodos de negócio (addItem, removeItem)
     */
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ENTITY INTERNA - OrderItem
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * OrderItem - ENTITY (mas INTERNA ao Aggregate).
 *
 * ❌ NÃO é Aggregate Root!
 * ❌ NÃO tem Repository próprio!
 * ✅ Só é acessada via Order (root)!
 */
class OrderItem {  // ← package-private (não pública!)

    private final ProductId productId;
    private final int quantity;
    private final Money price;

    OrderItem(ProductId productId, int quantity, Money price) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }

    /**
     * Calcular subtotal do item.
     */
    Money getSubtotal() {
        return price.multiply(quantity);
    }

    ProductId getProductId() { return productId; }
    int getQuantity() { return quantity; }
    Money getPrice() { return price; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECT - ShippingAddress
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class ShippingAddress {
    private final String street;
    private final String city;
    private final String zipCode;

    // Construtor, equals, hashCode...
}


BENEFÍCIOS DO AGGREGATE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ INVARIANTES GARANTIDAS:
   └─ Order.total SEMPRE correto (recalcula automático)

2. ✅ CONSISTÊNCIA TRANSACIONAL:
   └─ Salvar Order = salvar tudo (Order + Items)
   └─ Não salva OrderItem separado!

3. ✅ ENCAPSULAMENTO:
   └─ OrderItem é privado (só Order acessa)
   └─ Não há como quebrar regras!

4. ✅ ÚNICA ENTRADA:
   └─ order.addItem(...) ← sempre por aqui
   └─ Não acessa items.add(...) diretamente

5. ✅ SIMPLES USAR:
   └─ Cliente só fala com Order (root)
   └─ Não precisa saber sobre OrderItem!
```

### Regras de Ouro dos Aggregates

```
REGRAS DE OURO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ PEQUENOS AGGREGATES
   └─ Aggregate grande = performance ruim
   └─ Aggregate pequeno = rápido, eficiente
   └─ Prefira: Order + OrderItems (OK)
   └─ Evite: Order + OrderItems + Customer + Products (muito grande!)

2. ✅ REFERÊNCIAS POR ID (não por objeto)
   └─ Order tem CustomerId (não Customer object)
   └─ OrderItem tem ProductId (não Product object)
   └─ Evita carregar Aggregates desnecessários

3. ✅ INVARIANTES LOCAIS
   └─ Aggregate garante SUAS próprias regras
   └─ Order garante: total = soma items
   └─ NÃO garante: Customer.balance (outro aggregate!)

4. ✅ UM REPOSITORY POR AGGREGATE
   └─ OrderRepository (para Order)
   └─ CustomerRepository (para Customer)
   └─ ❌ NÃO: OrderItemRepository!

5. ✅ EVENTUAL CONSISTENCY ENTRE AGGREGATES
   └─ Dentro do Aggregate: consistência FORTE
   └─ Entre Aggregates: consistência EVENTUAL (Domain Events)


ANTI-PATTERNS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Aggregate gigante (Order + Customer + Products + Payments)
❌ Referência a outro Aggregate Root (Order tem Customer object)
❌ Modificar objeto interno diretamente (order.items.add(...))
❌ Múltiplos repositories para mesmo Aggregate
❌ Aggregate sem root (todos objetos expostos)
```

---

## 5. Domain Services

### Quando Usar Domain Service

```
DOMAIN SERVICE - QUANDO USAR?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PERGUNTAS MÁGICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. "Esta lógica pertence a qual Entity/Value Object?"
   ❓ Não pertence claramente a nenhum
   ✅ → DOMAIN SERVICE

2. "Esta operação envolve MÚLTIPLOS Aggregates?"
   ✅ SIM → DOMAIN SERVICE
   ❌ NÃO → método na Entity

3. "Esta lógica é STATELESS?"
   ✅ SIM → DOMAIN SERVICE
   ❌ NÃO → Entity/Value Object


EXEMPLOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Transferir dinheiro entre contas
   └─ Envolve: Account (origem) + Account (destino)
   └─ Não pertence a nenhum Account específico
   └─ DOMAIN SERVICE: MoneyTransferService

✅ Calcular frete
   └─ Envolve: Order, ShippingAddress, Warehouse
   └─ Não pertence claramente a nenhum
   └─ DOMAIN SERVICE: ShippingCalculator

✅ Validar disponibilidade de estoque
   └─ Envolve: Product, Inventory, Order
   └─ Não pertence a nenhum específico
   └─ DOMAIN SERVICE: StockValidator

❌ Calcular total do pedido
   └─ Pertence CLARAMENTE a Order
   └─ NÃO é Domain Service!
   └─ Método na Entity: order.calculateTotal()

❌ Aprovar pagamento
   └─ Pertence CLARAMENTE a Payment
   └─ NÃO é Domain Service!
   └─ Método na Entity: payment.approve()
```

### Exemplo: Domain Service

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN SERVICE - MoneyTransferService
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * MoneyTransferService - DOMAIN SERVICE (DDD).
 *
 * Responsabilidade:
 * - Transferir dinheiro entre contas
 * - Envolve DOIS Aggregates (Account origem + destino)
 * - Lógica NÃO pertence a nenhum Account específico
 * - STATELESS (não guarda estado)
 *
 * IMPORTANTE:
 * - DOMAIN Service (não Application Service!)
 * - Contém lógica de DOMÍNIO (não orquestração)
 * - Pode ser chamado por Application Service
 */
public class MoneyTransferService {

    /**
     * Transferir dinheiro de uma conta para outra.
     *
     * Regras de negócio:
     * - Conta origem deve ter saldo suficiente
     * - Ambas contas devem estar ativas
     * - Moedas devem ser iguais
     *
     * @throws InsufficientFundsException se saldo insuficiente
     * @throws AccountNotActiveException se conta inativa
     * @throws CurrencyMismatchException se moedas diferentes
     */
    public void transfer(Account from, Account to, Money amount) {

        // ✅ Validações do Domain Service
        if (!from.isActive()) {
            throw new AccountNotActiveException(
                "Source account is not active: " + from.getAccountId()
            );
        }

        if (!to.isActive()) {
            throw new AccountNotActiveException(
                "Target account is not active: " + to.getAccountId()
            );
        }

        if (!from.getBalance().hasSameCurrency(amount)) {
            throw new CurrencyMismatchException(
                "Currency mismatch between account and transfer amount"
            );
        }

        // ✅ Operação coordenada entre DOIS Aggregates
        from.debit(amount);  // Debita origem
        to.credit(amount);   // Credita destino

        // ✅ Domain Event (registrado no Aggregate Root)
        from.registerEvent(new MoneyTransferredEvent(
            from.getAccountId(),
            to.getAccountId(),
            amount
        ));
    }

    /**
     * Validar se transferência é possível.
     *
     * ✅ Lógica de domínio (não de infraestrutura)
     */
    public boolean canTransfer(Account from, Account to, Money amount) {
        return from.isActive() &&
               to.isActive() &&
               from.hasSufficientBalance(amount) &&
               from.getBalance().hasSameCurrency(amount);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE ROOT - Account
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Account - AGGREGATE ROOT.
 *
 * ✅ Métodos de negócio PRÓPRIOS da entidade.
 * ❌ NÃO tem método transfer() (lógica é do Domain Service!)
 */
public class Account {

    private final AccountId accountId;
    private Money balance;
    private AccountStatus status;

    /**
     * ✅ Debitar da conta (lógica INTERNA).
     * Chamado pelo MoneyTransferService.
     */
    public void debit(Money amount) {
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientFundsException(
                "Insufficient funds. Balance: " + balance +
                ", Required: " + amount
            );
        }

        this.balance = balance.subtract(amount);
    }

    /**
     * ✅ Creditar na conta (lógica INTERNA).
     * Chamado pelo MoneyTransferService.
     */
    public void credit(Money amount) {
        this.balance = balance.add(amount);
    }

    /**
     * ✅ Verificar saldo suficiente.
     */
    public boolean hasSufficientBalance(Money amount) {
        return balance.isGreaterThanOrEqual(amount);
    }

    /**
     * ✅ Verificar se conta está ativa.
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // Getters...
    public AccountId getAccountId() { return accountId; }
    public Money getBalance() { return balance; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION SERVICE (Orquestração)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * TransferMoneyUseCase - APPLICATION SERVICE.
 *
 * Responsabilidade:
 * - ORQUESTRAR o Use Case
 * - Buscar Aggregates (repositories)
 * - Chamar Domain Service
 * - Persistir mudanças
 * - Gerenciar transação
 *
 * NÃO contém lógica de domínio!
 * (lógica está em MoneyTransferService)
 */
@Service
public class TransferMoneyUseCase {

    private final AccountRepository accountRepository;
    private final MoneyTransferService transferService;  // ← Domain Service!

    public TransferMoneyUseCase(AccountRepository accountRepository,
                               MoneyTransferService transferService) {
        this.accountRepository = accountRepository;
        this.transferService = transferService;
    }

    @Transactional
    public void execute(TransferMoneyCommand command) {

        // 1. Buscar Aggregates (infraestrutura)
        Account from = accountRepository.findById(command.fromAccountId())
            .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));

        Account to = accountRepository.findById(command.toAccountId())
            .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));

        Money amount = new Money(command.amount(), command.currency());

        // 2. Executar lógica de domínio (Domain Service!)
        transferService.transfer(from, to, amount);

        // 3. Persistir mudanças (infraestrutura)
        accountRepository.save(from);
        accountRepository.save(to);

        // ✅ Use Case SÓ orquestra (não tem lógica de negócio!)
    }
}


DIFERENÇA: DOMAIN SERVICE vs APPLICATION SERVICE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DOMAIN SERVICE:
├─ Contém lógica de NEGÓCIO
├─ Opera sobre Domain Models
├─ Stateless (pura lógica)
├─ Exemplo: MoneyTransferService, ShippingCalculator
└─ Vive na DOMAIN LAYER

APPLICATION SERVICE:
├─ ORQUESTRA Use Cases
├─ Busca Aggregates (repositories)
├─ Chama Domain Services
├─ Gerencia transações
├─ Exemplo: TransferMoneyUseCase, ApprovePaymentService
└─ Vive na APPLICATION LAYER
```

---

## 6. Repositories

### Repository no DDD

```
REPOSITORY (DDD)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Abstração que simula uma "coleção em memória"
  de Aggregate Roots.

CARACTERÍSTICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Interface definida na DOMAIN LAYER (Port)
✅ Implementação na INFRASTRUCTURE LAYER (Adapter)
✅ Um Repository por AGGREGATE ROOT
✅ Retorna Domain Models (não Entities JPA!)
✅ Operações em termos do domínio (não SQL!)


EXEMPLO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Interface no DOMAIN
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId orderId);
    List<Order> findByCustomer(CustomerId customerId);
}

// ✅ Implementação no INFRASTRUCTURE
@Repository
public class JpaOrderRepository implements OrderRepository {
    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        OrderEntity entity = toEntity(order);
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return jpaRepository.findById(orderId.getValue())
            .map(this::toDomain);
    }
}
```

### Exemplo Completo: Repository

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN LAYER - Repository Interface (Port)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * PaymentRepository - PORT (Hexagonal Architecture).
 *
 * Interface definida pelo DOMAIN (não Infrastructure!)
 * Vocabulário do DOMÍNIO (não SQL!)
 */
public interface PaymentRepository {

    /**
     * Salvar pagamento.
     * @return Payment salvo (com possíveis mudanças de infra)
     */
    Payment save(Payment payment);

    /**
     * Buscar por ID.
     * @return Optional<Payment> (pode não existir)
     */
    Optional<Payment> findById(PaymentId paymentId);

    /**
     * Buscar pagamentos de um cliente.
     * ✅ Query em termos do DOMÍNIO (não SQL!)
     */
    List<Payment> findByCustomer(CustomerId customerId);

    /**
     * Buscar pagamentos pendentes.
     */
    List<Payment> findPendingPayments();

    /**
     * Verificar se pagamento existe.
     */
    boolean exists(PaymentId paymentId);

    /**
     * Remover pagamento.
     * (raro em DDD, geralmente soft-delete via status)
     */
    void delete(PaymentId paymentId);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE LAYER - JPA Entity
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * PaymentEntity - JPA Entity (INFRASTRUCTURE).
 *
 * ❌ Domain NÃO conhece esta classe!
 * ✅ Detalhe de implementação (PostgreSQL)
 */
@Entity
@Table(name = "payment")
class PaymentEntity {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    // Construtores, getters, setters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE LAYER - Mapper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * PaymentMapper - Converte Domain ↔ Entity.
 */
@Component
class PaymentMapper {

    /**
     * Domain → Entity (para salvar).
     */
    PaymentEntity toEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentId(payment.getPaymentId().getValue());
        entity.setCustomerId(payment.getCustomerId().getValue());
        entity.setAmount(payment.getAmount().getAmountValue());
        entity.setCurrency(payment.getAmount().getCurrency().getCurrencyCode());
        entity.setStatus(payment.getStatus());
        entity.setCreatedAt(payment.getCreatedAt());
        return entity;
    }

    /**
     * Entity → Domain (ao buscar).
     */
    Payment toDomain(PaymentEntity entity) {
        PaymentId paymentId = new PaymentId(entity.getPaymentId());
        CustomerId customerId = new CustomerId(entity.getCustomerId());

        Money amount = new Money(
            entity.getAmount(),
            Currency.getInstance(entity.getCurrency())
        );

        // Reconstruir Domain a partir da Entity
        Payment payment = new Payment(paymentId, customerId, amount);

        // Restaurar estado (se não PENDING)
        if (entity.getStatus() == PaymentStatus.APPROVED) {
            payment.approve();
        } else if (entity.getStatus() == PaymentStatus.CANCELLED) {
            payment.cancel();
        }

        return payment;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE LAYER - Repository Adapter
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * JpaPaymentRepository - ADAPTER (Hexagonal Architecture).
 *
 * Implementa interface do DOMAIN usando JPA.
 */
@Repository
class JpaPaymentRepository implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;  // Spring Data
    private final PaymentMapper mapper;

    public JpaPaymentRepository(PaymentJpaRepository jpaRepository,
                               PaymentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        // Domain → Entity
        PaymentEntity entity = mapper.toEntity(payment);

        // Salvar no banco
        PaymentEntity saved = jpaRepository.save(entity);

        // Entity → Domain
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return jpaRepository.findById(paymentId.getValue())
            .map(mapper::toDomain);  // Entity → Domain
    }

    @Override
    public List<Payment> findByCustomer(CustomerId customerId) {
        return jpaRepository.findByCustomerId(customerId.getValue())
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Payment> findPendingPayments() {
        return jpaRepository.findByStatus(PaymentStatus.PENDING)
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean exists(PaymentId paymentId) {
        return jpaRepository.existsById(paymentId.getValue());
    }

    @Override
    public void delete(PaymentId paymentId) {
        jpaRepository.deleteById(paymentId.getValue());
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE LAYER - Spring Data JPA
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * PaymentJpaRepository - Spring Data JPA.
 *
 * ❌ Domain NÃO conhece esta interface!
 * ✅ Detalhe de implementação (JPA)
 */
interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {

    List<PaymentEntity> findByCustomerId(String customerId);

    List<PaymentEntity> findByStatus(PaymentStatus status);
}


BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ DOMAIN INDEPENDENTE:
   └─ Domain não conhece JPA, PostgreSQL, nada!
   └─ Trocar banco? Domain intocado!

2. ✅ TESTÁVEL:
   └─ Criar FakePaymentRepository (in-memory)
   └─ Testar Use Case sem banco real!

3. ✅ VOCABULÁRIO DO DOMÍNIO:
   └─ findByCustomer (não findByCustomerId SQL)
   └─ findPendingPayments (não SELECT WHERE status=PENDING)

4. ✅ FLEXIBILIDADE:
   └─ JpaPaymentRepository (PostgreSQL)
   └─ MongoPaymentRepository (MongoDB)
   └─ InMemoryPaymentRepository (testes)
   └─ Todos implementam mesma interface!
```

---

## 7. Domain Events

### O que são Domain Events

```
DOMAIN EVENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Algo que ACONTECEU no passado e é RELEVANTE
  para o domínio do negócio.

CARACTERÍSTICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Imutável (aconteceu, não muda!)
✅ Nome no PASSADO (PaymentApproved, OrderPlaced)
✅ Contém dados do evento (quando, quem, o quê)
✅ Registrado no Aggregate Root
✅ Publicado após commit (Outbox Pattern)


POR QUE USAR?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Comunicação entre Bounded Contexts
✅ Eventual Consistency entre Aggregates
✅ Event Sourcing (histórico de mudanças)
✅ Auditoria (quem fez o quê, quando)
✅ Integração com outros sistemas


EXEMPLOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ PaymentApproved (pagamento foi aprovado)
✅ OrderPlaced (pedido foi feito)
✅ ProductOutOfStock (produto esgotou)
✅ CustomerRegistered (cliente se cadastrou)
✅ ShipmentDispatched (envio despachado)
```

### Exemplo: Domain Event

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN EVENT - PaymentApprovedEvent
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * PaymentApprovedEvent - DOMAIN EVENT (DDD).
 *
 * Representa: "Um pagamento foi aprovado!"
 *
 * Características:
 * - Imutável (record)
 * - Nome no passado (Approved, não Approve)
 * - Contém dados relevantes do evento
 */
public record PaymentApprovedEvent(
    String eventId,           // Identificador único do evento
    PaymentId paymentId,      // Qual pagamento
    CustomerId customerId,    // De qual cliente
    Money amount,             // Valor aprovado
    Instant occurredAt        // Quando aconteceu
) {

    /**
     * Factory method: criar evento a partir de Payment.
     */
    public static PaymentApprovedEvent from(Payment payment) {
        return new PaymentApprovedEvent(
            UUID.randomUUID().toString(),
            payment.getPaymentId(),
            payment.getCustomerId(),
            payment.getAmount(),
            Instant.now()
        );
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE ROOT - Payment (com Eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Payment - AGGREGATE ROOT com Domain Events.
 */
public class Payment {

    private final PaymentId paymentId;
    private final CustomerId customerId;
    private final Money amount;
    private PaymentStatus status;

    // ✅ Lista de eventos (não persistida, só em memória)
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Aprovar pagamento.
     */
    public void approve() {
        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException();
        }

        this.status = PaymentStatus.APPROVED;

        // ✅ Registrar Domain Event
        registerEvent(PaymentApprovedEvent.from(this));
    }

    /**
     * ✅ Registrar evento (não publica ainda!)
     * Publicação acontece APÓS commit (Outbox Pattern).
     */
    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /**
     * ✅ Obter eventos registrados.
     */
    public List<DomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    /**
     * ✅ Limpar eventos (após publicação).
     */
    public void clearEvents() {
        this.domainEvents.clear();
    }

    // Getters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION SERVICE - Publicar Eventos
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void approvePayment(ApprovePaymentCommand command) {

        // 1. Buscar Payment
        Payment payment = paymentRepository.findById(command.paymentId())
            .orElseThrow();

        // 2. Executar lógica de domínio
        payment.approve();  // ← Registra PaymentApprovedEvent!

        // 3. Persistir Payment
        Payment saved = paymentRepository.save(payment);

        // 4. Publicar Domain Events (após commit!)
        // ✅ Outbox Pattern: salva eventos na tabela outbox
        saved.getDomainEvents().forEach(event -> {
            eventPublisher.publish(event);
        });

        // 5. Limpar eventos
        saved.clearEvents();
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE - Event Publisher (Outbox)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Component
public class DomainEventPublisher {

    private final OutboxService outboxService;

    /**
     * Publicar evento usando Outbox Pattern.
     */
    public void publish(DomainEvent event) {

        if (event instanceof PaymentApprovedEvent approved) {
            outboxService.saveEvent(
                "PAYMENT",
                approved.paymentId().getValue(),
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                approved.customerId().getValue(),
                approved
            );
        }

        // Outros tipos de eventos...
    }
}


FLUXO COMPLETO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. payment.approve()
   └─ Muda status para APPROVED
   └─ Registra PaymentApprovedEvent

2. paymentRepository.save(payment)
   └─ Salva Payment no banco

3. eventPublisher.publish(event)
   └─ Salva PaymentApprovedEvent na tabela outbox

4. OutboxPublisher (job assíncrono)
   └─ Publica eventos da outbox para Kafka

5. Consumers (outros Bounded Contexts)
   └─ Recebem PaymentApprovedEvent
   └─ Processam (enviar email, atualizar estoque, etc)

✅ ATOMICIDADE: Payment + OutboxEvent salvos juntos!
✅ AT-LEAST-ONCE: Evento sempre é publicado!
✅ EVENTUAL CONSISTENCY: Consumers processam depois!
```

---

## 8. Ubiquitous Language

### O que é Linguagem Ubíqua

```
UBIQUITOUS LANGUAGE (Linguagem Ubíqua)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Linguagem COMPARTILHADA entre desenvolvedores e
  especialistas do domínio (domain experts).

OBJETIVO:
  Eliminar ambiguidade, mal-entendidos e traduções.

ONDE USA:
  ✅ Código (classes, métodos, variáveis)
  ✅ Conversas (reuniões, emails, slack)
  ✅ Documentação (diagramas, specs)
  ✅ Testes (nomes de testes)
  ✅ TUDO!


EXEMPLO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Domain Expert diz: "Quando o cliente APROVA um pagamento..."

❌ ERRADO (dev traduz):
public class PaymentService {
    public void changeStatus(Payment p) {  // ← "changeStatus"?
        p.setStatus("OK");  // ← "OK"?
    }
}

✅ CORRETO (mesma linguagem):
public class Payment {
    public void approve() {  // ← "approve"! (igual expert falou)
        this.status = PaymentStatus.APPROVED;  // ← "APPROVED"!
    }
}

Domain Expert lê o código e ENTENDE! ✨
```

### Exemplo: Antes e Depois

```java
// ❌ SEM UBIQUITOUS LANGUAGE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Domain Expert diz:
 * "Um pedido pode ser confirmado se tiver pelo menos
 *  um item e o cliente tiver limite de crédito disponível."
 */

// ❌ Código do dev (SEM linguagem do domínio):
public class OrderService {

    public void validate(Order o) {  // ← "validate"? Expert não falou isso!
        if (o.getItems().size() == 0) {  // ← "size"? Expert disse "pelo menos um"!
            throw new Exception("Invalid");  // ← "Invalid"? Qual regra quebrou?
        }

        Customer c = customerRepo.findById(o.getCustId());  // ← "CustId"? Expert disse "cliente"!
        double limit = c.getLimit();  // ← "limit"? Expert disse "limite de crédito"!
        double total = calculateTotal(o);  // ← OK

        if (total > limit) {  // ← Lógica OK, mas vocabulário pobre!
            throw new Exception("Exceeds limit");
        }

        o.setStatus("CONFIRMED");  // ← setter? Expert disse "confirmar"!
    }
}

PROBLEMAS:
├─ ❌ "validate" (expert disse "confirmar")
├─ ❌ "size == 0" (expert disse "ter pelo menos um item")
├─ ❌ "CustId" (expert disse "cliente")
├─ ❌ "limit" (expert disse "limite de crédito")
├─ ❌ "setStatus" (expert disse "confirmar")
└─ ❌ Domain Expert NÃO entende o código!


// ✅ COM UBIQUITOUS LANGUAGE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Domain Expert diz:
 * "Um pedido pode ser CONFIRMADO se tiver PELO MENOS UM ITEM
 *  e o cliente tiver LIMITE DE CRÉDITO DISPONÍVEL."
 */

// ✅ Código com linguagem do domínio:
public class Order {  // Aggregate Root

    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private OrderStatus status;

    /**
     * ✅ "confirmar" (igual expert falou!)
     * ✅ Regras de negócio em termos do domínio!
     */
    public void confirm(Customer customer) {

        // ✅ "pelo menos um item" (igual expert falou!)
        if (!hasAtLeastOneItem()) {
            throw new EmptyOrderException(
                "Cannot confirm order without items"  // ← Mensagem clara!
            );
        }

        // ✅ "limite de crédito disponível" (igual expert falou!)
        if (!customer.hasCreditLimitAvailable(this.getTotal())) {
            throw new CreditLimitExceededException(
                "Order total exceeds customer credit limit"  // ← Regra clara!
            );
        }

        // ✅ "confirmado" (não "setStatus")
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * ✅ Método com nome do domínio (não "size == 0")
     */
    private boolean hasAtLeastOneItem() {
        return !items.isEmpty();
    }

    public Money getTotal() {
        return items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);
    }
}

public class Customer {  // Aggregate Root

    private final CustomerId customerId;
    private final Money creditLimit;  // ✅ "limite de crédito"!

    /**
     * ✅ Método com linguagem do domínio!
     */
    public boolean hasCreditLimitAvailable(Money amount) {
        return creditLimit.isGreaterThanOrEqual(amount);
    }
}

BENEFÍCIOS:
├─ ✅ Domain Expert ENTENDE o código!
├─ ✅ Dev ENTENDE o negócio!
├─ ✅ Zero ambiguidade (confirm = confirmar)
├─ ✅ Comunicação perfeita!
└─ ✅ Código É a documentação!
```

### Construindo Linguagem Ubíqua

```
COMO CONSTRUIR UBIQUITOUS LANGUAGE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ EVENT STORMING
   └─ Reunião com devs + domain experts
   └─ Mapear eventos: "PaymentApproved", "OrderPlaced"
   └─ Descobrir termos do domínio

2. ✅ GLOSSÁRIO
   └─ Documentar termos do domínio
   └─ Exemplo:
       • Order: Pedido do cliente
       • Confirm: Marcar pedido como confirmado
       • Credit Limit: Valor máximo que cliente pode gastar

3. ✅ CODE REVIEW
   └─ Revisar nomes de classes/métodos
   └─ Perguntar: "Domain Expert entenderia?"

4. ✅ PAIR PROGRAMMING
   └─ Dev + Domain Expert juntos
   └─ Expert valida nomes em tempo real

5. ✅ TESTES COMO DOCUMENTAÇÃO
   └─ Nomes de testes em linguagem do domínio
   └─ Exemplo: shouldConfirmOrderWhenHasItemsAndCreditLimit


ANTI-PATTERNS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Termos técnicos no domínio
   └─ processData(), handleRequest(), doStuff()

❌ Abreviações
   └─ custId, ordQty, totAmt

❌ Siglas não explicadas
   └─ CRM, ERP, SKU (sem contexto)

❌ Tradução de termos
   └─ Expert: "aprovar" → Dev: "setStatusToOK()"

❌ Setter genérico
   └─ setStatus("CONFIRMED") ← use confirm()!
```

---

## 9. Bounded Contexts

### O que é Bounded Context

```
BOUNDED CONTEXT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFINIÇÃO:
  Fronteira explícita onde um modelo de domínio específico
  é válido. Dentro da fronteira, termos têm significado ÚNICO.

EXEMPLO REAL:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Palavra: "PRODUCT"

┌────────────────────────────────────────────┐
│  SALES CONTEXT (Vendas)                   │
├────────────────────────────────────────────┤
│  Product:                                  │
│  - productId                               │
│  - name                                    │
│  - price                                   │
│  - description                             │
│  - inStock()                               │
│                                            │
│  Foco: VENDER o produto                   │
└────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│  SHIPPING CONTEXT (Envio)                 │
├────────────────────────────────────────────┤
│  Product:                                  │
│  - productId                               │
│  - weight                                  │
│  - dimensions                              │
│  - fragile                                 │
│  - calculateShippingCost()                 │
│                                            │
│  Foco: ENVIAR o produto                   │
└────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│  INVENTORY CONTEXT (Estoque)              │
├────────────────────────────────────────────┤
│  Product:                                  │
│  - productId                               │
│  - quantityOnHand                          │
│  - reorderLevel                            │
│  - warehouseLocation                       │
│  - reserve()                               │
│                                            │
│  Foco: CONTROLAR estoque do produto       │
└────────────────────────────────────────────┘

MESMA PALAVRA ("Product"), SIGNIFICADOS DIFERENTES!
Cada contexto tem SUA própria model!
```

### Exemplo: E-commerce com Bounded Contexts

```
E-COMMERCE - BOUNDED CONTEXTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌─────────────────────────────────────────────┐
│         SALES CONTEXT                       │
│  (Contexto de Vendas)                       │
├─────────────────────────────────────────────┤
│  Aggregates:                                │
│  - Product (preço, descrição)               │
│  - ShoppingCart                             │
│  - Order                                    │
│                                             │
│  Use Cases:                                 │
│  - AddProductToCart                         │
│  - PlaceOrder                               │
│  - CalculateTotal                           │
│                                             │
│  Events:                                    │
│  - OrderPlaced                              │
│  - PaymentRequested                         │
└─────────────────────────────────────────────┘
                    │
                    │ OrderPlaced (event)
                    ↓
┌─────────────────────────────────────────────┐
│         PAYMENT CONTEXT                     │
│  (Contexto de Pagamentos)                   │
├─────────────────────────────────────────────┤
│  Aggregates:                                │
│  - Payment                                  │
│  - PaymentMethod                            │
│  - Transaction                              │
│                                             │
│  Use Cases:                                 │
│  - ProcessPayment                           │
│  - RefundPayment                            │
│  - ValidateCreditCard                       │
│                                             │
│  Events:                                    │
│  - PaymentApproved                          │
│  - PaymentFailed                            │
└─────────────────────────────────────────────┘
                    │
                    │ PaymentApproved (event)
                    ↓
┌─────────────────────────────────────────────┐
│         SHIPPING CONTEXT                    │
│  (Contexto de Envio)                        │
├─────────────────────────────────────────────┤
│  Aggregates:                                │
│  - Shipment                                 │
│  - Package                                  │
│  - DeliveryRoute                            │
│                                             │
│  Use Cases:                                 │
│  - CreateShipment                           │
│  - CalculateShippingCost                    │
│  - TrackPackage                             │
│                                             │
│  Events:                                    │
│  - ShipmentDispatched                       │
│  - PackageDelivered                         │
└─────────────────────────────────────────────┘
                    │
                    │ ShipmentDispatched (event)
                    ↓
┌─────────────────────────────────────────────┐
│         INVENTORY CONTEXT                   │
│  (Contexto de Estoque)                      │
├─────────────────────────────────────────────┤
│  Aggregates:                                │
│  - Product (quantidade, localização)        │
│  - Warehouse                                │
│  - StockLevel                               │
│                                             │
│  Use Cases:                                 │
│  - ReserveStock                             │
│  - ReplenishStock                           │
│  - TransferBetweenWarehouses                │
│                                             │
│  Events:                                    │
│  - StockReserved                            │
│  - ProductOutOfStock                        │
└─────────────────────────────────────────────┘


COMUNICAÇÃO ENTRE CONTEXTOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Via DOMAIN EVENTS (assíncrono)
✅ Via API REST (síncrono)
✅ Via Message Broker (Kafka, RabbitMQ)
❌ NÃO compartilham banco de dados!
❌ NÃO chamam código diretamente!
```

### Implementação: Bounded Context

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      SALES CONTEXT - Product
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.ecommerce.sales.domain;

/**
 * Product no SALES CONTEXT.
 * Foco: vender produto (preço, descrição).
 */
public class Product {

    private final ProductId productId;
    private final String name;
    private final Money price;
    private final String description;
    private boolean inStock;

    /**
     * ✅ Método do SALES context (verificar disponibilidade).
     */
    public boolean isAvailableForSale() {
        return inStock && price.isGreaterThan(Money.ZERO);
    }

    /**
     * ✅ Calcular preço com desconto.
     */
    public Money calculatePriceWithDiscount(Percentage discount) {
        return price.applyDiscount(discount);
    }

    // Getters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      SHIPPING CONTEXT - Product
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.ecommerce.shipping.domain;

/**
 * Product no SHIPPING CONTEXT.
 * Foco: enviar produto (peso, dimensões).
 *
 * ✅ DIFERENTE do Product do Sales Context!
 */
public class Product {

    private final ProductId productId;
    private final Weight weight;
    private final Dimensions dimensions;
    private final boolean fragile;

    /**
     * ✅ Método do SHIPPING context (calcular frete).
     */
    public Money calculateShippingCost(Address destination) {
        Money baseCost = weight.calculateBaseCost();

        if (fragile) {
            baseCost = baseCost.add(Money.of(10.00, "USD"));  // Taxa frágil
        }

        return baseCost;
    }

    /**
     * ✅ Verificar se precisa embalagem especial.
     */
    public boolean requiresSpecialPackaging() {
        return fragile || dimensions.isLarge();
    }

    // Getters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INVENTORY CONTEXT - Product
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.ecommerce.inventory.domain;

/**
 * Product no INVENTORY CONTEXT.
 * Foco: controlar estoque (quantidade, localização).
 *
 * ✅ DIFERENTE dos outros Product!
 */
public class Product {

    private final ProductId productId;
    private int quantityOnHand;
    private final int reorderLevel;
    private final String warehouseLocation;

    /**
     * ✅ Método do INVENTORY context (reservar estoque).
     */
    public void reserve(int quantity) {
        if (quantity > quantityOnHand) {
            throw new InsufficientStockException(
                "Not enough stock. Available: " + quantityOnHand +
                ", Requested: " + quantity
            );
        }

        this.quantityOnHand -= quantity;

        // ✅ Domain Event
        if (quantityOnHand <= reorderLevel) {
            registerEvent(new ProductLowStockEvent(productId, quantityOnHand));
        }
    }

    /**
     * ✅ Verificar se precisa reabastecimento.
     */
    public boolean needsReplenishment() {
        return quantityOnHand <= reorderLevel;
    }

    // Getters...
}


COMUNICAÇÃO ENTRE CONTEXTOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// SALES CONTEXT publica evento
@Service
public class PlaceOrderService {

    @Transactional
    public void placeOrder(Order order) {
        // ... lógica de vendas

        // ✅ Publicar evento para outros contextos
        eventPublisher.publish(new OrderPlacedEvent(
            order.getOrderId(),
            order.getItems(),  // ProductIds
            order.getTotal()
        ));
    }
}

// INVENTORY CONTEXT consome evento
@Component
public class OrderPlacedEventHandler {

    @EventHandler
    public void handle(OrderPlacedEvent event) {
        // ✅ Traduzir evento do SALES context para INVENTORY context

        for (OrderItemDto item : event.getItems()) {
            // Buscar Product no INVENTORY context
            Product product = productRepository.findById(
                new ProductId(item.getProductId())
            );

            // Reservar estoque
            product.reserve(item.getQuantity());

            productRepository.save(product);
        }
    }
}


BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ MODELOS INDEPENDENTES:
   └─ Product do Sales ≠ Product do Shipping
   └─ Cada um otimizado para seu contexto!

2. ✅ AUTONOMIA:
   └─ Sales Context muda sem afetar Shipping
   └─ Times trabalham independentes!

3. ✅ ESCALABILIDADE:
   └─ Inventory Context pode ter DB separado
   └─ Shipping Context pode ser microservice separado

4. ✅ CLAREZA:
   └─ Sales Product tem só o que Sales precisa
   └─ Shipping Product tem só o que Shipping precisa
```

---

## 10. DDD na Prática (Projeto Real)

### Projeto Completo: Sistema de Pagamento

Vamos ver DDD aplicado no projeto ms-producer!

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      VALUE OBJECTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// PaymentId (identidade)
public record PaymentId(String value) {
    public PaymentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentId cannot be null or blank");
        }
    }
}

// CustomerId (identidade)
public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId cannot be null or blank");
        }
    }
}

// Money (conceito do domínio)
public class Money {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        validateAmount(amount);
        this.amount = amount;
        this.currency = currency;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException();
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    // equals(), hashCode()...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ENTITY / AGGREGATE ROOT
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {  // Aggregate Root

    private final PaymentId paymentId;
    private final CustomerId customerId;
    private final Money amount;
    private PaymentStatus status;
    private final Instant createdAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

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

    // ✅ Comportamento (Ubiquitous Language!)
    public void approve() {
        if (status == PaymentStatus.CANCELLED) {
            throw new PaymentAlreadyCancelledException();
        }

        this.status = PaymentStatus.APPROVED;
        registerEvent(new PaymentApprovedEvent(this.paymentId, this.customerId, this.amount));
    }

    public void cancel(CancellationReason reason) {
        if (status == PaymentStatus.APPROVED) {
            throw new PaymentAlreadyApprovedException();
        }

        this.status = PaymentStatus.CANCELLED;
        registerEvent(new PaymentCancelledEvent(this.paymentId, reason));
    }

    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    // Getters...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN EVENTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentApprovedEvent(
    PaymentId paymentId,
    CustomerId customerId,
    Money amount,
    Instant occurredAt
) implements DomainEvent {
    public PaymentApprovedEvent(PaymentId paymentId, CustomerId customerId, Money amount) {
        this(paymentId, customerId, amount, Instant.now());
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      REPOSITORY (Interface no Domain)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId paymentId);
    List<Payment> findByCustomer(CustomerId customerId);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION SERVICE (Use Case)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void approvePayment(ApprovePaymentCommand command) {

        // 1. Criar Domain Model
        Payment payment = new Payment(
            new PaymentId(command.paymentId()),
            new CustomerId(command.customerId()),
            new Money(command.amount(), command.currency())
        );

        // 2. Executar lógica de domínio
        payment.approve();

        // 3. Persistir
        Payment saved = paymentRepository.save(payment);

        // 4. Publicar eventos
        saved.getDomainEvents().forEach(eventPublisher::publish);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE (Adapter)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Repository
class JpaPaymentRepository implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentMapper mapper;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapper.toEntity(payment);
        PaymentEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return jpaRepository.findById(paymentId.value())
            .map(mapper::toDomain);
    }
}


RESULTADO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Value Objects: Money, PaymentId, CustomerId
✅ Entity: Payment (Aggregate Root)
✅ Domain Events: PaymentApprovedEvent
✅ Repository: Interface no Domain, implementação na Infrastructure
✅ Application Service: Orquestra Use Case
✅ Ubiquitous Language: approve(), cancel() (não setStatus)
✅ Separação de camadas: Domain puro, Infrastructure isolada
```

---

## Conclusão

Parabéns! 🎉 Você domina Domain-Driven Design!

**O que você aprendeu:**
✅ Building Blocks Táticos (Entity, Value Object, Aggregate, etc)
✅ Entities vs Value Objects
✅ Aggregates e Aggregate Roots
✅ Domain Services
✅ Repositories (Port/Adapter)
✅ Domain Events
✅ Ubiquitous Language
✅ Bounded Contexts

**Lembre-se:**
> "DDD não é sobre código. É sobre entender profundamente
> o domínio do negócio e modelá-lo corretamente."

**Próximos passos:**
1. Aplique DDD em features reais do projeto
2. Faça Event Storming com domain experts
3. Construa glossário de termos do domínio
4. Leia: "Domain-Driven Design" (Eric Evans - Blue Book)
5. Leia: "Implementing Domain-Driven Design" (Vaughn Vernon - Red Book)

🚀 Agora construa software que reflete PERFEITAMENTE o negócio com DDD!
