# Tutorial Definitivo: Payment - Domínio Rico com Builder Pattern e DDD

## 📋 Sumário

1. [O que é Domínio Rico](#1-o-que-é-domínio-rico)
2. [Payment - Visão Geral do Domínio](#2-payment---visão-geral-do-domínio)
3. [Builder Pattern com DDD](#3-builder-pattern-com-ddd)
4. [Value Objects no Payment](#4-value-objects-no-payment)
5. [Implementação TDD do Payment - Passo a Passo](#5-implementação-tdd-do-payment---passo-a-passo)
6. [Validações de Domínio Ricas](#6-validações-de-domínio-ricas)
7. [Métodos Builder Avançados (with, from, of, add)](#7-métodos-builder-avançados-with-from-of-add)
8. [Testes Unitários Completos](#8-testes-unitários-completos)
9. [Invariantes de Domínio](#9-invariantes-de-domínio)
10. [Checklist e Boas Práticas](#10-checklist-e-boas-práticas)
11. [Exercícios Práticos](#11-exercícios-práticos)

---

## 1. O que é Domínio Rico

### Definição em 30 Segundos

**Domínio Rico** é uma abordagem onde as classes de domínio contêm **comportamento** e **regras de negócio**, não apenas dados. Em vez de objetos anêmicos (só getters/setters), temos entidades inteligentes que protegem suas próprias invariantes.

```
❌ DOMÍNIO ANÊMICO (Anti-Pattern)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {
    private String paymentId;
    private BigDecimal amount;
    private String status;

    // Apenas getters/setters (JavaBean)
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

// Lógica de negócio VAZA para serviços
@Service
public class PaymentService {
    public void approvePayment(Payment payment) {
        // ❌ Validações no serviço (deveria estar no domínio)
        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount invalid");
        }

        // ❌ Lógica de negócio no serviço
        payment.setStatus("APPROVED");
    }
}

PROBLEMAS:
├─ Payment não tem comportamento (só dados)
├─ Lógica de negócio espalhada (Service, Controller, etc)
├─ Fácil quebrar regras (qualquer um muda o estado)
├─ Difícil testar (precisa mock de tudo)
└─ Não reflete conceitos do domínio


✅ DOMÍNIO RICO (Correto!)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {
    private final PaymentId paymentId;
    private final Money amount;
    private PaymentStatus status;
    private final List<PaymentItem> items;

    // Construtor com validações
    private Payment(PaymentId paymentId, Money amount) {
        validatePaymentId(paymentId);
        validateAmount(amount);

        this.paymentId = paymentId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.items = new ArrayList<>();
    }

    // ✅ Comportamento: Aprovar pagamento
    public void approve() {
        validateCanApprove();
        this.status = PaymentStatus.APPROVED;
    }

    // ✅ Comportamento: Adicionar item
    public void addItem(PaymentItem item) {
        validateCanAddItem();
        this.items.add(item);
    }

    // ✅ Validações encapsuladas
    private void validateCanApprove() {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment already approved");
        }
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot approve payment without items");
        }
    }

    // ✅ Builder para construção fluente
    public static PaymentBuilder builder() {
        return new PaymentBuilder();
    }
}

BENEFÍCIOS:
├─ Lógica de negócio CENTRALIZADA no domínio
├─ Regras impossíveis de violar (encapsuladas)
├─ Fácil de testar (POJO puro)
├─ Reflete linguagem do negócio (Ubiquitous Language)
└─ Invariantes sempre válidas
```

### Por Que Domínio Rico?

| Aspecto | Domínio Anêmico ❌ | Domínio Rico ✅ |
|---------|-------------------|----------------|
| **Lógica de Negócio** | Espalhada (Service) | Centralizada (Domínio) |
| **Validações** | Esquecidas ou duplicadas | Garantidas no construtor |
| **Estado** | Qualquer um muda (setters) | Controlado (métodos) |
| **Testabilidade** | Difícil (precisa mocks) | Fácil (POJO puro) |
| **Manutenibilidade** | Difícil (buscar lógica) | Fácil (um só lugar) |
| **Invariantes** | Fácil violar | Sempre válidas |

---

## 2. Payment - Visão Geral do Domínio

### Contexto de Negócio

**Payment** representa um pagamento em um sistema de e-commerce. É uma **Aggregate Root** no DDD, responsável por garantir consistência de todas as operações relacionadas a pagamento.

### Ubiquitous Language (Linguagem Ubíqua)

```
TERMOS DO DOMÍNIO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Payment (Pagamento)
  └─ Aggregate Root que representa uma transação financeira

PaymentId (Identificador do Pagamento)
  └─ Value Object que identifica unicamente um pagamento

Money (Dinheiro)
  └─ Value Object que representa valor monetário (amount + currency)

PaymentItem (Item de Pagamento)
  └─ Entity que representa um item individual do pagamento

PaymentStatus (Status do Pagamento)
  └─ Enum: PENDING, APPROVED, CANCELLED, REFUNDED

PaymentMethod (Método de Pagamento)
  └─ Enum: CREDIT_CARD, DEBIT_CARD, PIX, BOLETO

Customer (Cliente)
  └─ Value Object que representa o cliente que está pagando

PaymentMetadata (Metadados do Pagamento)
  └─ Value Object com informações adicionais
```

### Regras de Negócio do Payment

```
INVARIANTES (sempre verdadeiras):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. PaymentId nunca é null ou vazio
2. Amount sempre é maior que zero
3. Currency nunca é null ou vazia
4. Customer nunca é null
5. Status inicial sempre é PENDING
6. Payment APPROVED não pode ser alterado para CANCELLED
7. Payment CANCELLED não pode ser APPROVED
8. Payment sem items não pode ser APPROVED
9. Refund só pode ser feito em payment APPROVED
10. Total calculado sempre corresponde à soma dos items


TRANSIÇÕES DE ESTADO PERMITIDAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PENDING → APPROVED     (approve())
PENDING → CANCELLED    (cancel())
APPROVED → REFUNDED    (refund())

PROIBIDO:
❌ APPROVED → CANCELLED
❌ CANCELLED → APPROVED
❌ REFUNDED → qualquer outro
❌ PENDING → REFUNDED (sem passar por APPROVED)


VALIDAÇÕES DE NEGÓCIO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Amount > 0
✅ Amount <= limite do cartão (ex: 50.000)
✅ Currency válida (USD, BRL, EUR)
✅ PaymentId no formato: "pay_" + UUID
✅ Customer tem ID e email válidos
✅ PaymentMethod compatível com Currency
✅ Items não podem ser adicionados após APPROVED
✅ Metadata pode conter no máximo 50 propriedades
```

### Diagrama do Aggregate Payment

```
┌─────────────────────────────────────────────────────────┐
│                    PAYMENT                              │
│                 (Aggregate Root)                        │
│─────────────────────────────────────────────────────────│
│ - paymentId: PaymentId         (Value Object)          │
│ - amount: Money                 (Value Object)          │
│ - status: PaymentStatus         (Enum)                  │
│ - customer: Customer            (Value Object)          │
│ - paymentMethod: PaymentMethod  (Enum)                  │
│ - items: List<PaymentItem>      (Entities)              │
│ - metadata: PaymentMetadata     (Value Object)          │
│ - createdAt: Instant                                    │
│ - approvedAt: Instant                                   │
│─────────────────────────────────────────────────────────│
│ + approve(): void                                       │
│ + cancel(): void                                        │
│ + refund(): void                                        │
│ + addItem(item): void                                   │
│ + removeItem(itemId): void                              │
│ + withMetadata(key, value): Payment                     │
│─────────────────────────────────────────────────────────│
│ + builder(): PaymentBuilder      (Factory Method)      │
│ + from(payment): PaymentBuilder  (Copy Builder)        │
│ + of(id, amount, customer): Payment (Named Constructor)│
└─────────────────────────────────────────────────────────┘
         │                          │
         │ contém                   │ contém
         ▼                          ▼
┌──────────────────┐      ┌──────────────────────┐
│  PaymentItem     │      │  PaymentMetadata     │
│   (Entity)       │      │  (Value Object)      │
│──────────────────│      │──────────────────────│
│ - itemId         │      │ - properties: Map    │
│ - description    │      │──────────────────────│
│ - quantity       │      │ + get(key)           │
│ - unitPrice      │      │ + put(key, value)    │
│──────────────────│      └──────────────────────┘
│ + calculateTotal()│
└──────────────────┘
```

---

## 3. Builder Pattern com DDD

### O que é Builder Pattern?

**Builder Pattern** é um padrão criacional que permite construir objetos complexos passo a passo de forma fluente e legível.

```
SEM BUILDER (Construtor com muitos parâmetros):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ❌ Difícil de ler, ordem dos parâmetros confusa
Payment payment = new Payment(
    "pay_123",           // paymentId
    new BigDecimal("100.00"),  // amount
    "USD",               // currency
    "cust_456",          // customerId
    "john@example.com",  // customerEmail
    "CREDIT_CARD",       // paymentMethod
    null,                // items (será adicionado depois?)
    null,                // metadata
    Instant.now()        // createdAt
);

PROBLEMAS:
├─ Ordem dos parâmetros não é óbvia
├─ Difícil adicionar novos campos (quebra API)
├─ Parâmetros opcionais = null (confuso)
├─ Sem validação durante construção
└─ Código pouco legível


COM BUILDER (Fluente e Legível):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Legível, auto-documentado, fluente
Payment payment = Payment.builder()
    .paymentId("pay_123")
    .amount(new BigDecimal("100.00"))
    .currency("USD")
    .customer(Customer.of("cust_456", "john@example.com"))
    .paymentMethod(PaymentMethod.CREDIT_CARD)
    .addItem(item1)
    .addItem(item2)
    .withMetadata("ip_address", "192.168.1.1")
    .withMetadata("user_agent", "Mozilla/5.0")
    .build();

BENEFÍCIOS:
├─ Auto-documentado (nomes dos métodos claros)
├─ Ordem flexível (qualquer ordem funciona)
├─ Extensível (adiciona campos sem quebrar API)
├─ Parâmetros opcionais naturais
├─ Validação acontece no build()
└─ Código muito legível
```

### Tipos de Métodos Builder

```
CONVENÇÕES DE NOMES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. builder() - Factory Method
   └─ Cria um novo builder vazio
   └─ Exemplo: Payment.builder()

2. of(...) - Named Constructor
   └─ Cria objeto com parâmetros essenciais
   └─ Exemplo: Money.of(amount, currency)

3. from(objeto) - Copy Builder
   └─ Cria builder a partir de objeto existente
   └─ Exemplo: Payment.from(existingPayment).withNewAmount(...)

4. with...(valor) - Setter Fluente
   └─ Define um campo no builder
   └─ Exemplo: builder.withAmount(100)

5. add...(elemento) - Adicionar à Coleção
   └─ Adiciona elemento a uma lista
   └─ Exemplo: builder.addItem(item)

6. build() - Construtor Final
   └─ Valida e cria o objeto
   └─ Exemplo: builder.build()


EXEMPLOS PRÁTICOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// 1. Criar do zero com builder()
Payment payment = Payment.builder()
    .paymentId("pay_123")
    .amount(Money.of("100.00", "USD"))
    .customer(customer)
    .build();

// 2. Criar com of() (construtor nomeado)
Money money = Money.of("100.00", "USD");
Customer customer = Customer.of("cust_123", "john@example.com");

// 3. Copiar e modificar com from()
Payment modified = Payment.from(originalPayment)
    .withMetadata("updated", "true")
    .build();

// 4. Adicionar items com add()
Payment payment = Payment.builder()
    .paymentId("pay_123")
    .amount(Money.of("200.00", "USD"))
    .customer(customer)
    .addItem(item1)    // Adiciona item 1
    .addItem(item2)    // Adiciona item 2
    .build();

// 5. Configurar com with()
Payment payment = Payment.builder()
    .paymentId("pay_123")
    .amount(Money.of("100.00", "USD"))
    .customer(customer)
    .withMetadata("source", "web")
    .withMetadata("campaign", "summer_sale")
    .build();
```

### Builder Pattern + DDD = Poder Máximo

```
PRINCÍPIOS DDD NO BUILDER:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ Aggregate Root define seu próprio Builder
   └─ Payment tem PaymentBuilder interno

2. ✅ Validações acontecem no build()
   └─ Garante que Payment criado é sempre válido

3. ✅ Construtor privado
   └─ Força uso do Builder (controle total)

4. ✅ Value Objects criados com of()
   └─ Money.of(), Customer.of(), PaymentId.of()

5. ✅ Imutabilidade preservada
   └─ Builder cria novo objeto, não modifica existente

6. ✅ Linguagem Ubíqua nos métodos
   └─ approve(), refund(), addItem() (termos do domínio)


EXEMPLO COMPLETO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {

    // Construtor PRIVADO (força uso do Builder)
    private Payment(PaymentBuilder builder) {
        this.paymentId = builder.paymentId;
        this.amount = builder.amount;
        this.customer = builder.customer;
        this.items = new ArrayList<>(builder.items);
        this.metadata = builder.metadata;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();

        // ✅ Validações no construtor
        validateInvariants();
    }

    // ✅ Factory Method
    public static PaymentBuilder builder() {
        return new PaymentBuilder();
    }

    // ✅ Copy Builder
    public static PaymentBuilder from(Payment payment) {
        return new PaymentBuilder()
            .paymentId(payment.paymentId)
            .amount(payment.amount)
            .customer(payment.customer)
            .items(payment.items)
            .metadata(payment.metadata);
    }

    // ✅ Named Constructor (caso simples)
    public static Payment of(PaymentId id, Money amount, Customer customer) {
        return Payment.builder()
            .paymentId(id)
            .amount(amount)
            .customer(customer)
            .build();
    }

    // Builder interno
    public static class PaymentBuilder {
        private PaymentId paymentId;
        private Money amount;
        private Customer customer;
        private List<PaymentItem> items = new ArrayList<>();
        private PaymentMetadata metadata = PaymentMetadata.empty();

        public PaymentBuilder paymentId(String id) {
            this.paymentId = PaymentId.of(id);
            return this;
        }

        public PaymentBuilder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public PaymentBuilder addItem(PaymentItem item) {
            this.items.add(item);
            return this;
        }

        public PaymentBuilder withMetadata(String key, String value) {
            this.metadata = this.metadata.with(key, value);
            return this;
        }

        // ✅ build() valida e cria
        public Payment build() {
            validateBeforeBuild();
            return new Payment(this);
        }

        private void validateBeforeBuild() {
            if (paymentId == null) {
                throw new IllegalStateException("PaymentId is required");
            }
            if (amount == null) {
                throw new IllegalStateException("Amount is required");
            }
            if (customer == null) {
                throw new IllegalStateException("Customer is required");
            }
        }
    }
}
```

---

## 4. Value Objects no Payment

### O que são Value Objects?

**Value Objects** são objetos imutáveis que representam conceitos do domínio através de seus **valores**, não de identidade. Dois Value Objects com os mesmos valores são considerados iguais.

```
ENTITY vs VALUE OBJECT:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

ENTITY (tem identidade):
  └─ Payment com id "pay_123" é DIFERENTE de Payment com id "pay_456"
  └─ Mesmo que tenham mesmo amount, customer, etc
  └─ Identidade importa!

VALUE OBJECT (sem identidade):
  └─ Money("100.00", "USD") é IGUAL a Money("100.00", "USD")
  └─ Não importa QUAL instância, valores são iguais
  └─ Valor importa, não identidade!


CARACTERÍSTICAS DE VALUE OBJECT:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Imutável (final fields, sem setters)
✅ Igualdade por valor (equals/hashCode)
✅ Sem identidade própria
✅ Valida no construtor (fail-fast)
✅ Pode ter comportamento (métodos)
✅ Substituível (pode trocar instância)
```

### Value Object: Money

```java
/**
 * Money - Value Object que representa dinheiro.
 *
 * Encapsula amount + currency e garante invariantes.
 */
public final class Money {

    private final BigDecimal amount;
    private final Currency currency;

    // Construtor privado (força uso de of())
    private Money(BigDecimal amount, Currency currency) {
        validateAmount(amount);
        validateCurrency(currency);

        this.amount = amount;
        this.currency = currency;
    }

    /**
     * ✅ Named Constructor (of)
     * Forma preferida de criar Money.
     */
    public static Money of(String amount, String currencyCode) {
        return new Money(
            new BigDecimal(amount),
            Currency.getInstance(currencyCode)
        );
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * ✅ Convenience method para USD
     */
    public static Money usd(String amount) {
        return of(amount, "USD");
    }

    public static Money brl(String amount) {
        return of(amount, "BRL");
    }

    /**
     * ✅ Comportamento: somar dinheiro
     */
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(
            this.amount.add(other.amount),
            this.currency
        );
    }

    /**
     * ✅ Comportamento: subtrair dinheiro
     */
    public Money subtract(Money other) {
        validateSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Subtraction would result in negative amount"
            );
        }

        return new Money(result, this.currency);
    }

    /**
     * ✅ Comportamento: multiplicar por quantidade
     */
    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return new Money(
            this.amount.multiply(BigDecimal.valueOf(quantity)),
            this.currency
        );
    }

    /**
     * ✅ Comportamento: comparações
     */
    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    // Validações
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    private void validateCurrency(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
    }

    private void validateSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot operate on different currencies: " +
                this.currency + " and " + other.currency
            );
        }
    }

    // Getters (sem setters = imutável)
    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getCurrencyCode() {
        return currency.getCurrencyCode();
    }

    /**
     * ✅ Igualdade por VALOR (não identidade)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 &&
               currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + " " + amount;
    }
}
```

### Value Object: PaymentId

```java
/**
 * PaymentId - Value Object que identifica um Payment.
 *
 * Formato: "pay_" + UUID
 */
public final class PaymentId {

    private static final String PREFIX = "pay_";
    private static final Pattern PATTERN = Pattern.compile("^pay_[a-f0-9\\-]{36}$");

    private final String value;

    private PaymentId(String value) {
        validateFormat(value);
        this.value = value;
    }

    /**
     * ✅ Named Constructor - criar com valor específico
     */
    public static PaymentId of(String value) {
        return new PaymentId(value);
    }

    /**
     * ✅ Named Constructor - gerar novo ID
     */
    public static PaymentId generate() {
        return new PaymentId(PREFIX + UUID.randomUUID().toString());
    }

    /**
     * ✅ Named Constructor - criar a partir de UUID
     */
    public static PaymentId fromUuid(UUID uuid) {
        return new PaymentId(PREFIX + uuid.toString());
    }

    private void validateFormat(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentId cannot be null or blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "PaymentId must match format: pay_<uuid>, got: " + value
            );
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentId that = (PaymentId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
```

### Value Object: Customer

```java
/**
 * Customer - Value Object que representa um cliente.
 *
 * Contém customerId e email.
 */
public final class Customer {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String customerId;
    private final String email;

    private Customer(String customerId, String email) {
        validateCustomerId(customerId);
        validateEmail(email);

        this.customerId = customerId;
        this.email = email.toLowerCase();
    }

    /**
     * ✅ Named Constructor
     */
    public static Customer of(String customerId, String email) {
        return new Customer(customerId, email);
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("CustomerId cannot be null or blank");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return customerId.equals(customer.customerId) &&
               email.equals(customer.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, email);
    }

    @Override
    public String toString() {
        return "Customer{id=" + customerId + ", email=" + email + "}";
    }
}
```

### Value Object: PaymentMetadata

```java
/**
 * PaymentMetadata - Value Object para metadados do pagamento.
 *
 * Permite armazenar pares chave-valor adicionais.
 */
public final class PaymentMetadata {

    private static final int MAX_PROPERTIES = 50;
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 1000;

    private final Map<String, String> properties;

    private PaymentMetadata(Map<String, String> properties) {
        validateProperties(properties);
        // Cópia defensiva + imutável
        this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
    }

    /**
     * ✅ Named Constructor - vazio
     */
    public static PaymentMetadata empty() {
        return new PaymentMetadata(Collections.emptyMap());
    }

    /**
     * ✅ Named Constructor - com propriedades iniciais
     */
    public static PaymentMetadata of(Map<String, String> properties) {
        return new PaymentMetadata(properties);
    }

    /**
     * ✅ Método with - adicionar nova propriedade (retorna NOVO objeto)
     */
    public PaymentMetadata with(String key, String value) {
        validateKey(key);
        validateValue(value);

        Map<String, String> newProperties = new HashMap<>(this.properties);
        newProperties.put(key, value);

        return new PaymentMetadata(newProperties);
    }

    /**
     * ✅ Método without - remover propriedade (retorna NOVO objeto)
     */
    public PaymentMetadata without(String key) {
        if (!properties.containsKey(key)) {
            return this; // Não muda, retorna o mesmo
        }

        Map<String, String> newProperties = new HashMap<>(this.properties);
        newProperties.remove(key);

        return new PaymentMetadata(newProperties);
    }

    /**
     * ✅ Comportamento - obter valor
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    /**
     * ✅ Comportamento - verificar existência
     */
    public boolean has(String key) {
        return properties.containsKey(key);
    }

    /**
     * ✅ Comportamento - verificar vazio
     */
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    public int size() {
        return properties.size();
    }

    // Validações
    private void validateProperties(Map<String, String> properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }
        if (properties.size() > MAX_PROPERTIES) {
            throw new IllegalArgumentException(
                "Metadata cannot have more than " + MAX_PROPERTIES + " properties"
            );
        }
        properties.forEach((key, value) -> {
            validateKey(key);
            validateValue(value);
        });
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Metadata key cannot be null or blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "Metadata key cannot exceed " + MAX_KEY_LENGTH + " characters"
            );
        }
    }

    private void validateValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Metadata value cannot be null");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                "Metadata value cannot exceed " + MAX_VALUE_LENGTH + " characters"
            );
        }
    }

    public Map<String, String> toMap() {
        return new HashMap<>(properties); // Cópia defensiva
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentMetadata that = (PaymentMetadata) o;
        return properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        return "PaymentMetadata" + properties;
    }
}
```

---

## 5. Implementação TDD do Payment - Passo a Passo

Agora vamos construir a classe **Payment** completa usando **TDD** (Test-Driven Development) + **Builder Pattern** + **DDD**.

### Estrutura do que vamos construir

```
PAYMENT - Aggregate Root
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Campos:
├─ paymentId: PaymentId (Value Object)
├─ amount: Money (Value Object)
├─ status: PaymentStatus (Enum)
├─ customer: Customer (Value Object)
├─ paymentMethod: PaymentMethod (Enum)
├─ items: List<PaymentItem> (Entities)
├─ metadata: PaymentMetadata (Value Object)
├─ createdAt: Instant
├─ approvedAt: Instant
└─ cancelledAt: Instant

Comportamentos:
├─ approve() - Aprovar pagamento
├─ cancel() - Cancelar pagamento
├─ refund() - Reembolsar pagamento
├─ addItem(item) - Adicionar item
├─ removeItem(itemId) - Remover item
├─ withMetadata(key, value) - Adicionar metadado
└─ calculateTotal() - Calcular total

Builder Methods:
├─ builder() - Factory method
├─ from(payment) - Copy builder
├─ of(id, amount, customer) - Named constructor
└─ PaymentBuilder - Inner class
```

### PASSO 1: Criar Enum PaymentStatus

```java
// src/main/java/com/mvbr/store/domain/model/payment/PaymentStatus.java

/**
 * PaymentStatus - Estados possíveis de um Payment.
 */
public enum PaymentStatus {
    /**
     * Pagamento criado, aguardando aprovação.
     */
    PENDING,

    /**
     * Pagamento aprovado e processado.
     */
    APPROVED,

    /**
     * Pagamento cancelado.
     */
    CANCELLED,

    /**
     * Pagamento reembolsado (após aprovação).
     */
    REFUNDED
}
```

### PASSO 2: Criar Enum PaymentMethod

```java
// src/main/java/com/mvbr/store/domain/model/payment/PaymentMethod.java

/**
 * PaymentMethod - Métodos de pagamento suportados.
 */
public enum PaymentMethod {
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    PIX("PIX"),
    BOLETO("Boleto Bancário"),
    PAYPAL("PayPal");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### PASSO 3: Criar PaymentItem Entity

```java
// src/main/java/com/mvbr/store/domain/model/payment/PaymentItem.java

/**
 * PaymentItem - Entity que representa um item do pagamento.
 *
 * Tem identidade própria (itemId).
 */
public class PaymentItem {

    private final String itemId;
    private final String description;
    private final int quantity;
    private final Money unitPrice;

    private PaymentItem(String itemId, String description, int quantity, Money unitPrice) {
        validateItemId(itemId);
        validateDescription(description);
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);

        this.itemId = itemId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * ✅ Named Constructor
     */
    public static PaymentItem of(String itemId, String description, int quantity, Money unitPrice) {
        return new PaymentItem(itemId, description, quantity, unitPrice);
    }

    /**
     * ✅ Comportamento - Calcular total do item
     */
    public Money calculateTotal() {
        return unitPrice.multiply(quantity);
    }

    // Validações
    private void validateItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("ItemId cannot be null or blank");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
        if (description.length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (quantity > 10000) {
            throw new IllegalArgumentException("Quantity cannot exceed 10000");
        }
    }

    private void validateUnitPrice(Money unitPrice) {
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unit price cannot be null");
        }
        if (!unitPrice.isPositive()) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
    }

    // Getters
    public String getItemId() {
        return itemId;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    /**
     * ✅ Igualdade por IDENTIDADE (itemId)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentItem that = (PaymentItem) o;
        return itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "PaymentItem{" +
               "id='" + itemId + '\'' +
               ", description='" + description + '\'' +
               ", quantity=" + quantity +
               ", unitPrice=" + unitPrice +
               ", total=" + calculateTotal() +
               '}';
    }
}
```

### PASSO 4: Começar TDD do Payment - Teste 1

```java
// src/test/java/com/mvbr/store/domain/model/payment/PaymentTest.java

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/**
 * Testes TDD para Payment (Domínio Rico com Builder).
 */
@DisplayName("Payment - Domain Model Tests")
class PaymentTest {

    // ═══════════════════════════════════════════════════
    //      TESTE 1: Criar Payment com Builder
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("Should create payment using builder with required fields")
    void shouldCreatePaymentUsingBuilder() {
        // Given
        PaymentId paymentId = PaymentId.generate();
        Money amount = Money.usd("100.00");
        Customer customer = Customer.of("cust_123", "john@example.com");

        // When
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .amount(amount)
            .customer(customer)
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .build();

        // Then
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentId()).isEqualTo(paymentId);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getCustomer()).isEqualTo(customer);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCreatedAt()).isNotNull();
    }
}
```

### PASSO 5: Implementar Payment - Código Mínimo

```java
// src/main/java/com/mvbr/store/domain/model/payment/Payment.java

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Payment - Aggregate Root que representa um pagamento.
 *
 * Implementa Builder Pattern + DDD + Rich Domain Model.
 */
public class Payment {

    // ═══════════════════════════════════════════════════
    //      CAMPOS (Imutáveis quando possível)
    // ═══════════════════════════════════════════════════

    private final PaymentId paymentId;
    private final Money amount;
    private final Customer customer;
    private final PaymentMethod paymentMethod;
    private final List<PaymentItem> items;
    private final PaymentMetadata metadata;

    private PaymentStatus status;
    private final Instant createdAt;
    private Instant approvedAt;
    private Instant cancelledAt;
    private Instant refundedAt;

    // ═══════════════════════════════════════════════════
    //      CONSTRUTOR PRIVADO (força uso do Builder)
    // ═══════════════════════════════════════════════════

    private Payment(PaymentBuilder builder) {
        this.paymentId = builder.paymentId;
        this.amount = builder.amount;
        this.customer = builder.customer;
        this.paymentMethod = builder.paymentMethod;
        this.items = new ArrayList<>(builder.items);
        this.metadata = builder.metadata;

        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.approvedAt = null;
        this.cancelledAt = null;
        this.refundedAt = null;

        // Validar invariantes
        validateInvariants();
    }

    // ═══════════════════════════════════════════════════
    //      FACTORY METHODS
    // ═══════════════════════════════════════════════════

    /**
     * ✅ Factory Method - criar builder vazio
     */
    public static PaymentBuilder builder() {
        return new PaymentBuilder();
    }

    /**
     * ✅ Copy Builder - criar builder a partir de payment existente
     */
    public static PaymentBuilder from(Payment payment) {
        return new PaymentBuilder()
            .paymentId(payment.paymentId)
            .amount(payment.amount)
            .customer(payment.customer)
            .paymentMethod(payment.paymentMethod)
            .items(payment.items)
            .metadata(payment.metadata);
    }

    /**
     * ✅ Named Constructor - criar payment simples
     */
    public static Payment of(PaymentId paymentId, Money amount, Customer customer) {
        return Payment.builder()
            .paymentId(paymentId)
            .amount(amount)
            .customer(customer)
            .paymentMethod(PaymentMethod.CREDIT_CARD) // Default
            .build();
    }

    // ═══════════════════════════════════════════════════
    //      COMPORTAMENTOS (Regras de Negócio)
    // ═══════════════════════════════════════════════════

    /**
     * ✅ Aprovar pagamento.
     *
     * Regras:
     * - Status deve ser PENDING
     * - Deve ter pelo menos 1 item
     */
    public void approve() {
        validateCanApprove();
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = Instant.now();
    }

    /**
     * ✅ Cancelar pagamento.
     *
     * Regras:
     * - Status deve ser PENDING
     * - Não pode cancelar se já APPROVED
     */
    public void cancel() {
        validateCanCancel();
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    /**
     * ✅ Reembolsar pagamento.
     *
     * Regras:
     * - Status deve ser APPROVED
     */
    public void refund() {
        validateCanRefund();
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Instant.now();
    }

    /**
     * ✅ Adicionar item ao pagamento.
     *
     * Regras:
     * - Status deve ser PENDING
     * - Item não pode ser null
     */
    public void addItem(PaymentItem item) {
        validateCanAddItem();

        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        this.items.add(item);
    }

    /**
     * ✅ Remover item do pagamento.
     *
     * Regras:
     * - Status deve ser PENDING
     * - Item deve existir
     */
    public void removeItem(String itemId) {
        validateCanRemoveItem();

        boolean removed = items.removeIf(item -> item.getItemId().equals(itemId));

        if (!removed) {
            throw new IllegalArgumentException("Item not found: " + itemId);
        }
    }

    /**
     * ✅ Calcular total do pagamento.
     *
     * Total = soma de todos os items
     */
    public Money calculateTotal() {
        if (items.isEmpty()) {
            return Money.of(BigDecimal.ZERO, amount.getCurrency());
        }

        return items.stream()
            .map(PaymentItem::calculateTotal)
            .reduce(Money::add)
            .orElse(Money.of(BigDecimal.ZERO, amount.getCurrency()));
    }

    // ═══════════════════════════════════════════════════
    //      VALIDAÇÕES DE REGRAS DE NEGÓCIO
    // ═══════════════════════════════════════════════════

    private void validateInvariants() {
        if (paymentId == null) {
            throw new IllegalStateException("PaymentId cannot be null");
        }
        if (amount == null) {
            throw new IllegalStateException("Amount cannot be null");
        }
        if (!amount.isPositive()) {
            throw new IllegalStateException("Amount must be positive");
        }
        if (customer == null) {
            throw new IllegalStateException("Customer cannot be null");
        }
        if (paymentMethod == null) {
            throw new IllegalStateException("PaymentMethod cannot be null");
        }
    }

    private void validateCanApprove() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot approve payment with status: " + status
            );
        }
        if (items.isEmpty()) {
            throw new IllegalStateException(
                "Cannot approve payment without items"
            );
        }
    }

    private void validateCanCancel() {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot cancel approved payment"
            );
        }
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                "Payment already cancelled"
            );
        }
        if (status == PaymentStatus.REFUNDED) {
            throw new IllegalStateException(
                "Cannot cancel refunded payment"
            );
        }
    }

    private void validateCanRefund() {
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Can only refund approved payments, current status: " + status
            );
        }
    }

    private void validateCanAddItem() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Can only add items to pending payments, current status: " + status
            );
        }
    }

    private void validateCanRemoveItem() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Can only remove items from pending payments, current status: " + status
            );
        }
    }

    // ═══════════════════════════════════════════════════
    //      GETTERS (sem setters = imutabilidade)
    // ═══════════════════════════════════════════════════

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * ✅ Retorna cópia imutável da lista
     */
    public List<PaymentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public PaymentMetadata getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    /**
     * ✅ Query methods (comportamento de consulta)
     */
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public boolean isCancelled() {
        return status == PaymentStatus.CANCELLED;
    }

    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public int getItemCount() {
        return items.size();
    }

    // ═══════════════════════════════════════════════════
    //      PAYMENT BUILDER (Inner Class)
    // ═══════════════════════════════════════════════════

    public static class PaymentBuilder {

        private PaymentId paymentId;
        private Money amount;
        private Customer customer;
        private PaymentMethod paymentMethod;
        private List<PaymentItem> items = new ArrayList<>();
        private PaymentMetadata metadata = PaymentMetadata.empty();

        // Construtor privado (só Payment pode criar)
        private PaymentBuilder() {
        }

        /**
         * ✅ Setter fluente - PaymentId
         */
        public PaymentBuilder paymentId(PaymentId paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public PaymentBuilder paymentId(String paymentId) {
            this.paymentId = PaymentId.of(paymentId);
            return this;
        }

        /**
         * ✅ Setter fluente - Amount
         */
        public PaymentBuilder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public PaymentBuilder amount(String amount, String currency) {
            this.amount = Money.of(amount, currency);
            return this;
        }

        /**
         * ✅ Setter fluente - Customer
         */
        public PaymentBuilder customer(Customer customer) {
            this.customer = customer;
            return this;
        }

        public PaymentBuilder customer(String customerId, String email) {
            this.customer = Customer.of(customerId, email);
            return this;
        }

        /**
         * ✅ Setter fluente - PaymentMethod
         */
        public PaymentBuilder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        /**
         * ✅ Adicionar item (add)
         */
        public PaymentBuilder addItem(PaymentItem item) {
            if (item != null) {
                this.items.add(item);
            }
            return this;
        }

        /**
         * ✅ Adicionar múltiplos items
         */
        public PaymentBuilder items(List<PaymentItem> items) {
            if (items != null) {
                this.items = new ArrayList<>(items);
            }
            return this;
        }

        /**
         * ✅ Adicionar metadado (with)
         */
        public PaymentBuilder withMetadata(String key, String value) {
            this.metadata = this.metadata.with(key, value);
            return this;
        }

        /**
         * ✅ Definir metadata completo
         */
        public PaymentBuilder metadata(PaymentMetadata metadata) {
            if (metadata != null) {
                this.metadata = metadata;
            }
            return this;
        }

        /**
         * ✅ BUILD - valida e cria Payment
         */
        public Payment build() {
            validateRequiredFields();
            return new Payment(this);
        }

        private void validateRequiredFields() {
            if (paymentId == null) {
                throw new IllegalStateException("PaymentId is required");
            }
            if (amount == null) {
                throw new IllegalStateException("Amount is required");
            }
            if (customer == null) {
                throw new IllegalStateException("Customer is required");
            }
            if (paymentMethod == null) {
                throw new IllegalStateException("PaymentMethod is required");
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //      EQUALS / HASHCODE (por identidade)
    // ═══════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return paymentId.equals(payment.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId);
    }

    @Override
    public String toString() {
        return "Payment{" +
               "id=" + paymentId +
               ", amount=" + amount +
               ", status=" + status +
               ", customer=" + customer +
               ", method=" + paymentMethod +
               ", items=" + items.size() +
               ", createdAt=" + createdAt +
               '}';
    }
}
```

---

## 6. Validações de Domínio Ricas

### Por Que Validações no Domínio?

```
VALIDAÇÕES NO LUGAR CERTO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ ERRADO - Validações no Controller/Service:

@RestController
public class PaymentController {

    @PostMapping("/payments")
    public Payment create(@RequestBody PaymentRequest request) {
        // ❌ Validação no controller
        if (request.amount <= 0) {
            throw new BadRequestException("Invalid amount");
        }

        // ❌ Regra de negócio no controller
        if (request.items.isEmpty()) {
            throw new BadRequestException("Need items");
        }

        Payment payment = new Payment();
        payment.setAmount(request.amount);
        // ... pode violar invariantes!
    }
}

PROBLEMAS:
├─ Validações duplicadas em múltiplos lugares
├─ Fácil esquecer validações
├─ Domínio pode ficar em estado inválido
└─ Difícil de testar (precisa simular HTTP)


✅ CORRETO - Validações no Domínio:

@RestController
public class PaymentController {

    @PostMapping("/payments")
    public Payment create(@RequestBody PaymentRequest request) {
        // ✅ Domínio valida tudo!
        Payment payment = Payment.builder()
            .paymentId(PaymentId.generate())
            .amount(Money.of(request.amount, request.currency))
            .customer(Customer.of(request.customerId, request.email))
            .paymentMethod(request.paymentMethod)
            .build(); // ← Validações acontecem aqui!

        // Se chegou aqui, payment é VÁLIDO!
        return paymentService.save(payment);
    }
}

BENEFÍCIOS:
├─ Validações centralizadas (um só lugar)
├─ Impossível esquecer (construtor força)
├─ Domínio SEMPRE válido (invariantes garantidas)
└─ Fácil de testar (POJO puro)
```

### Tipos de Validações

```
1. VALIDAÇÕES ESTRUTURAIS (formato, tipo):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Campo não-null
✅ Campo não-vazio/blank
✅ Formato válido (regex)
✅ Tipo correto (BigDecimal, não double)
✅ Limites de tamanho (min/max length)

Exemplo:
private void validatePaymentId(PaymentId paymentId) {
    if (paymentId == null) {
        throw new IllegalArgumentException("PaymentId cannot be null");
    }
}


2. VALIDAÇÕES DE DOMÍNIO (regras de negócio):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Amount > 0
✅ Amount <= limite máximo
✅ Email no formato correto
✅ Payment com items antes de aprovar
✅ Transições de estado válidas

Exemplo:
private void validateCanApprove() {
    if (status != PaymentStatus.PENDING) {
        throw new IllegalStateException("Cannot approve non-pending payment");
    }
    if (items.isEmpty()) {
        throw new IllegalStateException("Cannot approve without items");
    }
}


3. VALIDAÇÕES DE INVARIANTES (sempre verdadeiras):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ PaymentId nunca null após construção
✅ Amount sempre positivo
✅ Status sempre em estado válido
✅ Timestamps consistentes (approvedAt >= createdAt)

Exemplo:
private void validateInvariants() {
    assert paymentId != null : "PaymentId should never be null";
    assert amount != null : "Amount should never be null";
    assert amount.isPositive() : "Amount should always be positive";
}
```

### Validações Avançadas no Payment

```java
/**
 * ✅ Validação: Amount deve estar dentro de limites
 */
private void validateAmountLimits(Money amount) {
    Money minAmount = Money.of("0.01", amount.getCurrencyCode());
    Money maxAmount = Money.of("50000.00", amount.getCurrencyCode());

    if (amount.isLessThan(minAmount)) {
        throw new IllegalArgumentException(
            "Amount cannot be less than " + minAmount
        );
    }

    if (amount.isGreaterThan(maxAmount)) {
        throw new IllegalArgumentException(
            "Amount cannot exceed " + maxAmount
        );
    }
}

/**
 * ✅ Validação: Método de pagamento compatível com moeda
 */
private void validatePaymentMethodCurrency(PaymentMethod method, Money amount) {
    String currency = amount.getCurrencyCode();

    // PIX só funciona com BRL
    if (method == PaymentMethod.PIX && !currency.equals("BRL")) {
        throw new IllegalArgumentException(
            "PIX payment method only supports BRL currency"
        );
    }

    // BOLETO só funciona com BRL
    if (method == PaymentMethod.BOLETO && !currency.equals("BRL")) {
        throw new IllegalArgumentException(
            "BOLETO payment method only supports BRL currency"
        );
    }
}

/**
 * ✅ Validação: Items devem ter mesma moeda do payment
 */
private void validateItemsCurrency(Money paymentAmount, List<PaymentItem> items) {
    String expectedCurrency = paymentAmount.getCurrencyCode();

    for (PaymentItem item : items) {
        String itemCurrency = item.getUnitPrice().getCurrencyCode();

        if (!itemCurrency.equals(expectedCurrency)) {
            throw new IllegalArgumentException(
                String.format(
                    "Item '%s' has currency %s but payment has %s",
                    item.getItemId(),
                    itemCurrency,
                    expectedCurrency
                )
            );
        }
    }
}

/**
 * ✅ Validação: Total dos items deve corresponder ao amount
 */
private void validateTotalMatchesAmount(Money paymentAmount, List<PaymentItem> items) {
    if (items.isEmpty()) {
        return; // OK, items opcionais
    }

    Money calculatedTotal = items.stream()
        .map(PaymentItem::calculateTotal)
        .reduce(Money::add)
        .orElse(Money.of("0", paymentAmount.getCurrencyCode()));

    if (!calculatedTotal.equals(paymentAmount)) {
        throw new IllegalArgumentException(
            String.format(
                "Payment amount (%s) does not match sum of items (%s)",
                paymentAmount,
                calculatedTotal
            )
        );
    }
}

/**
 * ✅ Validação: Timestamps devem ser consistentes
 */
private void validateTimestamps() {
    if (approvedAt != null && approvedAt.isBefore(createdAt)) {
        throw new IllegalStateException(
            "ApprovedAt cannot be before createdAt"
        );
    }

    if (cancelledAt != null && cancelledAt.isBefore(createdAt)) {
        throw new IllegalStateException(
            "CancelledAt cannot be before createdAt"
        );
    }
}
```

---

## 7. Métodos Builder Avançados (with, from, of, add)

### Padrão "with" - Criar Nova Instância com Modificação

```java
/**
 * ✅ Método "with" - Cria novo Payment com metadata adicional
 *
 * Imutável: não modifica o Payment original!
 */
public Payment withMetadata(String key, String value) {
    return Payment.from(this)
        .metadata(this.metadata.with(key, value))
        .build();
}

/**
 * ✅ Método "with" - Cria novo Payment com item adicional
 */
public Payment withItem(PaymentItem item) {
    validateCanAddItem();

    return Payment.from(this)
        .addItem(item)
        .build();
}

/**
 * ✅ Método "with" - Cria novo Payment sem um item
 */
public Payment withoutItem(String itemId) {
    List<PaymentItem> filteredItems = items.stream()
        .filter(item -> !item.getItemId().equals(itemId))
        .toList();

    return Payment.from(this)
        .items(filteredItems)
        .build();
}

// Uso:
Payment original = Payment.builder()
    .paymentId(PaymentId.generate())
    .amount(Money.usd("100"))
    .customer(customer)
    .paymentMethod(PaymentMethod.CREDIT_CARD)
    .build();

// ✅ Cria NOVO payment com metadata (original não muda!)
Payment withMeta = original.withMetadata("source", "web");

// ✅ Cria NOVO payment com item
Payment withItem = original.withItem(item1);
```

### Padrão "from" - Copy Builder

```java
/**
 * ✅ Copy Builder - criar builder a partir de payment existente
 */
public static PaymentBuilder from(Payment payment) {
    return new PaymentBuilder()
        .paymentId(payment.paymentId)
        .amount(payment.amount)
        .customer(payment.customer)
        .paymentMethod(payment.paymentMethod)
        .items(payment.items)
        .metadata(payment.metadata);
}

// Uso - Clonar e modificar:
Payment original = createPayment();

Payment modified = Payment.from(original)
    .amount(Money.usd("200")) // Muda amount
    .addItem(newItem)          // Adiciona item
    .build();

// original permanece inalterado!
```

### Padrão "of" - Named Constructors

```java
/**
 * ✅ Named Constructor - criar payment simples
 */
public static Payment of(PaymentId id, Money amount, Customer customer) {
    return Payment.builder()
        .paymentId(id)
        .amount(amount)
        .customer(customer)
        .paymentMethod(PaymentMethod.CREDIT_CARD) // Default
        .build();
}

/**
 * ✅ Named Constructor - criar payment PIX
 */
public static Payment pix(Money amount, Customer customer) {
    return Payment.builder()
        .paymentId(PaymentId.generate())
        .amount(amount)
        .customer(customer)
        .paymentMethod(PaymentMethod.PIX)
        .build();
}

/**
 * ✅ Named Constructor - criar payment com items
 */
public static Payment withItems(
    PaymentId id,
    Money amount,
    Customer customer,
    List<PaymentItem> items
) {
    PaymentBuilder builder = Payment.builder()
        .paymentId(id)
        .amount(amount)
        .customer(customer)
        .paymentMethod(PaymentMethod.CREDIT_CARD);

    items.forEach(builder::addItem);

    return builder.build();
}

// Uso:
Payment simple = Payment.of(id, amount, customer);
Payment pixPayment = Payment.pix(amount, customer);
Payment withItems = Payment.withItems(id, amount, customer, items);
```

### Padrão "add" - Adicionar a Coleções

```java
/**
 * ✅ Builder: adicionar item (fluente)
 */
public PaymentBuilder addItem(PaymentItem item) {
    if (item != null) {
        this.items.add(item);
    }
    return this;
}

/**
 * ✅ Builder: adicionar item com parâmetros
 */
public PaymentBuilder addItem(
    String itemId,
    String description,
    int quantity,
    Money unitPrice
) {
    PaymentItem item = PaymentItem.of(itemId, description, quantity, unitPrice);
    return addItem(item);
}

/**
 * ✅ Builder: adicionar múltiplos items
 */
public PaymentBuilder addItems(List<PaymentItem> items) {
    if (items != null) {
        this.items.addAll(items);
    }
    return this;
}

// Uso:
Payment payment = Payment.builder()
    .paymentId(id)
    .amount(amount)
    .customer(customer)
    .paymentMethod(PaymentMethod.CREDIT_CARD)
    .addItem(item1)
    .addItem(item2)
    .addItem("item-3", "Product 3", 2, Money.usd("25"))
    .build();
```

### Exemplo Completo: Todos os Padrões Juntos

```java
// ✅ of() - Named Constructor
Payment payment = Payment.of(
    PaymentId.generate(),
    Money.usd("100"),
    Customer.of("cust_123", "john@example.com")
);

// ✅ from() - Copy Builder
Payment modified = Payment.from(payment)
    .amount(Money.usd("200"))
    .build();

// ✅ with() - Adicionar metadata (imutável)
Payment withMeta = payment
    .withMetadata("source", "mobile")
    .withMetadata("campaign", "black_friday");

// ✅ add() - Adicionar items (fluente)
Payment complete = Payment.builder()
    .paymentId(PaymentId.generate())
    .amount(Money.usd("300"))
    .customer(Customer.of("cust_456", "jane@example.com"))
    .paymentMethod(PaymentMethod.CREDIT_CARD)
    .addItem("item-1", "Product 1", 2, Money.usd("50"))
    .addItem("item-2", "Product 2", 1, Money.usd("200"))
    .withMetadata("ip", "192.168.1.1")
    .withMetadata("device", "iPhone")
    .build();
```

---

## 8. Testes Unitários Completos

### Estrutura de Testes

```
ORGANIZAÇÃO DOS TESTES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PaymentTest.java
├─ Construção
│  ├─ shouldCreatePaymentWithBuilder
│  ├─ shouldCreatePaymentWithOf
│  ├─ shouldCreatePaymentWithFrom
│  └─ shouldFailWhenMissingRequiredFields
│
├─ Validações
│  ├─ shouldRejectNullPaymentId
│  ├─ shouldRejectNullAmount
│  ├─ shouldRejectNegativeAmount
│  ├─ shouldRejectNullCustomer
│  └─ shouldRejectInvalidPaymentMethod
│
├─ Comportamento - Aprovar
│  ├─ shouldApprovePaymentWithItems
│  ├─ shouldNotApproveWithoutItems
│  ├─ shouldNotApproveAlreadyApproved
│  └─ shouldNotApproveCancelled
│
├─ Comportamento - Cancelar
│  ├─ shouldCancelPendingPayment
│  ├─ shouldNotCancelApprovedPayment
│  └─ shouldNotCancelAlreadyCancelled
│
├─ Comportamento - Reembolsar
│  ├─ shouldRefundApprovedPayment
│  └─ shouldNotRefundPendingPayment
│
├─ Comportamento - Items
│  ├─ shouldAddItemToPendingPayment
│  ├─ shouldNotAddItemToApprovedPayment
│  ├─ shouldRemoveItem
│  └─ shouldCalculateTotalFromItems
│
└─ Métodos Builder
   ├─ shouldUseWithMetadata
   ├─ shouldUseWithItem
   └─ shouldCopyWithFrom
```

### Testes Completos

```java
// src/test/java/com/mvbr/store/domain/model/payment/PaymentTest.java

package com.mvbr.store.domain.model.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes completos para Payment (Domínio Rico).
 */
@DisplayName("Payment - Domain Model Tests")
class PaymentTest {

    // ═══════════════════════════════════════════════════
    //      FIXTURES (dados de teste reutilizáveis)
    // ═══════════════════════════════════════════════════

    private PaymentId createPaymentId() {
        return PaymentId.generate();
    }

    private Money createMoney() {
        return Money.usd("100.00");
    }

    private Customer createCustomer() {
        return Customer.of("cust_123", "john@example.com");
    }

    private PaymentItem createItem(String id) {
        return PaymentItem.of(
            id,
            "Product " + id,
            1,
            Money.usd("50.00")
        );
    }

    private Payment.PaymentBuilder createBaseBuilder() {
        return Payment.builder()
            .paymentId(createPaymentId())
            .amount(createMoney())
            .customer(createCustomer())
            .paymentMethod(PaymentMethod.CREDIT_CARD);
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: CONSTRUÇÃO
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create payment using builder")
        void shouldCreatePaymentUsingBuilder() {
            // Given
            PaymentId id = createPaymentId();
            Money amount = createMoney();
            Customer customer = createCustomer();

            // When
            Payment payment = Payment.builder()
                .paymentId(id)
                .amount(amount)
                .customer(customer)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

            // Then
            assertThat(payment).isNotNull();
            assertThat(payment.getPaymentId()).isEqualTo(id);
            assertThat(payment.getAmount()).isEqualTo(amount);
            assertThat(payment.getCustomer()).isEqualTo(customer);
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getCreatedAt()).isNotNull();
            assertThat(payment.getApprovedAt()).isNull();
        }

        @Test
        @DisplayName("Should create payment using of() named constructor")
        void shouldCreatePaymentUsingOf() {
            // Given
            PaymentId id = createPaymentId();
            Money amount = createMoney();
            Customer customer = createCustomer();

            // When
            Payment payment = Payment.of(id, amount, customer);

            // Then
            assertThat(payment).isNotNull();
            assertThat(payment.getPaymentId()).isEqualTo(id);
            assertThat(payment.getAmount()).isEqualTo(amount);
            assertThat(payment.getCustomer()).isEqualTo(customer);
        }

        @Test
        @DisplayName("Should create payment using from() copy builder")
        void shouldCreatePaymentUsingFrom() {
            // Given
            Payment original = createBaseBuilder().build();

            // When
            Payment copy = Payment.from(original)
                .amount(Money.usd("200"))
                .build();

            // Then
            assertThat(copy.getPaymentId()).isEqualTo(original.getPaymentId());
            assertThat(copy.getCustomer()).isEqualTo(original.getCustomer());
            assertThat(copy.getAmount()).isEqualTo(Money.usd("200"));
        }

        @Test
        @DisplayName("Should fail when paymentId is missing")
        void shouldFailWhenPaymentIdMissing() {
            // When/Then
            assertThatThrownBy(() ->
                Payment.builder()
                    .amount(createMoney())
                    .customer(createCustomer())
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build()
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PaymentId is required");
        }

        @Test
        @DisplayName("Should fail when amount is missing")
        void shouldFailWhenAmountMissing() {
            // When/Then
            assertThatThrownBy(() ->
                Payment.builder()
                    .paymentId(createPaymentId())
                    .customer(createCustomer())
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build()
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Amount is required");
        }

        @Test
        @DisplayName("Should fail when customer is missing")
        void shouldFailWhenCustomerMissing() {
            // When/Then
            assertThatThrownBy(() ->
                Payment.builder()
                    .paymentId(createPaymentId())
                    .amount(createMoney())
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build()
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Customer is required");
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: APROVAÇÃO
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Approval Tests")
    class ApprovalTests {

        @Test
        @DisplayName("Should approve payment with items")
        void shouldApprovePaymentWithItems() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();

            // When
            payment.approve();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.isApproved()).isTrue();
            assertThat(payment.getApprovedAt()).isNotNull();
            assertThat(payment.getApprovedAt()).isAfterOrEqualTo(payment.getCreatedAt());
        }

        @Test
        @DisplayName("Should not approve payment without items")
        void shouldNotApproveWithoutItems() {
            // Given
            Payment payment = createBaseBuilder().build();

            // When/Then
            assertThatThrownBy(payment::approve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot approve payment without items");
        }

        @Test
        @DisplayName("Should not approve already approved payment")
        void shouldNotApproveAlreadyApproved() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();
            payment.approve();

            // When/Then
            assertThatThrownBy(payment::approve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot approve payment with status: APPROVED");
        }

        @Test
        @DisplayName("Should not approve cancelled payment")
        void shouldNotApproveCancelled() {
            // Given
            Payment payment = createBaseBuilder().build();
            payment.cancel();

            // When/Then
            assertThatThrownBy(payment::approve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot approve payment with status: CANCELLED");
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: CANCELAMENTO
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Cancellation Tests")
    class CancellationTests {

        @Test
        @DisplayName("Should cancel pending payment")
        void shouldCancelPendingPayment() {
            // Given
            Payment payment = createBaseBuilder().build();

            // When
            payment.cancel();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.isCancelled()).isTrue();
            assertThat(payment.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not cancel approved payment")
        void shouldNotCancelApprovedPayment() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();
            payment.approve();

            // When/Then
            assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot cancel approved payment");
        }

        @Test
        @DisplayName("Should not cancel already cancelled payment")
        void shouldNotCancelAlreadyCancelled() {
            // Given
            Payment payment = createBaseBuilder().build();
            payment.cancel();

            // When/Then
            assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment already cancelled");
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: REEMBOLSO
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Refund Tests")
    class RefundTests {

        @Test
        @DisplayName("Should refund approved payment")
        void shouldRefundApprovedPayment() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();
            payment.approve();

            // When
            payment.refund();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.isRefunded()).isTrue();
            assertThat(payment.getRefundedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not refund pending payment")
        void shouldNotRefundPendingPayment() {
            // Given
            Payment payment = createBaseBuilder().build();

            // When/Then
            assertThatThrownBy(payment::refund)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only refund approved payments");
        }

        @Test
        @DisplayName("Should not refund cancelled payment")
        void shouldNotRefundCancelledPayment() {
            // Given
            Payment payment = createBaseBuilder().build();
            payment.cancel();

            // When/Then
            assertThatThrownBy(payment::refund)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only refund approved payments");
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: ITEMS
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Items Tests")
    class ItemsTests {

        @Test
        @DisplayName("Should add item to pending payment")
        void shouldAddItemToPendingPayment() {
            // Given
            Payment payment = createBaseBuilder().build();
            PaymentItem item = createItem("item-1");

            // When
            payment.addItem(item);

            // Then
            assertThat(payment.getItems()).hasSize(1);
            assertThat(payment.getItems()).contains(item);
            assertThat(payment.hasItems()).isTrue();
            assertThat(payment.getItemCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should not add item to approved payment")
        void shouldNotAddItemToApprovedPayment() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();
            payment.approve();

            // When/Then
            assertThatThrownBy(() -> payment.addItem(createItem("item-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only add items to pending payments");
        }

        @Test
        @DisplayName("Should remove item from pending payment")
        void shouldRemoveItem() {
            // Given
            PaymentItem item1 = createItem("item-1");
            PaymentItem item2 = createItem("item-2");
            Payment payment = createBaseBuilder()
                .addItem(item1)
                .addItem(item2)
                .build();

            // When
            payment.removeItem("item-1");

            // Then
            assertThat(payment.getItems()).hasSize(1);
            assertThat(payment.getItems()).doesNotContain(item1);
            assertThat(payment.getItems()).contains(item2);
        }

        @Test
        @DisplayName("Should fail when removing non-existent item")
        void shouldFailWhenRemovingNonExistentItem() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();

            // When/Then
            assertThatThrownBy(() -> payment.removeItem("item-999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Item not found: item-999");
        }

        @Test
        @DisplayName("Should calculate total from items")
        void shouldCalculateTotalFromItems() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(PaymentItem.of("item-1", "Product 1", 2, Money.usd("50")))
                .addItem(PaymentItem.of("item-2", "Product 2", 1, Money.usd("100")))
                .build();

            // When
            Money total = payment.calculateTotal();

            // Then
            // 2 * 50 + 1 * 100 = 200
            assertThat(total).isEqualTo(Money.usd("200"));
        }

        @Test
        @DisplayName("Should return zero when no items")
        void shouldReturnZeroWhenNoItems() {
            // Given
            Payment payment = createBaseBuilder().build();

            // When
            Money total = payment.calculateTotal();

            // Then
            assertThat(total.isZero()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: BUILDER METHODS
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder Methods Tests")
    class BuilderMethodsTests {

        @Test
        @DisplayName("Should use withMetadata to add metadata")
        void shouldUseWithMetadata() {
            // Given
            Payment payment = createBaseBuilder().build();

            // When
            Payment withMeta = payment.withMetadata("source", "web");

            // Then
            assertThat(withMeta).isNotSameAs(payment); // Novo objeto!
            assertThat(withMeta.getMetadata().has("source")).isTrue();
            assertThat(withMeta.getMetadata().get("source")).hasValue("web");
            assertThat(payment.getMetadata().has("source")).isFalse(); // Original inalterado
        }

        @Test
        @DisplayName("Should use withItem to add item")
        void shouldUseWithItem() {
            // Given
            Payment payment = createBaseBuilder().build();
            PaymentItem item = createItem("item-1");

            // When
            Payment withItem = payment.withItem(item);

            // Then
            assertThat(withItem).isNotSameAs(payment);
            assertThat(withItem.getItems()).hasSize(1);
            assertThat(payment.getItems()).isEmpty();
        }

        @Test
        @DisplayName("Should use withoutItem to remove item")
        void shouldUseWithoutItem() {
            // Given
            PaymentItem item1 = createItem("item-1");
            PaymentItem item2 = createItem("item-2");
            Payment payment = createBaseBuilder()
                .addItem(item1)
                .addItem(item2)
                .build();

            // When
            Payment withoutItem = payment.withoutItem("item-1");

            // Then
            assertThat(withoutItem).isNotSameAs(payment);
            assertThat(withoutItem.getItems()).hasSize(1);
            assertThat(withoutItem.getItems()).contains(item2);
            assertThat(payment.getItems()).hasSize(2); // Original inalterado
        }

        @Test
        @DisplayName("Should chain multiple builder methods")
        void shouldChainMultipleBuilderMethods() {
            // Given/When
            Payment payment = Payment.builder()
                .paymentId(createPaymentId())
                .amount(Money.usd("300"))
                .customer(createCustomer())
                .paymentMethod(PaymentMethod.PIX)
                .addItem(createItem("item-1"))
                .addItem(createItem("item-2"))
                .withMetadata("ip", "192.168.1.1")
                .withMetadata("device", "mobile")
                .build();

            // Then
            assertThat(payment.getAmount()).isEqualTo(Money.usd("300"));
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
            assertThat(payment.getItems()).hasSize(2);
            assertThat(payment.getMetadata().has("ip")).isTrue();
            assertThat(payment.getMetadata().has("device")).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: QUERY METHODS
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("Should return correct status checks")
        void shouldReturnCorrectStatusChecks() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();

            // When/Then - PENDING
            assertThat(payment.isPending()).isTrue();
            assertThat(payment.isApproved()).isFalse();
            assertThat(payment.isCancelled()).isFalse();
            assertThat(payment.isRefunded()).isFalse();

            // When - Approve
            payment.approve();

            // Then - APPROVED
            assertThat(payment.isPending()).isFalse();
            assertThat(payment.isApproved()).isTrue();
            assertThat(payment.isCancelled()).isFalse();
            assertThat(payment.isRefunded()).isFalse();

            // When - Refund
            payment.refund();

            // Then - REFUNDED
            assertThat(payment.isPending()).isFalse();
            assertThat(payment.isApproved()).isFalse();
            assertThat(payment.isCancelled()).isFalse();
            assertThat(payment.isRefunded()).isTrue();
        }

        @Test
        @DisplayName("Should return defensive copy of items")
        void shouldReturnDefensiveCopyOfItems() {
            // Given
            Payment payment = createBaseBuilder()
                .addItem(createItem("item-1"))
                .build();

            // When
            List<PaymentItem> items = payment.getItems();

            // Then
            assertThatThrownBy(() -> items.add(createItem("item-2")))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ═══════════════════════════════════════════════════
    //      TESTES: EQUALS / HASHCODE
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Equals/HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Should be equal when same paymentId")
        void shouldBeEqualWhenSamePaymentId() {
            // Given
            PaymentId id = createPaymentId();
            Payment payment1 = Payment.builder()
                .paymentId(id)
                .amount(Money.usd("100"))
                .customer(createCustomer())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

            Payment payment2 = Payment.builder()
                .paymentId(id)
                .amount(Money.usd("200")) // Diferente!
                .customer(createCustomer())
                .paymentMethod(PaymentMethod.PIX) // Diferente!
                .build();

            // When/Then
            assertThat(payment1).isEqualTo(payment2);
            assertThat(payment1.hashCode()).isEqualTo(payment2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when different paymentId")
        void shouldNotBeEqualWhenDifferentPaymentId() {
            // Given
            Payment payment1 = createBaseBuilder().build();
            Payment payment2 = createBaseBuilder().build(); // Novo ID!

            // When/Then
            assertThat(payment1).isNotEqualTo(payment2);
        }
    }
}
```

---

## 9. Invariantes de Domínio

### O que são Invariantes?

**Invariantes** são condições que SEMPRE devem ser verdadeiras para um objeto válido do domínio, independentemente do estado em que ele se encontra.

```
INVARIANTES DO PAYMENT:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ PaymentId nunca é null
2. ✅ Amount nunca é null e sempre positivo
3. ✅ Customer nunca é null
4. ✅ Status sempre é um valor válido do enum
5. ✅ CreatedAt nunca é null
6. ✅ ApprovedAt só existe se status = APPROVED
7. ✅ CancelledAt só existe se status = CANCELLED
8. ✅ Items têm mesma currency que payment
9. ✅ Total calculado = soma dos items (se houver items)
10. ✅ Metadata nunca é null (pode ser vazio)


COMO GARANTIR INVARIANTES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ Validar no construtor (fail-fast)
2. ✅ Campos imutáveis (final quando possível)
3. ✅ Sem setters públicos
4. ✅ Comportamentos validam antes de mudar estado
5. ✅ Cópias defensivas de coleções
```

### Implementação de Invariantes

```java
/**
 * ✅ Validar TODOS os invariantes no construtor
 */
private Payment(PaymentBuilder builder) {
    this.paymentId = builder.paymentId;
    this.amount = builder.amount;
    this.customer = builder.customer;
    this.paymentMethod = builder.paymentMethod;
    this.items = new ArrayList<>(builder.items);
    this.metadata = builder.metadata;

    this.status = PaymentStatus.PENDING;
    this.createdAt = Instant.now();
    this.approvedAt = null;
    this.cancelledAt = null;

    // ✅ CRÍTICO: Validar invariantes!
    validateInvariants();
}

/**
 * ✅ Método que verifica TODOS os invariantes
 */
private void validateInvariants() {
    // Invariante 1: PaymentId nunca null
    if (paymentId == null) {
        throw new IllegalStateException("PaymentId cannot be null");
    }

    // Invariante 2: Amount nunca null e sempre positivo
    if (amount == null) {
        throw new IllegalStateException("Amount cannot be null");
    }
    if (!amount.isPositive()) {
        throw new IllegalStateException("Amount must be positive");
    }

    // Invariante 3: Customer nunca null
    if (customer == null) {
        throw new IllegalStateException("Customer cannot be null");
    }

    // Invariante 4: PaymentMethod nunca null
    if (paymentMethod == null) {
        throw new IllegalStateException("PaymentMethod cannot be null");
    }

    // Invariante 5: Metadata nunca null
    if (metadata == null) {
        throw new IllegalStateException("Metadata cannot be null");
    }

    // Invariante 6: CreatedAt nunca null
    if (createdAt == null) {
        throw new IllegalStateException("CreatedAt cannot be null");
    }

    // Invariante 7: Items com mesma currency
    validateItemsCurrency();

    // Invariante 8: Timestamps consistentes
    validateTimestamps();
}

/**
 * ✅ Invariante: Items têm mesma currency que payment
 */
private void validateItemsCurrency() {
    if (items.isEmpty()) {
        return;
    }

    String expectedCurrency = amount.getCurrencyCode();

    for (PaymentItem item : items) {
        String itemCurrency = item.getUnitPrice().getCurrencyCode();

        if (!itemCurrency.equals(expectedCurrency)) {
            throw new IllegalStateException(
                String.format(
                    "Item currency (%s) does not match payment currency (%s)",
                    itemCurrency,
                    expectedCurrency
                )
            );
        }
    }
}

/**
 * ✅ Invariante: Timestamps consistentes
 */
private void validateTimestamps() {
    if (approvedAt != null && approvedAt.isBefore(createdAt)) {
        throw new IllegalStateException(
            "ApprovedAt cannot be before createdAt"
        );
    }

    if (cancelledAt != null && cancelledAt.isBefore(createdAt)) {
        throw new IllegalStateException(
            "CancelledAt cannot be before createdAt"
        );
    }

    if (refundedAt != null && refundedAt.isBefore(createdAt)) {
        throw new IllegalStateException(
            "RefundedAt cannot be before createdAt"
        );
    }
}

/**
 * ✅ Invariante: ApprovedAt só existe se APPROVED
 */
public Instant getApprovedAt() {
    if (status != PaymentStatus.APPROVED && approvedAt != null) {
        throw new IllegalStateException(
            "ApprovedAt should only exist for APPROVED payments"
        );
    }
    return approvedAt;
}
```

### Testes de Invariantes

```java
@Nested
@DisplayName("Invariants Tests")
class InvariantsTests {

    @Test
    @DisplayName("Should always have non-null paymentId")
    void shouldAlwaysHaveNonNullPaymentId() {
        // Given
        Payment payment = createBaseBuilder().build();

        // Then
        assertThat(payment.getPaymentId()).isNotNull();
    }

    @Test
    @DisplayName("Should always have positive amount")
    void shouldAlwaysHavePositiveAmount() {
        // Given
        Payment payment = createBaseBuilder().build();

        // Then
        assertThat(payment.getAmount().isPositive()).isTrue();
    }

    @Test
    @DisplayName("Should always have consistent timestamps")
    void shouldAlwaysHaveConsistentTimestamps() {
        // Given
        Payment payment = createBaseBuilder()
            .addItem(createItem("item-1"))
            .build();

        // When
        payment.approve();

        // Then
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(payment.getApprovedAt()).isAfterOrEqualTo(payment.getCreatedAt());
    }

    @Test
    @DisplayName("Should maintain items currency invariant")
    void shouldMaintainItemsCurrencyInvariant() {
        // Given
        Money usdAmount = Money.usd("100");
        Money brlUnitPrice = Money.brl("50"); // Diferente!

        // When/Then
        assertThatThrownBy(() ->
            Payment.builder()
                .paymentId(createPaymentId())
                .amount(usdAmount)
                .customer(createCustomer())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .addItem(PaymentItem.of("item-1", "Product", 1, brlUnitPrice))
                .build()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Item currency (BRL) does not match payment currency (USD)");
    }

    @Test
    @DisplayName("Should return defensive copy of items list")
    void shouldReturnDefensiveCopyOfItemsList() {
        // Given
        Payment payment = createBaseBuilder()
            .addItem(createItem("item-1"))
            .build();

        // When
        List<PaymentItem> items = payment.getItems();

        // Then - Tentar modificar deve lançar exceção
        assertThatThrownBy(() -> items.clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

---

## 10. Checklist e Boas Práticas

### Checklist: Criando Domínio Rico

```
ANTES DE FINALIZAR SEU DOMÍNIO RICO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Aggregate Root identificado
   └─ Payment é o Aggregate Root

☐ Construtor privado (força uso do Builder)
   └─ private Payment(PaymentBuilder builder)

☐ Factory Methods implementados
   ☐ builder() - criar builder vazio
   ☐ from() - copy builder
   ☐ of() - named constructor

☐ Value Objects criados
   ☐ PaymentId (identidade)
   ☐ Money (valor monetário)
   ☐ Customer (cliente)
   ☐ PaymentMetadata (metadados)

☐ Enums criados
   ☐ PaymentStatus
   ☐ PaymentMethod

☐ Entities criadas
   ☐ PaymentItem (tem identidade)

☐ Validações no construtor
   ☐ Todos campos obrigatórios validados
   ☐ Formato correto validado
   ☐ Invariantes garantidos

☐ Comportamentos de negócio
   ☐ approve()
   ☐ cancel()
   ☐ refund()
   ☐ addItem()
   ☐ removeItem()

☐ Imutabilidade
   ☐ Campos final quando possível
   ☐ Sem setters públicos
   ☐ Cópia defensiva de coleções

☐ Builder Pattern
   ☐ Classe interna PaymentBuilder
   ☐ Métodos fluentes (retorna this)
   ☐ build() valida antes de criar
   ☐ Métodos with/from/of/add

☐ Testes unitários
   ☐ Construção
   ☐ Validações
   ☐ Comportamentos
   ☐ Transições de estado
   ☐ Invariantes
   ☐ Equals/HashCode

☐ Documentação
   ☐ Javadoc nas classes
   ☐ Comentários nas regras de negócio
   ☐ Exemplos de uso
```

### Boas Práticas

```
PRÁTICAS ESSENCIAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ FAIL-FAST
   └─ Validar no construtor, não depois
   └─ Se inválido, NÃO CRIA o objeto

2. ✅ LINGUAGEM UBÍQUA
   └─ Nomes refletem termos do domínio
   └─ approve(), refund(), não setStatus()

3. ✅ TELL, DON'T ASK
   └─ payment.approve() ← correto
   └─ payment.setStatus(APPROVED) ← errado

4. ✅ ENCAPSULAMENTO
   └─ Lógica de negócio no domínio
   └─ Não vaza para serviços

5. ✅ IMUTABILIDADE
   └─ Value Objects sempre imutáveis
   └─ Entities imutáveis quando possível

6. ✅ CÓPIA DEFENSIVA
   └─ Listas retornam cópias
   └─ Collections.unmodifiableList()

7. ✅ VALIDAÇÃO COMPLETA
   └─ Valida formato E regras de negócio
   └─ Valida invariantes

8. ✅ TESTES COMPLETOS
   └─ Testa TODOS os cenários
   └─ Testa caminhos felizes E erros

9. ✅ BUILDER FLUENTE
   └─ API legível e auto-documentada
   └─ Métodos encadeáveis

10. ✅ DOCUMENTAÇÃO CLARA
    └─ Javadoc explica regras
    └─ Comentários no código
```

### Anti-Patterns a Evitar

```
❌ NÃO FAÇA:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Setters públicos
   └─ Quebra encapsulamento
   └─ Permite estado inválido

❌ Lógica de negócio em Services
   └─ Domínio anêmico
   └─ Difícil de testar

❌ Validações espalhadas
   └─ Duplicação
   └─ Fácil esquecer

❌ Exceptions genéricas
   └─ throw new Exception("error")
   └─ Use IllegalArgumentException, IllegalStateException

❌ Construtor público com muitos parâmetros
   └─ Difícil de usar
   └─ Use Builder

❌ Mutable Value Objects
   └─ Value Objects DEVEM ser imutáveis
   └─ Money, Customer, PaymentId = imutáveis

❌ Expor coleções mutáveis
   └─ return items; ← errado
   └─ return List.copyOf(items); ← correto

❌ Equals/HashCode incorretos
   └─ Entity: por identidade
   └─ Value Object: por valor

❌ Sem testes
   └─ Código sem testes = código legado
   └─ TDD sempre!

❌ Ignorar invariantes
   └─ Domínio pode ficar inválido
   └─ Valide SEMPRE!
```

---

## 11. Exercícios Práticos

### Exercício 1: Adicionar Campo "Description" ao Payment

**Requisitos:**
- Adicionar campo `description` (String)
- Validar: não pode ser null ou blank
- Validar: máximo 500 caracteres
- Adicionar ao Builder
- Criar testes

**Dica:** Seguir mesmo padrão dos outros campos.

### Exercício 2: Implementar Limite de Desconto

**Requisitos:**
- Adicionar campo `discount` (Money)
- Validar: não pode ser negativo
- Validar: não pode ser maior que amount
- Criar método `applyDiscount(Money discount)`
- Recalcular total com desconto
- Criar testes

**Dica:** Total = amount - discount

### Exercício 3: Adicionar PaymentHistory

**Requisitos:**
- Criar Value Object `PaymentEvent` (timestamp, event type, user)
- Adicionar lista `history` ao Payment
- Registrar evento quando: approve, cancel, refund
- Criar método `getHistory()` que retorna cópia imutável
- Criar testes

**Dica:** Usar padrão Observer/Event Sourcing.

### Exercício 4: Implementar Partial Refund

**Requisitos:**
- Adicionar método `refund(Money amount)` (reembolso parcial)
- Validar: amount <= amount original
- Adicionar campo `refundedAmount`
- Status muda para PARTIALLY_REFUNDED se parcial
- Criar testes

**Dica:** Novo enum status: PARTIALLY_REFUNDED

### Exercício 5: Adicionar PaymentProcessor

**Requisitos:**
- Criar interface `PaymentProcessor`
- Implementar `CreditCardProcessor`, `PixProcessor`
- Adicionar campo `processor` ao Payment
- Validar compatibilidade (PIX → BRL)
- Criar testes

**Dica:** Usar Strategy Pattern (OCP).

---

## Conclusão

Parabéns! 🎉 Você dominou a criação de **Domínio Rico com Builder Pattern e DDD**!

### O que você aprendeu:

✅ Domínio Rico vs Domínio Anêmico
✅ Builder Pattern com métodos fluentes
✅ Value Objects (Money, PaymentId, Customer, Metadata)
✅ Aggregate Root (Payment)
✅ Entities (PaymentItem)
✅ Validações de domínio ricas
✅ Invariantes de domínio
✅ Métodos Builder (with, from, of, add)
✅ Testes unitários completos com TDD
✅ Encapsulamento e imutabilidade
✅ Linguagem Ubíqua do DDD

### Próximos Passos:

1. Pratique com os **exercícios** acima
2. Aplique este padrão em outros domínios (Order, Product, User)
3. Integre com **Spring Data JPA** (mapeamento)
4. Adicione **eventos de domínio** (DomainEvents)
5. Implemente **Repositories** (portas/adaptadores)
6. Estude **Event Sourcing** para histórico completo

### Leituras Recomendadas:

📚 "Domain-Driven Design" - Eric Evans
📚 "Implementing Domain-Driven Design" - Vaughn Vernon
📚 "Clean Code" - Robert C. Martin
📚 "Effective Java" - Joshua Bloch (Builder Pattern)

### Lembre-se:

> "Um domínio rico não é sobre código bonito.
> É sobre representar com precisão as regras de negócio
> de forma que seja impossível criar um objeto inválido."

Agora vá e construa domínios ricos e expressivos! 🚀

---

**FIM DO TUTORIAL**

Este tutorial foi criado para ser sua **fonte única de verdade** sobre Domínio Rico com Builder Pattern e DDD. Releia, pratique e domine estes conceitos!
