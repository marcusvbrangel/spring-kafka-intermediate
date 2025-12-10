# Tutorial Definitivo: Saga Pattern - Coreografia

## 📚 Sumário

1. [Definição em 30 Segundos](#definição-em-30-segundos)
2. [O que é Saga com Coreografia](#o-que-é-coreografia)
3. [Coreografia vs Orquestração](#coreografia-vs-orquestração)
4. [Como Funciona a Coreografia](#como-funciona)
5. [Event-Driven Architecture](#event-driven-architecture)
6. [Implementação Passo a Passo](#implementação-passo-a-passo)
7. [Eventos de Domínio](#eventos-de-domínio)
8. [Compensações Distribuídas](#compensações-distribuídas)
9. [Idempotência em Consumers](#idempotência)
10. [Dead Letter Queue (DLQ)](#dead-letter-queue)
11. [Event Versioning](#event-versioning)
12. [Kafka Configuration](#kafka-configuration)
13. [Monitoramento e Observabilidade](#monitoramento)
14. [Implementação Completa com Spring Boot](#implementação-completa)
15. [Testes](#testes)
16. [Armadilhas Comuns](#armadilhas)
17. [Quando Usar Coreografia](#quando-usar)
18. [Checklist de Implementação](#checklist)
19. [Exercícios Práticos](#exercícios-práticos)

---

## Definição em 30 Segundos

**Saga Pattern com Coreografia** é um padrão para gerenciar transações distribuídas onde **cada microserviço publica eventos** após completar sua transação local, e outros serviços **reagem a esses eventos**. Não há coordenador central - a coordenação emerge da interação entre serviços via eventos.

**Princípio-Chave:** Cada músico (microserviço) sabe sua parte e reage aos outros músicos (eventos), sem maestro.

```
Order Service → publica OrderCreatedEvent
Payment Service → escuta OrderCreatedEvent → processa → publica PaymentApprovedEvent
Inventory Service → escuta PaymentApprovedEvent → reserva → publica InventoryReservedEvent
```

---

## 1. O que é Saga com Coreografia {#o-que-é-coreografia}

### 1.1. Arquitetura Event-Driven

Na coreografia, **serviços se comunicam via eventos** (message broker):

```
ARQUITETURA:
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    Order     │       │   Payment    │       │  Inventory   │
│   Service    │       │   Service    │       │   Service    │
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                      │                      │
       │ publishes            │ publishes            │ publishes
       │ OrderCreatedEvent    │ PaymentApprovedEvent │ InventoryReservedEvent
       │                      │                      │
       ↓                      ↓                      ↓
    ┌─────────────────────────────────────────────────────────┐
    │                    KAFKA TOPICS                         │
    │  - order.created.v1                                     │
    │  - payment.approved.v1                                  │
    │  - inventory.reserved.v1                                │
    └─────────────────────────────────────────────────────────┘
       ↑                      ↑                      ↑
       │ listens              │ listens              │ listens
       │                      │                      │
┌──────┴───────┐       ┌──────┴───────┐       ┌──────┴───────┐
│   Payment    │       │  Inventory   │       │    Order     │
│   Service    │       │   Service    │       │   Service    │
└──────────────┘       └──────────────┘       └──────────────┘
```

### 1.2. Exemplo: Criar Order (Fluxo Completo)

```
SUCESSO:
1. Client → POST /orders
2. Order Service:
   - Cria order (status=PENDING)
   - Publica OrderCreatedEvent

3. Payment Service:
   - Escuta OrderCreatedEvent
   - Processa payment
   - Publica PaymentApprovedEvent

4. Inventory Service:
   - Escuta PaymentApprovedEvent
   - Reserva items
   - Publica InventoryReservedEvent

5. Order Service:
   - Escuta InventoryReservedEvent
   - Atualiza order (status=CONFIRMED)
   - Publica OrderConfirmedEvent

FALHA:
1-3. (mesmo fluxo)

4. Inventory Service:
   - Escuta PaymentApprovedEvent
   - ❌ Sem estoque!
   - Publica InventoryReservationFailedEvent

5. Payment Service:
   - Escuta InventoryReservationFailedEvent
   - Reembolsa payment
   - Publica PaymentRefundedEvent

6. Order Service:
   - Escuta PaymentRefundedEvent
   - Cancela order (status=CANCELLED)
   - Publica OrderCancelledEvent
```

---

## 2. Coreografia vs Orquestração {#coreografia-vs-orquestração}

### 2.1. Comparação Visual

```
ORQUESTRAÇÃO (Maestro Coordena):
                ┌─────────────────┐
                │  ORCHESTRATOR   │ ← Single Point of Failure
                └────────┬────────┘
                         │
        ┌────────────────┼────────────────┐
        ↓ (HTTP)         ↓ (HTTP)         ↓ (HTTP)
  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │  Order   │     │ Payment  │     │Inventory │
  │ Service  │     │ Service  │     │ Service  │
  └──────────┘     └──────────┘     └──────────┘

COREOGRAFIA (Reação a Eventos):
  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │  Order   │     │ Payment  │     │Inventory │
  │ Service  │     │ Service  │     │ Service  │
  └─────┬────┘     └─────┬────┘     └─────┬────┘
        │                │                │
        │ (publishes)    │ (publishes)    │ (publishes)
        │                │                │
        ↓                ↓                ↓
    ┌────────────────────────────────────────┐
    │          KAFKA (Message Broker)        │
    └────────────────────────────────────────┘
        ↑                ↑                ↑
        │ (listens)      │ (listens)      │ (listens)
        │                │                │
```

### 2.2. Tabela Comparativa

| Aspecto | Orquestração | Coreografia |
|---------|--------------|-------------|
| **Coordenação** | Centralizada (Orchestrator) | Descentralizada (Eventos) |
| **Comunicação** | HTTP/gRPC (Synchronous) | Message Broker (Asynchronous) |
| **Acoplamento** | Baixo entre serviços, alto com orchestrator | Acoplamento via eventos/schemas |
| **Single Point of Failure** | Sim (orchestrator) | Não |
| **Visibilidade** | Fácil ver fluxo completo | Difícil rastrear fluxo end-to-end |
| **Debugar** | Fácil (logs centralizados) | Difícil (logs distribuídos) |
| **Escalabilidade** | Orquestrador pode ser gargalo | Alta escalabilidade |
| **Latência** | Potencialmente maior (sync) | Menor (async) |
| **Complexidade** | Concentrada no orchestrator | Distribuída entre serviços |
| **Melhor para** | Fluxos complexos, regras centralizadas | Fluxos simples, alta disponibilidade |
| **Adição de serviços** | Precisa atualizar orchestrator | Apenas subscrever a eventos |

---

## 3. Como Funciona a Coreografia {#como-funciona}

### 3.1. Fluxo Detalhado (Sucesso)

```
PASSO 1: Cliente cria order
┌─────────┐
│ Client  │
└────┬────┘
     │ POST /api/orders
     ↓
┌────────────────────────────────────────────────┐
│ ORDER SERVICE                                  │
│                                                │
│ @PostMapping("/api/orders")                    │
│ public OrderResponse createOrder() {           │
│   Order order = new Order(...);                │
│   order.setStatus(PENDING);                    │
│   orderRepository.save(order);                 │
│                                                │
│   // Publica evento                            │
│   kafkaTemplate.send(                          │
│     "order.created.v1",                        │
│     new OrderCreatedEvent(order)               │
│   );                                           │
│                                                │
│   return new OrderResponse(order);             │
│ }                                              │
└────────────────────────────────────────────────┘
           │
           │ OrderCreatedEvent
           ↓
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │order.created.v1 │
    └─────────────────┘

PASSO 2: Payment Service reage
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │order.created.v1 │
    └────────┬────────┘
             │ OrderCreatedEvent
             ↓
┌────────────────────────────────────────────────┐
│ PAYMENT SERVICE                                │
│                                                │
│ @KafkaListener(topics = "order.created.v1")    │
│ public void handleOrderCreated(               │
│     OrderCreatedEvent event                    │
│ ) {                                            │
│   // Processa payment                          │
│   Payment payment = processPayment(event);     │
│                                                │
│   if (payment.isApproved()) {                  │
│     // Publica evento de sucesso              │
│     kafkaTemplate.send(                        │
│       "payment.approved.v1",                   │
│       new PaymentApprovedEvent(payment)        │
│     );                                         │
│   } else {                                     │
│     // Publica evento de falha                │
│     kafkaTemplate.send(                        │
│       "payment.failed.v1",                     │
│       new PaymentFailedEvent(payment)          │
│     );                                         │
│   }                                            │
│ }                                              │
└────────────────────────────────────────────────┘
           │
           │ PaymentApprovedEvent
           ↓
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │payment.approved │
    │       .v1       │
    └─────────────────┘

PASSO 3: Inventory Service reage
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │payment.approved │
    │       .v1       │
    └────────┬────────┘
             │ PaymentApprovedEvent
             ↓
┌────────────────────────────────────────────────┐
│ INVENTORY SERVICE                              │
│                                                │
│ @KafkaListener(topics = "payment.approved.v1") │
│ public void handlePaymentApproved(            │
│     PaymentApprovedEvent event                 │
│ ) {                                            │
│   try {                                        │
│     // Reserva items                           │
│     Inventory inv = reserveItems(event);       │
│                                                │
│     // Publica evento de sucesso              │
│     kafkaTemplate.send(                        │
│       "inventory.reserved.v1",                 │
│       new InventoryReservedEvent(inv)          │
│     );                                         │
│   } catch (OutOfStockException e) {            │
│     // Publica evento de falha                │
│     kafkaTemplate.send(                        │
│       "inventory.reservation-failed.v1",       │
│       new InventoryReservationFailedEvent()    │
│     );                                         │
│   }                                            │
│ }                                              │
└────────────────────────────────────────────────┘
           │
           │ InventoryReservedEvent
           ↓
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │inventory        │
    │  .reserved.v1   │
    └─────────────────┘

PASSO 4: Order Service finaliza
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │inventory        │
    │  .reserved.v1   │
    └────────┬────────┘
             │ InventoryReservedEvent
             ↓
┌────────────────────────────────────────────────┐
│ ORDER SERVICE                                  │
│                                                │
│ @KafkaListener(                                │
│   topics = "inventory.reserved.v1"             │
│ )                                              │
│ public void handleInventoryReserved(           │
│     InventoryReservedEvent event               │
│ ) {                                            │
│   // Atualiza order para CONFIRMED             │
│   Order order = orderRepository.findById(      │
│     event.getOrderId()                         │
│   ).orElseThrow();                             │
│                                                │
│   order.setStatus(OrderStatus.CONFIRMED);      │
│   orderRepository.save(order);                 │
│                                                │
│   // Publica evento final                     │
│   kafkaTemplate.send(                          │
│     "order.confirmed.v1",                      │
│     new OrderConfirmedEvent(order)             │
│   );                                           │
│ }                                              │
└────────────────────────────────────────────────┘
```

---

### 3.2. Fluxo com Compensação (Falha)

```
CENÁRIO: Inventory sem estoque

PASSOS 1-2: (Order criado, Payment processado)

PASSO 3: Inventory falha
┌────────────────────────────────────────────────┐
│ INVENTORY SERVICE                              │
│                                                │
│ @KafkaListener(topics = "payment.approved.v1") │
│ public void handlePaymentApproved(...) {       │
│   try {                                        │
│     reserveItems(event);                       │
│   } catch (OutOfStockException e) {            │
│     // ❌ FALHA: Sem estoque!                  │
│     kafkaTemplate.send(                        │
│       "inventory.reservation-failed.v1",       │
│       new InventoryReservationFailedEvent(     │
│         event.getOrderId(),                    │
│         "Out of stock"                         │
│       )                                        │
│     );                                         │
│   }                                            │
│ }                                              │
└────────────────────────────────────────────────┘
           │
           │ InventoryReservationFailedEvent
           ↓
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │inventory        │
    │.reservation-    │
    │  failed.v1      │
    └─────────────────┘

PASSO 4: Payment Service COMPENSA
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │inventory        │
    │.reservation-    │
    │  failed.v1      │
    └────────┬────────┘
             │ InventoryReservationFailedEvent
             ↓
┌────────────────────────────────────────────────┐
│ PAYMENT SERVICE                                │
│                                                │
│ @KafkaListener(                                │
│   topics = "inventory.reservation-failed.v1"   │
│ )                                              │
│ public void handleInventoryFailed(             │
│     InventoryReservationFailedEvent event      │
│ ) {                                            │
│   // COMPENSAÇÃO: Reembolsa payment            │
│   Payment payment = paymentRepository          │
│     .findByOrderId(event.getOrderId())         │
│     .orElseThrow();                            │
│                                                │
│   payment.refund();                            │
│   paymentRepository.save(payment);             │
│                                                │
│   // Publica evento de compensação            │
│   kafkaTemplate.send(                          │
│     "payment.refunded.v1",                     │
│     new PaymentRefundedEvent(payment)          │
│   );                                           │
│ }                                              │
└────────────────────────────────────────────────┘
           │
           │ PaymentRefundedEvent
           ↓
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │ payment         │
    │ .refunded.v1    │
    └─────────────────┘

PASSO 5: Order Service COMPENSA
    ┌─────────────────┐
    │  KAFKA TOPIC    │
    │ payment         │
    │ .refunded.v1    │
    └────────┬────────┘
             │ PaymentRefundedEvent
             ↓
┌────────────────────────────────────────────────┐
│ ORDER SERVICE                                  │
│                                                │
│ @KafkaListener(topics = "payment.refunded.v1") │
│ public void handlePaymentRefunded(             │
│     PaymentRefundedEvent event                 │
│ ) {                                            │
│   // COMPENSAÇÃO: Cancela order                │
│   Order order = orderRepository.findById(      │
│     event.getOrderId()                         │
│   ).orElseThrow();                             │
│                                                │
│   order.setStatus(OrderStatus.CANCELLED);      │
│   orderRepository.save(order);                 │
│                                                │
│   // Publica evento final                     │
│   kafkaTemplate.send(                          │
│     "order.cancelled.v1",                      │
│     new OrderCancelledEvent(order)             │
│   );                                           │
│ }                                              │
└────────────────────────────────────────────────┘

RESULTADO: Order cancelado, Payment reembolsado, sistema consistente ✅
```

---

## 4. Event-Driven Architecture {#event-driven-architecture}

### 4.1. Tipos de Eventos

#### 1. Event Notification (Notificação)

```java
// Evento simples: Notifica que algo aconteceu
public record OrderCreatedEvent(
    UUID eventId,
    UUID orderId,
    LocalDateTime occurredAt
) {}

// Consumidor decide o que fazer
@KafkaListener(topics = "order.created.v1")
public void handleOrderCreated(OrderCreatedEvent event) {
    // Busca dados completos se necessário
    Order order = orderService.getOrder(event.orderId());
    // Processa...
}
```

#### 2. Event-Carried State Transfer (Transferência de Estado)

```java
// Evento carrega TODOS os dados necessários
public record OrderCreatedEvent(
    UUID eventId,
    UUID orderId,
    UUID userId,
    BigDecimal amount,
    String currency,
    List<OrderItem> items,
    LocalDateTime occurredAt
) {}

// Consumidor TEM todos os dados
@KafkaListener(topics = "order.created.v1")
public void handleOrderCreated(OrderCreatedEvent event) {
    // Não precisa buscar nada, evento tem tudo
    processPayment(
        event.orderId(),
        event.amount(),
        event.currency()
    );
}
```

**Recomendação:** Use Event-Carried State Transfer para reduzir acoplamento.

---

### 4.2. Event Naming Convention

```
PADRÃO: {aggregate}.{action}.{version}

Exemplos:
✅ order.created.v1
✅ payment.approved.v1
✅ inventory.reserved.v1
✅ order.cancelled.v1

❌ NÃO USE:
create-order          (não segue padrão)
ORDER_CREATED         (use lowercase)
orderCreated          (use hífens/pontos)
```

---

### 4.3. Event Structure

```java
// ===== ESTRUTURA PADRÃO DE EVENTO =====
public record OrderCreatedEvent(
    // 1. Identificação do evento
    UUID eventId,              // ID único do evento
    String eventType,          // "ORDER_CREATED"
    LocalDateTime occurredAt,  // Timestamp
    String schemaVersion,      // "v1"

    // 2. Correlação
    UUID correlationId,        // Rastreio end-to-end
    UUID causationId,          // Evento que causou este

    // 3. Agregado
    UUID aggregateId,          // ID do Order
    String aggregateType,      // "Order"

    // 4. Dados de negócio
    UUID userId,
    BigDecimal amount,
    String currency,
    List<OrderItem> items,

    // 5. Metadata
    String source              // "order-service"
) {
    public static OrderCreatedEvent from(Order order, UUID correlationId) {
        return new OrderCreatedEvent(
            UUID.randomUUID(),
            "ORDER_CREATED",
            LocalDateTime.now(),
            "v1",
            correlationId,
            null,
            order.getId(),
            "Order",
            order.getUserId(),
            order.getAmount(),
            order.getCurrency(),
            order.getItems(),
            "order-service"
        );
    }
}
```

---

## 5. Implementação Passo a Passo {#implementação-passo-a-passo}

### 5.1. Arquitetura da Solução

```
SERVIÇOS:

1. Order Service
   - Cria orders
   - Escuta: InventoryReservedEvent, PaymentRefundedEvent
   - Publica: OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent

2. Payment Service
   - Processa payments
   - Escuta: OrderCreatedEvent, InventoryReservationFailedEvent
   - Publica: PaymentApprovedEvent, PaymentFailedEvent, PaymentRefundedEvent

3. Inventory Service
   - Reserva/libera inventory
   - Escuta: PaymentApprovedEvent, OrderCancelledEvent
   - Publica: InventoryReservedEvent, InventoryReservationFailedEvent

4. Notification Service (opcional)
   - Envia e-mails
   - Escuta: OrderConfirmedEvent, OrderCancelledEvent


DIAGRAMA:
         ┌─────────────────────────────────────┐
         │          KAFKA CLUSTER              │
         │                                     │
         │  Topics:                            │
         │  - order.created.v1                 │
         │  - payment.approved.v1              │
         │  - payment.failed.v1                │
         │  - payment.refunded.v1              │
         │  - inventory.reserved.v1            │
         │  - inventory.reservation-failed.v1  │
         │  - order.confirmed.v1               │
         │  - order.cancelled.v1               │
         └───────┬─────────────────────┬───────┘
                 │                     │
        ┌────────┼─────────────────────┼────────┐
        │        │                     │        │
        ↓        ↓                     ↓        ↓
  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
  │  Order   │ │ Payment  │ │Inventory │ │Notification│
  │ Service  │ │ Service  │ │ Service  │ │  Service │
  └──────────┘ └──────────┘ └──────────┘ └──────────┘
       ↓             ↓             ↓            ↓
  ┌──────────┐ ┌──────────┐ ┌──────────┐ (sem DB)
  │ Order DB │ │ Payment  │ │Inventory │
  │          │ │   DB     │ │   DB     │
  └──────────┘ └──────────┘ └──────────┘
```

---

### 5.2. Order Service

#### Domain Model

```java
// ===== ORDER ENTITY =====
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status; // PENDING, CONFIRMED, CANCELLED

    private BigDecimal amount;
    private String currency;

    @Convert(converter = JsonConverter.class)
    private List<OrderItem> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== FACTORY METHOD =====
    public static Order create(UUID userId, BigDecimal amount, List<OrderItem> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.amount = amount;
        order.currency = "BRL";
        order.items = items;
        order.createdAt = LocalDateTime.now();
        order.updatedAt = LocalDateTime.now();
        return order;
    }

    // ===== BUSINESS METHODS =====
    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm order with status: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel confirmed order");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters/Setters
}

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}

public record OrderItem(
    String productId,
    int quantity,
    BigDecimal price
) {}
```

#### Service (Publisher)

```java
// ===== ORDER SERVICE (cria order e publica evento) =====
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for userId={}", request.userId());

        // 1. Cria order
        Order order = Order.create(
            request.userId(),
            request.amount(),
            request.items()
        );

        // 2. Salva no banco
        orderRepository.save(order);

        // 3. Cria evento
        OrderCreatedEvent event = OrderCreatedEvent.from(
            order,
            UUID.randomUUID() // correlationId
        );

        // 4. Publica evento
        kafkaTemplate.send("order.created.v1", order.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish OrderCreatedEvent for orderId={}",
                        order.getId(), ex);
                    // ⚠️ PROBLEMA: Order foi salvo mas evento não foi publicado!
                    // SOLUÇÃO: Outbox Pattern (veremos depois)
                } else {
                    log.info("Published OrderCreatedEvent for orderId={}", order.getId());
                }
            });

        return order;
    }
}
```

#### Event Listeners (Consumers)

```java
// ===== ORDER EVENT LISTENERS =====
@Component
@Slf4j
public class OrderEventListener {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderConfirmedEvent> confirmKafkaTemplate;
    private final KafkaTemplate<String, OrderCancelledEvent> cancelKafkaTemplate;
    private final ProcessedEventRepository processedEventRepository;

    // ===== ESCUTA: InventoryReservedEvent =====
    @KafkaListener(
        topics = "inventory.reserved.v1",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for orderId={}", event.orderId());

        // 1. Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        // 2. Busca order
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        // 3. Confirma order
        order.confirm();
        orderRepository.save(order);

        // 4. Marca evento como processado
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        // 5. Publica OrderConfirmedEvent
        OrderConfirmedEvent confirmedEvent = OrderConfirmedEvent.from(
            order,
            event.correlationId()
        );

        confirmKafkaTemplate.send(
            "order.confirmed.v1",
            order.getId().toString(),
            confirmedEvent
        );

        log.info("Order {} confirmed successfully", order.getId());
    }

    // ===== ESCUTA: PaymentRefundedEvent =====
    @KafkaListener(
        topics = "payment.refunded.v1",
        groupId = "order-service-group"
    )
    @Transactional
    public void handlePaymentRefunded(PaymentRefundedEvent event) {
        log.info("Received PaymentRefundedEvent for orderId={}", event.orderId());

        // Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        // Busca order
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        // Cancela order
        order.cancel();
        orderRepository.save(order);

        // Marca evento como processado
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        // Publica OrderCancelledEvent
        OrderCancelledEvent cancelledEvent = OrderCancelledEvent.from(
            order,
            event.correlationId()
        );

        cancelKafkaTemplate.send(
            "order.cancelled.v1",
            order.getId().toString(),
            cancelledEvent
        );

        log.warn("Order {} cancelled due to payment refund", order.getId());
    }
}
```

---

### 5.3. Payment Service

#### Domain Model

```java
// ===== PAYMENT ENTITY =====
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status; // PENDING, APPROVED, FAILED, REFUNDED

    private BigDecimal amount;
    private String currency;

    private String paymentMethod; // CREDIT_CARD, PIX, etc
    private String transactionId;  // ID da transação no gateway

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Payment create(UUID orderId, BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.status = PaymentStatus.PENDING;
        payment.amount = amount;
        payment.currency = currency;
        payment.createdAt = LocalDateTime.now();
        payment.updatedAt = LocalDateTime.now();
        return payment;
    }

    public void approve(String transactionId) {
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund() {
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Can only refund approved payments");
        }
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters/Setters
}

public enum PaymentStatus {
    PENDING,
    APPROVED,
    FAILED,
    REFUNDED
}
```

#### Event Listeners

```java
// ===== PAYMENT EVENT LISTENERS =====
@Component
@Slf4j
public class PaymentEventListener {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final KafkaTemplate<String, PaymentApprovedEvent> approvedKafkaTemplate;
    private final KafkaTemplate<String, PaymentFailedEvent> failedKafkaTemplate;
    private final KafkaTemplate<String, PaymentRefundedEvent> refundedKafkaTemplate;
    private final ProcessedEventRepository processedEventRepository;

    // ===== ESCUTA: OrderCreatedEvent =====
    @KafkaListener(
        topics = "order.created.v1",
        groupId = "payment-service-group"
    )
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for orderId={}", event.aggregateId());

        // Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        try {
            // 1. Cria payment
            Payment payment = Payment.create(
                event.aggregateId(),
                event.amount(),
                event.currency()
            );

            paymentRepository.save(payment);

            // 2. Processa payment no gateway
            String transactionId = paymentGateway.charge(
                payment.getAmount(),
                payment.getCurrency()
            );

            // 3. Aprova payment
            payment.approve(transactionId);
            paymentRepository.save(payment);

            // 4. Marca evento como processado
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            // 5. Publica PaymentApprovedEvent
            PaymentApprovedEvent approvedEvent = PaymentApprovedEvent.from(
                payment,
                event.correlationId()
            );

            approvedKafkaTemplate.send(
                "payment.approved.v1",
                payment.getOrderId().toString(),
                approvedEvent
            );

            log.info("Payment {} approved for order {}", payment.getId(), event.aggregateId());

        } catch (PaymentGatewayException e) {
            log.error("Payment failed for order {}", event.aggregateId(), e);

            // Cria payment com status FAILED
            Payment payment = Payment.create(
                event.aggregateId(),
                event.amount(),
                event.currency()
            );
            payment.fail(e.getMessage());
            paymentRepository.save(payment);

            // Marca evento como processado
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            // Publica PaymentFailedEvent
            PaymentFailedEvent failedEvent = PaymentFailedEvent.from(
                payment,
                event.correlationId(),
                e.getMessage()
            );

            failedKafkaTemplate.send(
                "payment.failed.v1",
                payment.getOrderId().toString(),
                failedEvent
            );
        }
    }

    // ===== ESCUTA: InventoryReservationFailedEvent =====
    @KafkaListener(
        topics = "inventory.reservation-failed.v1",
        groupId = "payment-service-group"
    )
    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.warn("Received InventoryReservationFailedEvent for orderId={}. Refunding payment...",
            event.orderId());

        // Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        // Busca payment
        Payment payment = paymentRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new PaymentNotFoundException(event.orderId()));

        // Reembolsa no gateway
        paymentGateway.refund(payment.getTransactionId());

        // Atualiza payment
        payment.refund();
        paymentRepository.save(payment);

        // Marca evento como processado
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        // Publica PaymentRefundedEvent
        PaymentRefundedEvent refundedEvent = PaymentRefundedEvent.from(
            payment,
            event.correlationId()
        );

        refundedKafkaTemplate.send(
            "payment.refunded.v1",
            payment.getOrderId().toString(),
            refundedEvent
        );

        log.info("Payment {} refunded successfully", payment.getId());
    }
}
```

---

### 5.4. Inventory Service

#### Domain Model

```java
// ===== INVENTORY ENTITY =====
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private String productId;

    private int availableQuantity;
    private int reservedQuantity;

    private LocalDateTime updatedAt;

    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new OutOfStockException(
                "Product " + productId + " has only " + availableQuantity + " available"
            );
        }

        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void release(int quantity) {
        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters/Setters
}

// ===== RESERVATION ENTITY =====
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    private UUID orderId;

    @Convert(converter = JsonConverter.class)
    private List<ReservedItem> items;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status; // RESERVED, RELEASED

    private LocalDateTime createdAt;

    public static Reservation create(UUID orderId, List<ReservedItem> items) {
        Reservation reservation = new Reservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.items = items;
        reservation.status = ReservationStatus.RESERVED;
        reservation.createdAt = LocalDateTime.now();
        return reservation;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    // Getters/Setters
}

public record ReservedItem(
    String productId,
    int quantity
) {}

public enum ReservationStatus {
    RESERVED,
    RELEASED
}
```

#### Event Listeners

```java
// ===== INVENTORY EVENT LISTENERS =====
@Component
@Slf4j
public class InventoryEventListener {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, InventoryReservedEvent> reservedKafkaTemplate;
    private final KafkaTemplate<String, InventoryReservationFailedEvent> failedKafkaTemplate;
    private final ProcessedEventRepository processedEventRepository;

    // ===== ESCUTA: PaymentApprovedEvent =====
    @KafkaListener(
        topics = "payment.approved.v1",
        groupId = "inventory-service-group"
    )
    @Transactional
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        log.info("Received PaymentApprovedEvent for orderId={}. Reserving inventory...",
            event.orderId());

        // Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        try {
            // 1. Reserva items
            List<ReservedItem> reservedItems = new ArrayList<>();

            for (OrderItem item : event.items()) {
                Inventory inventory = inventoryRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));

                inventory.reserve(item.quantity());
                inventoryRepository.save(inventory);

                reservedItems.add(new ReservedItem(item.productId(), item.quantity()));
            }

            // 2. Cria reservation
            Reservation reservation = Reservation.create(event.orderId(), reservedItems);
            reservationRepository.save(reservation);

            // 3. Marca evento como processado
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            // 4. Publica InventoryReservedEvent
            InventoryReservedEvent reservedEvent = InventoryReservedEvent.from(
                reservation,
                event.correlationId()
            );

            reservedKafkaTemplate.send(
                "inventory.reserved.v1",
                event.orderId().toString(),
                reservedEvent
            );

            log.info("Inventory reserved for order {}", event.orderId());

        } catch (OutOfStockException e) {
            log.error("Inventory reservation failed for order {}", event.orderId(), e);

            // Marca evento como processado
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            // Publica InventoryReservationFailedEvent
            InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                UUID.randomUUID(),
                "INVENTORY_RESERVATION_FAILED",
                LocalDateTime.now(),
                "v1",
                event.correlationId(),
                event.eventId(),
                event.orderId(),
                e.getMessage(),
                "inventory-service"
            );

            failedKafkaTemplate.send(
                "inventory.reservation-failed.v1",
                event.orderId().toString(),
                failedEvent
            );
        }
    }

    // ===== ESCUTA: OrderCancelledEvent =====
    @KafkaListener(
        topics = "order.cancelled.v1",
        groupId = "inventory-service-group"
    )
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for orderId={}. Releasing inventory...",
            event.aggregateId());

        // Verifica idempotência
        if (processedEventRepository.exists(event.eventId())) {
            log.warn("Event {} already processed. Skipping.", event.eventId());
            return;
        }

        // Busca reservation
        Reservation reservation = reservationRepository.findByOrderId(event.aggregateId())
            .orElse(null);

        if (reservation == null) {
            log.warn("No reservation found for order {}. Skipping release.", event.aggregateId());
            processedEventRepository.save(new ProcessedEvent(event.eventId()));
            return;
        }

        // Libera items
        for (ReservedItem item : reservation.getItems()) {
            Inventory inventory = inventoryRepository.findById(item.productId())
                .orElseThrow();

            inventory.release(item.quantity());
            inventoryRepository.save(inventory);
        }

        // Marca reservation como released
        reservation.release();
        reservationRepository.save(reservation);

        // Marca evento como processado
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        log.info("Inventory released for order {}", event.aggregateId());
    }
}
```

---

## 6. Eventos de Domínio {#eventos-de-domínio}

### 6.1. Eventos Completos

```java
// ===== ORDER EVENTS =====
public record OrderCreatedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID aggregateId,      // orderId
    String aggregateType,
    UUID userId,
    BigDecimal amount,
    String currency,
    List<OrderItem> items,
    String source
) {
    public static OrderCreatedEvent from(Order order, UUID correlationId) {
        return new OrderCreatedEvent(
            UUID.randomUUID(),
            "ORDER_CREATED",
            LocalDateTime.now(),
            "v1",
            correlationId,
            null,
            order.getId(),
            "Order",
            order.getUserId(),
            order.getAmount(),
            order.getCurrency(),
            order.getItems(),
            "order-service"
        );
    }
}

public record OrderConfirmedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID aggregateId,      // orderId
    String aggregateType,
    String source
) {
    public static OrderConfirmedEvent from(Order order, UUID correlationId) {
        return new OrderConfirmedEvent(
            UUID.randomUUID(),
            "ORDER_CONFIRMED",
            LocalDateTime.now(),
            "v1",
            correlationId,
            null,
            order.getId(),
            "Order",
            "order-service"
        );
    }
}

public record OrderCancelledEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID aggregateId,      // orderId
    String aggregateType,
    String reason,
    String source
) {
    public static OrderCancelledEvent from(Order order, UUID correlationId) {
        return new OrderCancelledEvent(
            UUID.randomUUID(),
            "ORDER_CANCELLED",
            LocalDateTime.now(),
            "v1",
            correlationId,
            null,
            order.getId(),
            "Order",
            "Payment refunded or inventory unavailable",
            "order-service"
        );
    }
}

// ===== PAYMENT EVENTS =====
public record PaymentApprovedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID paymentId,
    UUID orderId,
    BigDecimal amount,
    String currency,
    String transactionId,
    List<OrderItem> items,  // Para Inventory usar
    String source
) {}

public record PaymentFailedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID paymentId,
    UUID orderId,
    String reason,
    String source
) {}

public record PaymentRefundedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID paymentId,
    UUID orderId,
    String source
) {}

// ===== INVENTORY EVENTS =====
public record InventoryReservedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID reservationId,
    UUID orderId,
    List<ReservedItem> items,
    String source
) {}

public record InventoryReservationFailedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    String schemaVersion,
    UUID correlationId,
    UUID causationId,
    UUID orderId,
    String reason,
    String source
) {}
```

---

## 7. Compensações Distribuídas {#compensações-distribuídas}

### 7.1. Estratégias de Compensação

```
COMPENSAÇÃO EM COREOGRAFIA:
Cada serviço é responsável por compensar SUA PRÓPRIA transação

┌───────────────────────────────────────────────────────────┐
│ FLUXO NORMAL (Happy Path):                                │
│                                                           │
│ Order Service    → OrderCreatedEvent                      │
│ Payment Service  → PaymentApprovedEvent                   │
│ Inventory Service → InventoryReservedEvent                │
│ Order Service    → OrderConfirmedEvent ✅                 │
└───────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│ COMPENSAÇÃO (Inventory falhou):                           │
│                                                           │
│ Order Service    → OrderCreatedEvent                      │
│ Payment Service  → PaymentApprovedEvent                   │
│ Inventory Service → InventoryReservationFailedEvent ❌    │
│                                                           │
│ COMPENSAÇÕES (eventos de compensação):                    │
│ Payment Service  → PaymentRefundedEvent (COMPENSAÇÃO 1)   │
│ Order Service    → OrderCancelledEvent  (COMPENSAÇÃO 2)   │
└───────────────────────────────────────────────────────────┘
```

### 7.2. Exemplo: Compensação Completa

```java
// ===== INVENTORY SERVICE =====
// Falha ao reservar → Publica evento de falha
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    try {
        reserveInventory(event);
        // Publica InventoryReservedEvent
    } catch (OutOfStockException e) {
        // ❌ FALHA: Publica evento de falha
        kafkaTemplate.send(
            "inventory.reservation-failed.v1",
            new InventoryReservationFailedEvent(event.orderId(), e.getMessage())
        );
    }
}

// ===== PAYMENT SERVICE =====
// Escuta falha de Inventory → Compensa (refund)
@KafkaListener(topics = "inventory.reservation-failed.v1")
public void handleInventoryFailed(InventoryReservationFailedEvent event) {
    log.warn("Inventory failed for order {}. Compensating payment...", event.orderId());

    // COMPENSAÇÃO: Reembolsa
    Payment payment = paymentRepository.findByOrderId(event.orderId()).orElseThrow();
    paymentGateway.refund(payment.getTransactionId());
    payment.refund();
    paymentRepository.save(payment);

    // Publica evento de compensação
    kafkaTemplate.send(
        "payment.refunded.v1",
        new PaymentRefundedEvent(payment.getOrderId())
    );
}

// ===== ORDER SERVICE =====
// Escuta refund → Compensa (cancela order)
@KafkaListener(topics = "payment.refunded.v1")
public void handlePaymentRefunded(PaymentRefundedEvent event) {
    log.warn("Payment refunded for order {}. Compensating order...", event.orderId());

    // COMPENSAÇÃO: Cancela
    Order order = orderRepository.findById(event.orderId()).orElseThrow();
    order.cancel();
    orderRepository.save(order);

    // Publica evento de compensação
    kafkaTemplate.send(
        "order.cancelled.v1",
        new OrderCancelledEvent(order.getId())
    );
}
```

---

### 7.3. Compensação com Retry

```java
@KafkaListener(topics = "inventory.reservation-failed.v1")
@Retryable(
    value = {PaymentGatewayException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
public void handleInventoryFailed(InventoryReservationFailedEvent event) {
    // Tenta compensar
    Payment payment = paymentRepository.findByOrderId(event.orderId()).orElseThrow();
    paymentGateway.refund(payment.getTransactionId()); // ← Pode falhar

    payment.refund();
    paymentRepository.save(payment);

    kafkaTemplate.send("payment.refunded.v1", new PaymentRefundedEvent(payment));
}

@Recover
public void recoverInventoryFailed(
    PaymentGatewayException e,
    InventoryReservationFailedEvent event
) {
    // ❌ Compensação falhou após 5 tentativas
    log.error("Failed to compensate payment for order {} after retries",
        event.orderId(), e);

    // Envia para DLQ
    kafkaTemplate.send(
        "payment.compensation-failed.dlq",
        new CompensationFailedMessage(event.orderId(), e.getMessage())
    );

    // Alerta time de ops
    alertService.sendCriticalAlert(
        "PAYMENT COMPENSATION FAILED",
        "OrderId: " + event.orderId()
    );
}
```

---

## 8. Idempotência em Consumers {#idempotência}

### 8.1. O Problema

```
PROBLEMA: Kafka pode entregar mensagem MÚLTIPLAS VEZES

┌─────────────────────────────────────────────┐
│ Kafka envia PaymentApprovedEvent            │
└────────┬────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────────┐
│ Inventory Service                              │
│ - Processa evento                              │
│ - Reserva 10 items                             │
│ - Kafka consumer CRASHA antes de commitar     │
│   offset                                       │
└────────────────────────────────────────────────┘
         ↑
         │ (restart)
         │
┌────────────────────────────────────────────────┐
│ Kafka RE-ENVIA MESMO PaymentApprovedEvent      │
└────────┬───────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────────┐
│ Inventory Service                              │
│ - Processa evento NOVAMENTE                    │
│ - Reserva MAIS 10 items  ← DUPLICADO!          │
│                                                │
│ RESULTADO: 20 items reservados em vez de 10!   │
└────────────────────────────────────────────────┘
```

---

### 8.2. Solução: Processed Events Table

```java
// ===== PROCESSED EVENT ENTITY =====
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;  // ← ID do evento (não do aggregate!)

    private LocalDateTime processedAt;

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
    }

    // Getters/Setters
}

// ===== REPOSITORY =====
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    default boolean exists(UUID eventId) {
        return existsById(eventId);
    }
}

// ===== CONSUMER COM IDEMPOTÊNCIA =====
@KafkaListener(topics = "payment.approved.v1")
@Transactional
public void handlePaymentApproved(PaymentApprovedEvent event) {
    // 1. Verifica se já processou este evento
    if (processedEventRepository.exists(event.eventId())) {
        log.warn("Event {} already processed. Skipping.", event.eventId());
        return; // ← Não processa novamente
    }

    // 2. Processa evento
    reserveInventory(event);

    // 3. Marca como processado (MESMA TRANSAÇÃO)
    processedEventRepository.save(new ProcessedEvent(event.eventId()));

    // 4. Publica próximo evento
    kafkaTemplate.send("inventory.reserved.v1", ...);
}
```

**IMPORTANTE:** `processedEventRepository.save()` deve estar na **mesma transação** que o processamento do evento.

---

### 8.3. Migration para Processed Events

```sql
-- V2__create_processed_events_table.sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);

-- Cleanup job: Remove eventos processados há mais de 30 dias
CREATE OR REPLACE FUNCTION cleanup_old_processed_events()
RETURNS void AS $$
BEGIN
    DELETE FROM processed_events
    WHERE processed_at < NOW() - INTERVAL '30 days';
END;
$$ LANGUAGE plpgsql;
```

---

## 9. Dead Letter Queue (DLQ) {#dead-letter-queue}

### 9.1. O que é DLQ

**Dead Letter Queue** é um tópico Kafka onde mensagens que **falharam repetidamente** são enviadas para análise manual.

```
FLUXO COM DLQ:

┌─────────────────────────────────────┐
│ payment.approved.v1 (Topic normal)   │
└────────┬────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────┐
│ Inventory Service                      │
│ Tenta processar evento                 │
└───┬────────────────────────────────────┘
    │
    ├─ Sucesso → Commit offset ✅
    │
    └─ Falha → Retry
         ├─ Retry 1: Falha
         ├─ Retry 2: Falha
         └─ Retry 3: Falha ❌

                ↓ (após 3 retries)

┌─────────────────────────────────────┐
│ payment.approved.v1.dlq (DLQ Topic) │ ← Mensagem enviada aqui
└─────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────┐
│ DLQ Monitor / Manual Investigation     │
│ - Analisa erro                         │
│ - Corrige problema                     │
│ - Re-processa manualmente              │
└────────────────────────────────────────┘
```

---

### 9.2. Implementação com Spring Kafka

```java
// ===== KAFKA ERROR HANDLER =====
@Configuration
public class KafkaErrorHandlingConfig {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public DefaultErrorHandler errorHandler() {
        // 1. Retry até 3 vezes com backoff exponencial
        BackOff backOff = new ExponentialBackOff(
            1000L,  // Initial interval: 1s
            2.0     // Multiplier: 2x
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            // 2. Se falhar após retries → Envia para DLQ
            (record, exception) -> {
                log.error("Failed to process record after retries. Sending to DLQ. Topic: {}, Partition: {}, Offset: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception
                );

                // Envia para DLQ
                String dlqTopic = record.topic() + ".dlq";
                kafkaTemplate.send(dlqTopic, record.key(), record.value());
            },
            backOff
        );

        // 3. Não faz retry para estas exceções (não retryable)
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            InvalidEventException.class
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler()); // ← Configura error handler
        return factory;
    }
}
```

---

### 9.3. DLQ Consumer (Monitoramento)

```java
// ===== DLQ CONSUMER =====
@Component
@Slf4j
public class DlqConsumer {

    private final AlertService alertService;

    @KafkaListener(
        topics = {
            "payment.approved.v1.dlq",
            "inventory.reserved.v1.dlq",
            "order.created.v1.dlq"
        },
        groupId = "dlq-monitor-group"
    )
    public void handleDlqMessage(
        @Payload Object message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.OFFSET) Long offset
    ) {
        log.error("Message sent to DLQ. Topic: {}, Offset: {}, Message: {}",
            topic, offset, message);

        // Alerta time de ops
        alertService.sendCriticalAlert(
            "MESSAGE IN DLQ",
            String.format("Topic: %s, Offset: %d, Message: %s", topic, offset, message)
        );

        // Armazena em banco para análise
        dlqRepository.save(new DlqMessage(
            topic,
            offset,
            message.toString(),
            LocalDateTime.now()
        ));
    }
}

// ===== DLQ MESSAGE ENTITY =====
@Entity
@Table(name = "dlq_messages")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;
    private Long offset;

    @Column(length = 10000)
    private String message;

    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    private DlqStatus status; // PENDING, REPROCESSED, IGNORED

    // Getters/Setters
}

public enum DlqStatus {
    PENDING,      // Aguardando análise
    REPROCESSED,  // Re-processado manualmente
    IGNORED       // Ignorado (erro conhecido, não retryable)
}
```

---

### 9.4. Re-processamento Manual

```java
// ===== ENDPOINT PARA RE-PROCESSAR DLQ =====
@RestController
@RequestMapping("/api/dlq")
public class DlqController {

    private final DlqRepository dlqRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<String> reprocessMessage(@PathVariable Long id) {
        DlqMessage dlqMessage = dlqRepository.findById(id)
            .orElseThrow(() -> new DlqMessageNotFoundException(id));

        if (dlqMessage.getStatus() == DlqStatus.REPROCESSED) {
            return ResponseEntity.badRequest().body("Message already reprocessed");
        }

        // Re-envia para tópico original (sem .dlq)
        String originalTopic = dlqMessage.getTopic().replace(".dlq", "");

        kafkaTemplate.send(originalTopic, dlqMessage.getMessage());

        // Marca como reprocessado
        dlqMessage.setStatus(DlqStatus.REPROCESSED);
        dlqRepository.save(dlqMessage);

        return ResponseEntity.ok("Message reprocessed successfully");
    }

    @GetMapping
    public List<DlqMessageResponse> getPendingMessages() {
        return dlqRepository.findByStatus(DlqStatus.PENDING)
            .stream()
            .map(DlqMessageResponse::from)
            .toList();
    }
}
```

---

## 10. Event Versioning {#event-versioning}

### 10.1. O Problema

```
PROBLEMA: Como evoluir schema de eventos sem quebrar consumers?

VERSÃO 1 (inicial):
{
  "eventId": "uuid",
  "orderId": "uuid",
  "amount": 100.50
}

VERSÃO 2 (adicionamos currency):
{
  "eventId": "uuid",
  "orderId": "uuid",
  "amount": 100.50,
  "currency": "BRL"  ← NOVO CAMPO
}

❌ Consumer antigo vai quebrar se espera v2
❌ Producer novo vai quebrar consumer antigo
```

---

### 10.2. Estratégia: Schema Versioning

#### Abordagem 1: Versionamento no Nome do Tópico

```
TÓPICOS:
- order.created.v1  ← Versão 1
- order.created.v2  ← Versão 2

CONSUMERS:
- Consumers antigos → Escutam order.created.v1
- Consumers novos → Escutam order.created.v2

PRODUCERS:
- Produzem em AMBOS tópicos durante transição:
  kafkaTemplate.send("order.created.v1", eventV1);
  kafkaTemplate.send("order.created.v2", eventV2);
```

**Implementação:**

```java
// ===== PRODUCER (publica em ambas versões) =====
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderCreatedEventV1> kafkaTemplateV1;
    private final KafkaTemplate<String, OrderCreatedEventV2> kafkaTemplateV2;

    public void publishOrderCreated(Order order) {
        // V1 (backward compatibility)
        OrderCreatedEventV1 eventV1 = new OrderCreatedEventV1(
            UUID.randomUUID(),
            order.getId(),
            order.getAmount()
            // ← Sem currency
        );
        kafkaTemplateV1.send("order.created.v1", eventV1);

        // V2 (nova versão)
        OrderCreatedEventV2 eventV2 = new OrderCreatedEventV2(
            UUID.randomUUID(),
            order.getId(),
            order.getAmount(),
            order.getCurrency()  // ← Novo campo
        );
        kafkaTemplateV2.send("order.created.v2", eventV2);
    }
}

// ===== CONSUMER V1 (antigo) =====
@KafkaListener(topics = "order.created.v1")
public void handleOrderCreatedV1(OrderCreatedEventV1 event) {
    // Processa versão antiga
}

// ===== CONSUMER V2 (novo) =====
@KafkaListener(topics = "order.created.v2")
public void handleOrderCreatedV2(OrderCreatedEventV2 event) {
    // Processa versão nova (com currency)
}
```

---

#### Abordagem 2: Schema Evolution (Campo Opcional)

```java
// ===== V1 (initial) =====
public record OrderCreatedEvent(
    UUID eventId,
    UUID orderId,
    BigDecimal amount
) {}

// ===== V2 (adicionamos currency OPCIONAL) =====
public record OrderCreatedEvent(
    UUID eventId,
    UUID orderId,
    BigDecimal amount,
    @JsonProperty(defaultValue = "BRL")  // ← Valor padrão
    String currency
) {}

// CONSUMER (compatível com ambas versões):
@KafkaListener(topics = "order.created.v1")
public void handleOrderCreated(OrderCreatedEvent event) {
    // Se evento V1 → currency será "BRL" (default)
    // Se evento V2 → currency será o valor real
    String currency = event.currency() != null ? event.currency() : "BRL";
}
```

**Regras para Schema Evolution:**

✅ **PODE:**
- Adicionar campos OPCIONAIS com valores padrão
- Remover campos OPCIONAIS
- Renomear campos (com alias `@JsonAlias`)

❌ **NÃO PODE:**
- Adicionar campos OBRIGATÓRIOS
- Mudar tipo de campo existente
- Remover campos obrigatórios

---

### 10.3. Schema Registry (Avro + Confluent)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.5.0</version>
</dependency>
```

```avro
// order-created-event.avsc (Avro schema)
{
  "namespace": "com.company.events",
  "type": "record",
  "name": "OrderCreatedEvent",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "orderId", "type": "string"},
    {"name": "amount", "type": "double"},
    {"name": "currency", "type": ["null", "string"], "default": null}
  ]
}
```

```yaml
# application.yml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      properties:
        schema.registry.url: http://localhost:8081

    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: http://localhost:8081
        specific.avro.reader: true
```

**Vantagem:** Schema Registry garante compatibilidade automaticamente.

---

## 11. Kafka Configuration {#kafka-configuration}

### 11.1. Producer Configuration

```yaml
# application.yml (Producer)
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

      # Acknowledgment
      acks: all  # ← IMPORTANTE: Aguarda ack de todas réplicas

      # Idempotência
      properties:
        enable.idempotence: true  # ← Evita duplicatas
        max.in.flight.requests.per.connection: 5

      # Retry
      retries: 3
      properties:
        retry.backoff.ms: 1000  # 1s entre retries

      # Compression
      compression-type: snappy

      # Batch
      batch-size: 16384  # 16KB
      properties:
        linger.ms: 10  # Aguarda 10ms para formar batch
```

### 11.2. Consumer Configuration

```yaml
# application.yml (Consumer)
spring:
  kafka:
    bootstrap-servers: localhost:9092

    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

      # Group ID
      group-id: payment-service-group

      # Auto-offset reset
      auto-offset-reset: earliest  # ← Inicia do começo se não tiver offset

      # Manual commit (recomendado para idempotência)
      enable-auto-commit: false

      # Desserialização
      properties:
        spring.json.trusted.packages: "*"
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer

      # Max poll
      max-poll-records: 10  # Processa 10 mensagens por vez
      properties:
        max.poll.interval.ms: 300000  # 5 minutos (tempo máximo para processar batch)

    # Listener
    listener:
      ack-mode: manual  # ← Commit manual (após processar com sucesso)
```

### 11.3. Topic Configuration

```java
// ===== TOPIC CONFIGURATION =====
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order.created.v1")
            .partitions(3)        // 3 partitions para paralelismo
            .replicas(2)          // 2 réplicas para durabilidade
            .config("retention.ms", "604800000")  // 7 dias
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic paymentApprovedTopic() {
        return TopicBuilder.name("payment.approved.v1")
            .partitions(3)
            .replicas(2)
            .config("retention.ms", "604800000")
            .build();
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name("inventory.reserved.v1")
            .partitions(3)
            .replicas(2)
            .config("retention.ms", "604800000")
            .build();
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name("order.confirmed.v1")
            .partitions(3)
            .replicas(2)
            .config("retention.ms", "604800000")
            .build();
    }

    // DLQ Topics
    @Bean
    public NewTopic paymentApprovedDlqTopic() {
        return TopicBuilder.name("payment.approved.v1.dlq")
            .partitions(1)   // DLQ geralmente tem 1 partition
            .replicas(2)
            .config("retention.ms", "2592000000")  // 30 dias
            .build();
    }
}
```

---

## 12. Monitoramento e Observabilidade {#monitoramento}

### 12.1. Métricas com Micrometer

```java
// ===== METRICS =====
@Component
public class KafkaMetrics {

    private final MeterRegistry meterRegistry;

    public void recordEventPublished(String eventType) {
        meterRegistry.counter("kafka.events.published",
            "event_type", eventType
        ).increment();
    }

    public void recordEventConsumed(String eventType, boolean success) {
        meterRegistry.counter("kafka.events.consumed",
            "event_type", eventType,
            "status", success ? "success" : "failure"
        ).increment();
    }

    public void recordEventProcessingTime(String eventType, long durationMs) {
        meterRegistry.timer("kafka.event.processing.duration",
            "event_type", eventType
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }
}

// ===== USO =====
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    long startTime = System.currentTimeMillis();

    try {
        // Processa evento
        reserveInventory(event);

        // Métrica de sucesso
        kafkaMetrics.recordEventConsumed("PaymentApproved", true);

    } catch (Exception e) {
        // Métrica de falha
        kafkaMetrics.recordEventConsumed("PaymentApproved", false);
        throw e;

    } finally {
        // Métrica de latência
        long duration = System.currentTimeMillis() - startTime;
        kafkaMetrics.recordEventProcessingTime("PaymentApproved", duration);
    }
}
```

---

### 12.2. Distributed Tracing com Sleuth

```java
// ===== PROPAGAÇÃO DE TRACE =====
@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final Tracer tracer;

    public void publishOrderCreated(Order order) {
        Span span = tracer.nextSpan().name("publish-order-created-event").start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // Adiciona trace context ao evento
            String traceId = span.context().traceIdString();
            String spanId = span.context().spanIdString();

            OrderCreatedEvent event = new OrderCreatedEvent(
                // ... outros campos
                traceId,  // ← Propagação de trace
                spanId
            );

            kafkaTemplate.send("order.created.v1", event);

            span.tag("event.type", "ORDER_CREATED");
            span.tag("order.id", order.getId().toString());

        } finally {
            span.end();
        }
    }
}

// ===== CONSUMER COM TRACE =====
@KafkaListener(topics = "order.created.v1")
public void handleOrderCreated(OrderCreatedEvent event) {
    // Cria span filho usando traceId/spanId do evento
    Span span = tracer.nextSpan()
        .name("handle-order-created-event")
        .tag("event.id", event.eventId().toString())
        .tag("order.id", event.orderId().toString())
        .start();

    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
        // Processa evento
        processPayment(event);

    } finally {
        span.end();
    }
}
```

**Trace no Zipkin:**

```
TRACE ID: abc123def456

[POST /orders]
    │
    ├─ [OrderService.createOrder] 150ms
    │  └─ [publish-order-created-event] 10ms
    │
    ├─ [PaymentService.handleOrderCreated] 250ms ← Span filho
    │  ├─ [PaymentGateway.charge] 200ms
    │  └─ [publish-payment-approved-event] 10ms
    │
    └─ [InventoryService.handlePaymentApproved] 180ms ← Span filho
       ├─ [InventoryService.reserve] 150ms
       └─ [publish-inventory-reserved-event] 10ms

TOTAL: 590ms
```

---

## 13. Implementação Completa com Spring Boot {#implementação-completa}

Devido ao tamanho, veja a implementação completa nos exemplos das seções anteriores. Principais pontos:

### 13.1. Estrutura de Projeto

```
order-service/
├── src/main/java/com/company/order/
│   ├── OrderServiceApplication.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java
│   │   │   └── OrderStatus.java
│   │   └── repository/
│   │       ├── OrderRepository.java
│   │       └── ProcessedEventRepository.java
│   ├── application/
│   │   ├── service/
│   │   │   └── OrderService.java
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   └── dto/
│   │       ├── CreateOrderRequest.java
│   │       └── OrderResponse.java
│   ├── infrastructure/
│   │   ├── messaging/
│   │   │   ├── event/
│   │   │   │   ├── OrderCreatedEvent.java
│   │   │   │   ├── OrderConfirmedEvent.java
│   │   │   │   └── OrderCancelledEvent.java
│   │   │   ├── publisher/
│   │   │   │   └── OrderEventPublisher.java
│   │   │   └── listener/
│   │   │       └── OrderEventListener.java
│   │   └── config/
│   │       ├── KafkaProducerConfig.java
│   │       ├── KafkaConsumerConfig.java
│   │       ├── KafkaTopicConfig.java
│   │       └── KafkaErrorHandlingConfig.java
│   └── job/
│       └── ProcessedEventCleanupJob.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_orders_table.sql
        └── V2__create_processed_events_table.sql
```

### 13.2. Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  # Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  # Redpanda Console (Kafka UI)
  redpanda-console:
    image: redpandadata/console:latest
    ports:
      - "8089:8080"
    environment:
      KAFKA_BROKERS: kafka:9092
    depends_on:
      - kafka

  # PostgreSQL (Order Service)
  order-db:
    image: postgres:15
    environment:
      POSTGRES_DB: order_service
      POSTGRES_USER: order_user
      POSTGRES_PASSWORD: order_pass
    ports:
      - "5432:5432"

  # PostgreSQL (Payment Service)
  payment-db:
    image: postgres:15
    environment:
      POSTGRES_DB: payment_service
      POSTGRES_USER: payment_user
      POSTGRES_PASSWORD: payment_pass
    ports:
      - "5433:5432"

  # PostgreSQL (Inventory Service)
  inventory-db:
    image: postgres:15
    environment:
      POSTGRES_DB: inventory_service
      POSTGRES_USER: inventory_user
      POSTGRES_PASSWORD: inventory_pass
    ports:
      - "5434:5432"

  # Order Service
  order-service:
    build: ./order-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://order-db:5432/order_service
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - order-db
      - kafka

  # Payment Service
  payment-service:
    build: ./payment-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://payment-db:5432/payment_service
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - payment-db
      - kafka

  # Inventory Service
  inventory-service:
    build: ./inventory-service
    ports:
      - "8083:8083"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://inventory-db:5432/inventory_service
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - inventory-db
      - kafka

  # Zipkin (Tracing)
  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"

  # Prometheus
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  # Grafana
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```

---

## 14. Testes {#testes}

### 14.1. Teste com EmbeddedKafka

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"order.created.v1", "payment.approved.v1"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, OrderCreatedEvent> consumer;

    @BeforeEach
    void setUp() {
        // Cria consumer para verificar eventos publicados
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            "test-group",
            "true",
            embeddedKafka
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        ConsumerFactory<String, OrderCreatedEvent> consumerFactory =
            new DefaultKafkaConsumerFactory<>(consumerProps);

        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList("order.created.v1"));
    }

    @Test
    void shouldPublishOrderCreatedEvent() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            UUID.randomUUID(),
            BigDecimal.valueOf(100.00),
            List.of(new OrderItem("Product1", 2, BigDecimal.valueOf(50.00)))
        );

        // Act
        Order order = orderService.createOrder(request);

        // Assert - Order salvo no banco
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Assert - Evento publicado
        ConsumerRecords<String, OrderCreatedEvent> records =
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isEqualTo(1);

        OrderCreatedEvent event = records.iterator().next().value();
        assertThat(event.aggregateId()).isEqualTo(order.getId());
        assertThat(event.amount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }
}
```

---

### 14.2. Teste de Consumer

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"inventory.reserved.v1"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OrderEventListenerTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, InventoryReservedEvent> kafkaTemplate;

    @Test
    void shouldConfirmOrderWhenInventoryReserved() throws InterruptedException {
        // Arrange - Cria order PENDING
        Order order = Order.create(
            UUID.randomUUID(),
            BigDecimal.valueOf(100.00),
            List.of()
        );
        orderRepository.save(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Act - Publica InventoryReservedEvent
        InventoryReservedEvent event = new InventoryReservedEvent(
            UUID.randomUUID(),
            "INVENTORY_RESERVED",
            LocalDateTime.now(),
            "v1",
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            order.getId(),
            List.of(),
            "inventory-service"
        );

        kafkaTemplate.send("inventory.reserved.v1", event);

        // Wait for processing
        Thread.sleep(2000);

        // Assert - Order confirmado
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Assert - Evento marcado como processado
        assertThat(processedEventRepository.exists(event.eventId())).isTrue();
    }

    @Test
    void shouldNotProcessDuplicateEvent() throws InterruptedException {
        // Arrange
        Order order = Order.create(UUID.randomUUID(), BigDecimal.valueOf(100.00), List.of());
        orderRepository.save(order);

        InventoryReservedEvent event = new InventoryReservedEvent(...);

        // Act - Envia evento 2x
        kafkaTemplate.send("inventory.reserved.v1", event);
        Thread.sleep(1000);
        kafkaTemplate.send("inventory.reserved.v1", event);
        Thread.sleep(1000);

        // Assert - Processado apenas 1x (idempotência)
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}
```

---

## 15. Armadilhas Comuns {#armadilhas}

### 15.1. Não Garantir Idempotência

```java
// ❌ ERRADO: Sem idempotência
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    // Se mensagem for reprocessada → duplica!
    reserveInventory(event);
}

// ✅ CORRETO: Com idempotência
@KafkaListener(topics = "payment.approved.v1")
@Transactional
public void handlePaymentApproved(PaymentApprovedEvent event) {
    if (processedEventRepository.exists(event.eventId())) {
        return; // Já processado
    }

    reserveInventory(event);
    processedEventRepository.save(new ProcessedEvent(event.eventId()));
}
```

---

### 15.2. Dual Write Problem (Event sem Outbox)

```java
// ❌ PROBLEMA: Dual Write
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = Order.create(...);
    orderRepository.save(order); // ← Commit aqui

    // ⚠️ Se falhar aqui, order foi salvo mas evento não foi publicado!
    kafkaTemplate.send("order.created.v1", new OrderCreatedEvent(order));

    return order;
}

// ✅ SOLUÇÃO 1: Outbox Pattern
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = Order.create(...);
    orderRepository.save(order);

    // Salva evento no outbox (mesma transação!)
    outboxRepository.save(new OutboxEvent("order.created.v1", order));

    // Job separado publica eventos do outbox
    return order;
}

// ✅ SOLUÇÃO 2: Transactional Outbox com Debezium
// (CDC captura mudanças no banco e publica automaticamente)
```

---

### 15.3. Falta de Tratamento de Erros

```java
// ❌ ERRADO: Sem tratamento de erro
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    reserveInventory(event); // ← Pode lançar exceção
    // Se falhar, Kafka vai fazer retry INFINITO!
}

// ✅ CORRETO: Com error handling
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    try {
        reserveInventory(event);
    } catch (OutOfStockException e) {
        // Publica evento de compensação
        kafkaTemplate.send(
            "inventory.reservation-failed.v1",
            new InventoryReservationFailedEvent(event.orderId(), e.getMessage())
        );
    } catch (Exception e) {
        // Erro inesperado → DLQ
        log.error("Unexpected error processing event", e);
        throw e; // Error handler envia para DLQ
    }
}
```

---

### 15.4. Consumer Lento Bloqueia Partition

```
PROBLEMA: 1 consumer lento bloqueia partition inteira

Partition 0: [msg1] [msg2] [msg3] ← Consumer A (LENTO, 10s/msg)
Partition 1: [msg4] [msg5] [msg6] ← Consumer B (rápido, 100ms/msg)
Partition 2: [msg7] [msg8] [msg9] ← Consumer C (rápido, 100ms/msg)

→ Mensagens na partition 0 ficam travadas!
```

**Solução:**

```java
// Paralelismo dentro do consumer
@KafkaListener(
    topics = "payment.approved.v1",
    concurrency = "3"  // ← 3 threads processando em paralelo
)
public void handlePaymentApproved(PaymentApprovedEvent event) {
    // Processa em paralelo
}
```

---

### 15.5. Ordem de Eventos Incorreta

```
PROBLEMA: Eventos fora de ordem

Cliente envia:
1. CreateOrderEvent (orderId=123)
2. UpdateOrderEvent (orderId=123, status=PAID)
3. CancelOrderEvent (orderId=123)

Kafka entrega:
1. CreateOrderEvent ✅
2. CancelOrderEvent ❌ (chegou antes do Update!)
3. UpdateOrderEvent ❌

Resultado: Order fica PAID em vez de CANCELLED!
```

**Solução:**

```java
// Use MESMA partition key para garantir ordem
kafkaTemplate.send(
    "order.events.v1",
    order.getId().toString(),  // ← Partition key (orderId)
    event
);

// Todos eventos do MESMO orderId vão para a MESMA partition
// → Garantia de ordem
```

---

## 16. Quando Usar Coreografia {#quando-usar}

### 16.1. Use Coreografia Quando:

✅ **Alta disponibilidade é crítica**
- Sem single point of failure
- Se um serviço cair, outros continuam processando

✅ **Fluxo simples e linear**
- Order → Payment → Inventory → Confirmation
- Poucos passos, sem muitas regras

✅ **Serviços altamente desacoplados**
- Cada serviço opera independentemente
- Adicionar novo serviço é fácil (apenas subscribe ao evento)

✅ **Escalabilidade horizontal**
- Kafka escala melhor que HTTP
- Processamento assíncrono

✅ **Modelo event-driven já estabelecido**
- Time tem experiência com Kafka
- Infraestrutura de eventos já existe

---

### 16.2. NÃO Use Coreografia Quando:

❌ **Fluxo complexo com muitas regras de negócio**
- Lógica distribuída entre N serviços é difícil de entender
- Orquestração centraliza melhor

❌ **Precisa de visibilidade centralizada do fluxo**
- Difícil rastrear saga end-to-end em coreografia
- Orquestrador mostra estado completo

❌ **Time pequeno ou sem experiência com eventos**
- Coreografia aumenta complexidade
- Orquestração é mais fácil de debugar

❌ **Necessita de transações ACID**
- Coreografia é eventual consistency
- Se precisa de ACID, use monolito ou orquestração com 2PC

---

## 17. Checklist de Implementação {#checklist}

### Antes de Começar

- [ ] Identifiquei todos os eventos de domínio?
- [ ] Defini tópicos Kafka para cada evento?
- [ ] Defini schema de cada evento (com versionamento)?
- [ ] Mapeei fluxo de compensação para cada falha possível?
- [ ] Tenho infraestrutura Kafka (cluster, Zookeeper)?

### Durante Implementação

- [ ] Cada evento tem ID único (eventId)?
- [ ] Eventos carregam correlationId para tracing?
- [ ] Implementei idempotência (processed_events table)?
- [ ] Configurei Dead Letter Queue (DLQ)?
- [ ] Configurei retry com backoff exponencial?
- [ ] Eventos de compensação estão definidos?
- [ ] Implementei monitoramento (métricas, logs)?
- [ ] Configurei distributed tracing (Sleuth/Zipkin)?
- [ ] Defini estratégia de versionamento de eventos?
- [ ] Implementei Outbox Pattern (ou CDC)?
- [ ] Configurei retenção de tópicos Kafka?
- [ ] Defini número de partitions adequado?
- [ ] Criei testes com EmbeddedKafka?
- [ ] Testei cenários de falha e compensação?

### Após Deploy

- [ ] Monitorei lag dos consumers?
- [ ] Monitorei taxa de erro dos consumers?
- [ ] Configurei alertas para mensagens em DLQ?
- [ ] Documentei fluxo de eventos (diagramas)?
- [ ] Criei runbook para troubleshooting?
- [ ] Treinei time em análise de eventos Kafka?

---

## 18. Exercícios Práticos {#exercícios-práticos}

### Exercício 1: Implementar Fluxo Completo

**Cenário:** Implementar saga completa de criação de pedido.

**Tarefas:**
1. Crie Order Service que publica OrderCreatedEvent
2. Crie Payment Service que escuta OrderCreatedEvent e publica PaymentApprovedEvent
3. Crie Inventory Service que escuta PaymentApprovedEvent e publica InventoryReservedEvent
4. Order Service escuta InventoryReservedEvent e confirma order

**Solução:** Veja seções 5.2, 5.3 e 5.4.

---

### Exercício 2: Implementar Compensação

**Cenário:** Inventory falha (sem estoque). Implemente compensação.

**Tarefas:**
1. Inventory Service publica InventoryReservationFailedEvent
2. Payment Service escuta e reembolsa payment
3. Order Service escuta PaymentRefundedEvent e cancela order

**Solução:**

```java
// Inventory Service
@KafkaListener(topics = "payment.approved.v1")
public void handlePaymentApproved(PaymentApprovedEvent event) {
    try {
        reserveInventory(event);
        kafkaTemplate.send("inventory.reserved.v1", ...);
    } catch (OutOfStockException e) {
        // Publica evento de falha
        kafkaTemplate.send(
            "inventory.reservation-failed.v1",
            new InventoryReservationFailedEvent(event.orderId(), e.getMessage())
        );
    }
}

// Payment Service (compensação)
@KafkaListener(topics = "inventory.reservation-failed.v1")
public void handleInventoryFailed(InventoryReservationFailedEvent event) {
    Payment payment = paymentRepository.findByOrderId(event.orderId()).orElseThrow();
    paymentGateway.refund(payment.getTransactionId());
    payment.refund();
    paymentRepository.save(payment);

    kafkaTemplate.send("payment.refunded.v1", new PaymentRefundedEvent(payment));
}

// Order Service (compensação)
@KafkaListener(topics = "payment.refunded.v1")
public void handlePaymentRefunded(PaymentRefundedEvent event) {
    Order order = orderRepository.findById(event.orderId()).orElseThrow();
    order.cancel();
    orderRepository.save(order);

    kafkaTemplate.send("order.cancelled.v1", new OrderCancelledEvent(order));
}
```

---

### Exercício 3: Implementar Idempotência

**Cenário:** Consumer recebe evento duplicado. Garanta que processa apenas 1x.

**Solução:**

```java
@Entity
public class ProcessedEvent {
    @Id
    private UUID eventId;
    private LocalDateTime processedAt;
}

@KafkaListener(topics = "payment.approved.v1")
@Transactional
public void handlePaymentApproved(PaymentApprovedEvent event) {
    // Verifica se já processou
    if (processedEventRepository.exists(event.eventId())) {
        log.warn("Event {} already processed. Skipping.", event.eventId());
        return;
    }

    // Processa
    reserveInventory(event);

    // Marca como processado (mesma transação)
    processedEventRepository.save(new ProcessedEvent(event.eventId()));
}
```

---

## Conclusão

**Saga Pattern com Coreografia** é uma solução poderosa para gerenciar transações distribuídas em arquiteturas event-driven. O padrão oferece:

✅ **Desacoplamento** - Serviços se comunicam via eventos
✅ **Escalabilidade** - Kafka escala horizontalmente
✅ **Alta disponibilidade** - Sem single point of failure
✅ **Resiliência** - Retry automático, DLQ
✅ **Eventual consistency** - Sistema sempre volta ao estado consistente

**Pontos-chave para lembrar:**

1. **Idempotência é OBRIGATÓRIA** - processed_events table
2. **Compensações distribuídas** - Cada serviço compensa sua transação
3. **Dead Letter Queue** - Para mensagens que falharam repetidamente
4. **Event versioning** - Planeje evolução de schemas
5. **Outbox Pattern** - Evite dual-write problem
6. **Monitoramento** - Trace distribuído é essencial

**Quando usar:**
- Alta disponibilidade é crítica
- Fluxos simples e lineares
- Modelo event-driven estabelecido
- Escalabilidade horizontal

**Quando NÃO usar:**
- Fluxos complexos com muitas regras
- Precisa de visibilidade centralizada
- Time sem experiência com eventos
- Necessita de transações ACID

Com este conhecimento, você está preparado para implementar Saga Pattern com Coreografia de forma robusta e resiliente usando Spring Boot e Kafka! 🚀