# Exemplo de Uso da Classe `Order` com Builder (DDD)

Abaixo está um exemplo simples de como você pode usar a classe `Order` construída com o padrão Builder, dentro de um `public static void main`.

```java
import java.math.BigDecimal;
import java.util.UUID;

public class MainDemo {
    public static void main(String[] args) {

        // ============================
        // Criando Value Objects
        // ============================
        CustomerId customerId = CustomerId.of(UUID.randomUUID());
        Money price = Money.of(new BigDecimal("49.90"));
        Quantity qty = Quantity.of(2);

        // ============================
        // Criando um OrderItem via Builder
        // ============================
        OrderItem item = OrderItem.builder()
                .withProductId(UUID.randomUUID())
                .withPrice(price)
                .withQuantity(qty)
                .build();

        // ============================
        // Criando o Order como Aggregate Root
        // ============================
        Order order = Order.builder()
                .withCustomerId(customerId)
                .addItem(item)                   // adiciona um item ao pedido
                .withStatus(OrderStatus.CREATED) // define o estado inicial
                .build();

        // ============================
        // Exibindo os valores
        // ============================
        System.out.println("Order ID: " + order.getId().value());
        System.out.println("Customer: " + order.getCustomerId().value());
        System.out.println("Status: " + order.getStatus());
        System.out.println("Total de itens: " + order.getItems().size());
        System.out.println("Valor total: " + order.totalAmount().value());
    }
}
```

Esse arquivo é apenas um exemplo de como utilizar o Aggregate Root `Order` e seus objetos relacionados.
