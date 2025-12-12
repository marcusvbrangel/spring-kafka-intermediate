# Estudo: DDD + Builder para `Order`

Este arquivo contém uma versão completa do modelo DDD para a entidade `Order` (Aggregate Root), incluindo Value Objects, Enum, exceções de domínio, contrato de repositório e o Aggregate com **Builder** (com comentários antes de cada verbo do builder explicando o porquê).

---

## DomainException.java
```java
package domain.shared;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
```

---

## OrderId.java (Value Object)
```java
package domain.vo;

import domain.shared.DomainException;
import java.util.Objects;
import java.util.UUID;

public final class OrderId {
    private final String value;

    private OrderId(String value) {
        if (value == null || value.isBlank()) throw new DomainException("orderId cannot be null or blank");
        this.value = value;
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public static OrderId of(String id) {
        return new OrderId(id);
    }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderId)) return false;
        return value.equals(((OrderId)o).value);
    }

    @Override public int hashCode() { return Objects.hash(value); }

    @Override public String toString() { return value; }
}
```

---

## CustomerId.java (Value Object)
```java
package domain.vo;

import domain.shared.DomainException;
import java.util.Objects;

public final class CustomerId {
    private final String value;

    private CustomerId(String value) {
        if (value == null || value.isBlank()) throw new DomainException("customerId cannot be null or blank");
        this.value = value;
    }

    public static CustomerId of(String id) { return new CustomerId(id); }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerId)) return false;
        return value.equals(((CustomerId)o).value);
    }

    @Override public int hashCode() { return Objects.hash(value); }

    @Override public String toString() { return value; }
}
```

---

## Money.java (Value Object)
```java
package domain.vo;

import domain.shared.DomainException;
import java.math.BigDecimal;
import java.util.Objects;

public final class Money implements Comparable<Money> {
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount == null) throw new DomainException("Money amount cannot be null");
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) { return new Money(amount); }
    public static Money of(long value) { return new Money(BigDecimal.valueOf(value)); }

    public Money add(Money other) { return new Money(this.amount.add(other.amount)); }
    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        return new Money(result);
    }
    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public BigDecimal value() { return amount; }

    @Override public int compareTo(Money o) { return this.amount.compareTo(o.amount); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        return amount.equals(((Money)o).amount);
    }

    @Override public int hashCode() { return Objects.hash(amount); }

    @Override public String toString() { return amount.toPlainString(); }
}
```

---

## ProductId.java (Value Object)
```java
package domain.vo;

import domain.shared.DomainException;
import java.util.Objects;

public final class ProductId {
    private final String value;

    public ProductId(String value) {
        if (value == null || value.isBlank()) throw new DomainException("productId cannot be null or blank");
        this.value = value;
    }

    public static ProductId of(String id) { return new ProductId(id); }
    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductId)) return false;
        return value.equals(((ProductId)o).value);
    }

    @Override public int hashCode() { return Objects.hash(value); }

    @Override public String toString() { return value; }
}
```

---

## Quantity.java (Value Object)
```java
package domain.vo;

import domain.shared.DomainException;
import java.util.Objects;

public final class Quantity {
    private final int value;

    public Quantity(int value) {
        if (value <= 0) throw new DomainException("Quantity must be greater than zero");
        this.value = value;
    }

    public static Quantity of(int v) { return new Quantity(v); }
    public int value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity)) return false;
        return value == ((Quantity)o).value;
    }

    @Override public int hashCode() { return Objects.hash(value); }
}
```

---

## OrderItem.java (Value Object)
```java
package domain.entity;

import domain.shared.DomainException;
import domain.vo.ProductId;
import domain.vo.Quantity;
import domain.vo.Money;
import java.util.Objects;

public final class OrderItem {
    private final ProductId productId;
    private final Quantity quantity;
    private final Money unitPrice;

    public OrderItem(ProductId productId, Quantity quantity, Money unitPrice) {
        if (productId == null) throw new DomainException("productId cannot be null");
        if (quantity == null) throw new DomainException("quantity cannot be null");
        if (unitPrice == null) throw new DomainException("unitPrice cannot be null");

        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public ProductId productId() { return productId; }
    public Quantity quantity() { return quantity; }
    public Money unitPrice() { return unitPrice; }

    public Money getSubtotal() {
        return unitPrice.multiply(quantity.value());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem)) return false;
        OrderItem that = (OrderItem) o;
        return productId.equals(that.productId)
                && quantity.equals(that.quantity)
                && unitPrice.equals(that.unitPrice);
    }

    @Override public int hashCode() { return Objects.hash(productId, quantity, unitPrice); }

    @Override public String toString() {
        return "OrderItem{" +
                "productId=" + productId +
                ", quantity=" + quantity.value() +
                ", unitPrice=" + unitPrice +
                '}';
    }
}
```

---

## OrderStatus.java (Enum)
```java
package domain.vo;

public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    CANCELLED,
    SHIPPED,
    COMPLETED
}
```

---

## OrderRepository.java (Repository contract)
```java
package domain.repository;

import domain.vo.OrderId;
import domain.entity.Order;
import java.util.Optional;

public interface OrderRepository {
    void save(Order order);                       // persist aggregate
    Optional<Order> findById(OrderId orderId);    // reconstitute aggregate
}
```

---

## Order.java (Aggregate Root com Builder e comentários)

```java
package domain.entity;

import domain.shared.DomainException;
import domain.vo.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 Aggregate Root: Order
 - Imutável (por conveniência) exceto quando decisão de negócio exigir mutação interna.
 - Builder com métodos DDD-friendly:
   - builder() : iniciar construção manual
   - of(...) : iniciar a construção com campos obrigatórios
   - from(...) / reconstitute(...) : reconstituir a partir de dados externos
   - toBuilder() : obter builder pré-populado a partir do próprio agregado
   - withXxx() : atribuições fluentes de campos simples
   - addItem() / addAllItems() : verbos padrão para coleções
   - build() : valida invariantes e retorna o agregado pronto
*/
public final class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private final Money discount;
    private final Money total;
    private OrderStatus status;

    // Construtor privado — reconstituição somente via Builder
    private Order(Builder builder) {
        if (builder.orderId == null) throw new DomainException("orderId is required");
        if (builder.customerId == null) throw new DomainException("customerId is required");
        if (builder.discount == null) builder.discount = Money.ZERO;
        if (builder.items == null) builder.items = new ArrayList<>();

        this.orderId = builder.orderId;
        this.customerId = builder.customerId;
        this.items = List.copyOf(builder.items);
        this.discount = builder.discount;
        this.status = builder.status == null ? OrderStatus.DRAFT : builder.status;

        this.total = calculateTotalInternal();
        ensureInvariants();
    }

    // --- Comportamentos do Aggregate Root ---

    public Order confirm() {
        if (items.isEmpty()) throw new DomainException("Cannot confirm an order without items");
        // muta o estado ou cria nova instância: optamos por criar nova instância
        return this.toBuilder()
                // toBuilder(): cria um builder pré-populado com o estado atual
                // withStatus(): define o novo status
                .withStatus(OrderStatus.CONFIRMED)
                // build(): retorna nova instância do agregado com a alteração
                .build();
    }

    public Order cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED)
            throw new DomainException("Cannot cancel shipped or completed orders");
        return this.toBuilder()
                .withStatus(OrderStatus.CANCELLED)
                .build();
    }

    // Aplicar desconto como operação do agregado
    public Order applyDiscount(Money discount) {
        if (discount == null) throw new DomainException("discount cannot be null");
        if (discount.compareTo(Money.ZERO) < 0) throw new DomainException("discount cannot be negative");
        Money subtotal = calculateSubtotalInternal();
        if (discount.compareTo(subtotal) > 0) throw new DomainException("discount cannot be greater than subtotal");

        return Order.builder()
                // builder(): inicia novo builder vazio
                .withOrderId(this.orderId)         // withOrderId(): preserva identidade
                .withCustomerId(this.customerId)   // withCustomerId(): preserva cliente
                .addAllItems(this.items)           // addAllItems(): copia itens atuais
                .withStatus(this.status)           // withStatus(): preserva status
                .withDiscount(discount)            // withDiscount(): aplica o novo desconto
                .build();                          // build(): cria o novo aggregate
    }

    // Getters
    public OrderId orderId() { return orderId; }
    public CustomerId customerId() { return customerId; }
    public List<OrderItem> items() { return items; }
    public Money discount() { return discount; }
    public Money total() { return total; }
    public OrderStatus status() { return status; }

    // --- cálculos internos ---
    private Money calculateSubtotalInternal() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, Money::add);
    }

    private Money calculateTotalInternal() {
        Money subtotal = calculateSubtotalInternal();
        Money result = subtotal.subtract(discount == null ? Money.ZERO : discount);
        return result;
    }

    // invariantes do agregado
    private void ensureInvariants() {
        if (discount == null) throw new DomainException("discount cannot be null");
        if (discount.compareTo(Money.ZERO) < 0) throw new DomainException("discount cannot be negative");
        Money subtotal = calculateSubtotalInternal();
        if (discount.compareTo(subtotal) > 0) throw new DomainException("discount cannot be greater than subtotal");
        if (total == null) throw new DomainException("total cannot be null");
    }

    // --- Factories / Builders (DDD-friendly) ---

    // from(): reconstituir aggregate root a partir de dados persistidos (usado por repositório)
    public static Builder from(OrderId id, CustomerId customerId, List<OrderItem> items, Money discount, OrderStatus status) {
        return new Builder()
                .withOrderId(id)        // withOrderId(): popula o id vindo da persistência
                .withCustomerId(customerId)
                .addAllItems(items)     // addAllItems(): popula itens reconstituídos
                .withDiscount(discount)
                .withStatus(status);
    }

    // create(): factory para criar um novo Order (estado inicial - DRAFT)
    public static Order create(OrderId id, CustomerId customerId) {
        return Order.builder()
                .withOrderId(id)
                .withCustomerId(customerId)
                .withStatus(OrderStatus.DRAFT)
                .build();
    }

    // builder(): iniciar um builder para montar o agregado passo a passo
    public static Builder builder() {
        return new Builder();
    }

    // toBuilder(): obter um builder pré-populado a partir do próprio agregado
    public Builder toBuilder() {
        return new Builder()
                .withOrderId(this.orderId)
                .withCustomerId(this.customerId)
                .addAllItems(this.items)
                .withDiscount(this.discount)
                .withStatus(this.status);
    }

    // ====================================================
    // BUILDER interno
    // ====================================================
    public static final class Builder {
        private OrderId orderId;
        private CustomerId customerId;
        private List<OrderItem> items = new ArrayList<>();
        private Money discount = Money.ZERO;
        private OrderStatus status = OrderStatus.DRAFT;

        // withOrderId(): atribui o OrderId (campo obrigatório)
        public Builder withOrderId(OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        // withCustomerId(): atribui o CustomerId (campo obrigatório)
        public Builder withCustomerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        // withDiscount(): define o desconto no builder com validação local
        public Builder withDiscount(Money discount) {
            if (discount == null) this.discount = Money.ZERO;
            else if (discount.compareTo(Money.ZERO) < 0) throw new DomainException("discount cannot be negative");
            else this.discount = discount;
            return this;
        }

        // withStatus(): define o status do agregado
        public Builder withStatus(OrderStatus status) {
            this.status = status;
            return this;
        }

        // addItem(): adiciona um único item ao builder (verbo padrão para listas)
        public Builder addItem(OrderItem item) {
            if (item == null) throw new DomainException("item cannot be null");
            this.items.add(item);
            return this;
        }

        // addItem(productId, qty, price): overload conveniente para criar o OrderItem inline
        public Builder addItem(ProductId productId, Quantity quantity, Money unitPrice) {
            return addItem(new OrderItem(productId, quantity, unitPrice));
        }

        // addAllItems(): adiciona uma coleção inteira de itens ao builder
        public Builder addAllItems(List<OrderItem> items) {
            if (items != null) this.items.addAll(items);
            return this;
        }

        // build(): constrói o aggregate root final, aplicando validações / invariantes do domínio
        public Order build() {
            if (orderId == null) throw new DomainException("orderId is required");
            if (customerId == null) throw new DomainException("customerId is required");
            if (discount == null) discount = Money.ZERO;

            Money subtotal = items.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(Money.ZERO, Money::add);
            if (discount.compareTo(subtotal) > 0) throw new DomainException("discount cannot be greater than subtotal");

            return new Order(this);
        }
    }

    // equals/hashCode (baseado em identity)
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        return Objects.equals(orderId, ((Order)o).orderId);
    }

    @Override public int hashCode() { return Objects.hash(orderId); }
}
```

---

## Exemplo de uso (em um serviço/app)
```java
OrderId id = OrderId.generate();
CustomerId customer = CustomerId.of("cust-123");

Order order = Order.create(id, customer)
        .toBuilder()
        .addItem(ProductId.of("p-1"), Quantity.of(2), Money.of(100))
        .addItem(ProductId.of("p-2"), Quantity.of(1), Money.of(50))
        .withDiscount(Money.of(25))
        .build();

// confirmar pedido (gera nova instância com status CONFIRMED)
Order confirmed = order.confirm();
```

---

## Observações rápidas
- Arquitetura recomendada: separar `domain`, `application` e `infra` packages.
- O `OrderRepository` deve reconstituir agregados chamando `Order.from(...)` ou mapeando para o Builder.
- Considere publicar DomainEvents após ações como `confirm()` e `applyDiscount()`.

---

Fim do arquivo.
