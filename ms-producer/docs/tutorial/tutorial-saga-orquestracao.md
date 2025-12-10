# Tutorial Definitivo: Saga Pattern - Orquestração

## 📚 Sumário

1. [Definição em 30 Segundos](#definição-em-30-segundos)
2. [O Problema das Transações Distribuídas](#o-problema)
3. [O que é Saga Pattern](#o-que-é-saga)
4. [Orquestração vs Coreografia](#orquestração-vs-coreografia)
5. [Como Funciona a Orquestração](#como-funciona)
6. [State Machine (Máquina de Estados)](#state-machine)
7. [Implementação Passo a Passo](#implementação-passo-a-passo)
8. [Compensações (Rollback Distribuído)](#compensações)
9. [Gerenciamento de Estado](#gerenciamento-de-estado)
10. [Retry e Timeouts](#retry-e-timeouts)
11. [Idempotência](#idempotência)
12. [Isolamento e Leituras Sujas](#isolamento)
13. [Monitoramento e Observabilidade](#monitoramento)
14. [Implementação Completa com Spring Boot](#implementação-completa)
15. [Testes](#testes)
16. [Armadilhas Comuns](#armadilhas)
17. [Quando Usar Orquestração](#quando-usar)
18. [Checklist de Implementação](#checklist)
19. [Exercícios Práticos](#exercícios-práticos)

---

## Definição em 30 Segundos

**Saga Pattern com Orquestração** é um padrão para gerenciar transações distribuídas em microserviços usando um **orquestrador centralizado** (Saga Orchestrator) que coordena a execução de todas as etapas da transação. O orquestrador decide qual serviço chamar, em qual ordem, e executa **compensações** (rollback) se algo falhar.

**Princípio-Chave:** Um maestro (orquestrador) coordena todos os músicos (microserviços).

```
Orquestrador → "Order Service, crie o pedido"
Orquestrador → "Payment Service, processe o pagamento"
Orquestrador → "Inventory Service, reserve os itens"
SE FALHAR: Orquestrador executa compensações na ordem reversa
```

---

## 1. O Problema das Transações Distribuídas {#o-problema}

### 1.1. Transações ACID no Monolito

Em um monolito, transações são **atômicas**:

```java
// ❌ MONOLITO: Transação ACID (Atomicity, Consistency, Isolation, Durability)
@Transactional
public void createOrder(OrderRequest request) {
    // Tudo acontece na MESMA transação de banco
    Order order = orderRepository.save(new Order(...));
    Payment payment = paymentRepository.save(new Payment(...));
    Inventory inventory = inventoryRepository.reserve(order.getItems());

    // Se QUALQUER operação falhar → TUDO é revertido automaticamente
}
```

**Diagrama:**

```
MONOLITO (Banco Único):
┌──────────────────────────────────────┐
│         BEGIN TRANSACTION            │
├──────────────────────────────────────┤
│  1. INSERT INTO orders ...           │ ✅
│  2. INSERT INTO payments ...         │ ✅
│  3. UPDATE inventory SET qty=qty-1   │ ❌ ERRO!
├──────────────────────────────────────┤
│         ROLLBACK                     │ ← Tudo é desfeito
└──────────────────────────────────────┘
```

---

### 1.2. O Problema em Microserviços

Em microserviços, cada serviço tem seu **próprio banco de dados**:

```
MICROSERVIÇOS (Bancos Separados):
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   Order     │  │  Payment    │  │  Inventory  │
│  Service    │  │  Service    │  │  Service    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       ↓                ↓                ↓
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Order DB   │  │ Payment DB  │  │ Inventory DB│
└─────────────┘  └─────────────┘  └─────────────┘

❌ PROBLEMA: Não existe transação distribuída!
```

**Cenário de falha:**

```java
// ❌ IMPOSSÍVEL: @Transactional não funciona entre serviços!
@Transactional
public void createOrder(OrderRequest request) {
    // 1. Chama Order Service → ✅ Sucesso (criou order)
    orderServiceClient.createOrder(request);

    // 2. Chama Payment Service → ✅ Sucesso (cobrou cartão)
    paymentServiceClient.createPayment(request);

    // 3. Chama Inventory Service → ❌ FALHA (sem estoque)
    inventoryServiceClient.reserveItems(request);

    // 💥 PROBLEMA:
    //    - Order foi criado
    //    - Payment foi processado ($ cobrado do cliente!)
    //    - Inventory falhou
    //    → Sistema em estado INCONSISTENTE!
}
```

**Resultado:**

```
Cliente foi COBRADO mas não recebeu o produto!

┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Order DB   │  │ Payment DB  │  │ Inventory DB│
│             │  │             │  │             │
│ Order #123  │  │ Payment     │  │ (vazio)     │
│ CREATED ✅  │  │ APPROVED ✅ │  │ FAILED ❌   │
└─────────────┘  └─────────────┘  └─────────────┘
        ↑                ↑                ↑
   Inconsistência!
```

---

### 1.3. Soluções Possíveis (E Suas Limitações)

#### Solução 1: Two-Phase Commit (2PC)

```
❌ Two-Phase Commit:
Coordenador → "Todos preparem a transação"
Order Service → "Preparado"
Payment Service → "Preparado"
Inventory Service → "Preparado"
Coordenador → "Todos committem agora!"

PROBLEMAS:
- Bloqueante (locks distribuídos)
- Latência alta
- Single point of failure (coordenador)
- Não funciona bem em microserviços
```

#### Solução 2: Saga Pattern ✅

```
✅ Saga Pattern:
- Sequência de transações LOCAIS
- Cada serviço tem sua transação
- Compensações se algo falhar
- Eventual consistency (não ACID)
```

---

## 2. O que é Saga Pattern {#o-que-é-saga}

**Saga** é um padrão que divide uma transação distribuída em uma **sequência de transações locais**. Cada transação local atualiza o banco de dados de um único serviço e publica um evento ou envia um comando para o próximo passo.

### 2.1. Tipos de Saga

**1. Orquestração (Orchestration)** ← Foco deste tutorial
- Orquestrador centralizado coordena tudo
- Serviços não sabem da saga
- Orquestrador chama serviços via HTTP/gRPC

**2. Coreografia (Choreography)**
- Sem coordenador central
- Serviços reagem a eventos
- Comunicação via message broker (Kafka)

---

## 3. Orquestração vs Coreografia {#orquestração-vs-coreografia}

### 3.1. Comparação Visual

```
ORQUESTRAÇÃO (Maestro coordena):
                  ┌──────────────────┐
                  │   ORCHESTRATOR   │ ← Controle centralizado
                  └────────┬─────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ↓ (1)              ↓ (2)              ↓ (3)
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  Order   │      │ Payment  │      │Inventory │
  │ Service  │      │ Service  │      │ Service  │
  └──────────┘      └──────────┘      └──────────┘

Orquestrador decide:
- Ordem de execução
- O que fazer se falhar
- Quando compensar


COREOGRAFIA (Cada um reage a eventos):
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  Order   │──┐   │ Payment  │──┐   │Inventory │
  │ Service  │  │   │ Service  │  │   │ Service  │
  └──────────┘  │   └──────────┘  │   └──────────┘
                │                 │
        (publica evento)  (publica evento)
                │                 │
                ↓                 ↓
         OrderCreatedEvent  PaymentApprovedEvent

Cada serviço decide:
- Quando reagir a um evento
- O que fazer se falhar
```

### 3.2. Comparação Detalhada

| Aspecto | Orquestração | Coreografia |
|---------|-------------|-------------|
| **Controle** | Centralizado (Orchestrator) | Descentralizado (eventos) |
| **Acoplamento** | Serviços desacoplados | Acoplamento via eventos |
| **Complexidade** | Orquestrador pode ser complexo | Lógica distribuída entre serviços |
| **Visibilidade** | Fácil ver fluxo completo | Difícil rastrear fluxo |
| **Falhas** | Orquestrador gerencia | Cada serviço gerencia |
| **Melhor para** | Fluxos complexos com muitas regras | Fluxos simples com poucos passos |
| **Single Point of Failure** | Sim (orquestrador) | Não |

---

## 4. Como Funciona a Orquestração {#como-funciona}

### 4.1. Fluxo Completo (Sucesso)

```
SAGA: Criar Order + Payment + Inventory

    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │                                                         │
    │  State: START                                           │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (1) CreateOrder
                            ↓
                    ┌───────────────┐
                    │ Order Service │
                    │ create()      │
                    └───────┬───────┘
                            │ OrderCreated ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: ORDER_CREATED                                   │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (2) ProcessPayment
                            ↓
                    ┌───────────────┐
                    │Payment Service│
                    │ process()     │
                    └───────┬───────┘
                            │ PaymentProcessed ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: PAYMENT_PROCESSED                               │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (3) ReserveInventory
                            ↓
                    ┌───────────────┐
                    │Inventory Svc  │
                    │ reserve()     │
                    └───────┬───────┘
                            │ InventoryReserved ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: COMPLETED ✅                                    │
    └─────────────────────────────────────────────────────────┘
```

---

### 4.2. Fluxo com Falha e Compensação

```
SAGA: Criar Order + Payment + Inventory (FALHA no Inventory)

    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: START                                           │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (1) CreateOrder
                            ↓
                    ┌───────────────┐
                    │ Order Service │
                    │ create()      │
                    └───────┬───────┘
                            │ OrderCreated ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: ORDER_CREATED                                   │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (2) ProcessPayment
                            ↓
                    ┌───────────────┐
                    │Payment Service│
                    │ process()     │
                    └───────┬───────┘
                            │ PaymentProcessed ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: PAYMENT_PROCESSED                               │
    └───────────────────────┬─────────────────────────────────┘
                            │
                    (3) ReserveInventory
                            ↓
                    ┌───────────────┐
                    │Inventory Svc  │
                    │ reserve()     │
                    └───────┬───────┘
                            │ ReservationFailed ❌ (sem estoque)
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: COMPENSATING                                    │
    │  ATENÇÃO: Precisa desfazer tudo!                        │
    └───────────────────────┬─────────────────────────────────┘
                            │
                 (COMPENSAÇÃO) CancelPayment
                            ↓
                    ┌───────────────┐
                    │Payment Service│
                    │ refund()      │
                    └───────┬───────┘
                            │ PaymentRefunded ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: COMPENSATING                                    │
    └───────────────────────┬─────────────────────────────────┘
                            │
                 (COMPENSAÇÃO) CancelOrder
                            ↓
                    ┌───────────────┐
                    │ Order Service │
                    │ cancel()      │
                    └───────┬───────┘
                            │ OrderCancelled ✅
                            ↓
    ┌─────────────────────────────────────────────────────────┐
    │              SAGA ORCHESTRATOR                          │
    │  State: FAILED (compensated) ❌                         │
    └─────────────────────────────────────────────────────────┘
```

**Resultado:** Sistema volta ao estado consistente (order cancelado, payment reembolsado).

---

## 5. State Machine (Máquina de Estados) {#state-machine}

O orquestrador usa uma **State Machine** para rastrear o estado da saga.

### 5.1. Estados da Saga

```java
public enum SagaState {
    STARTED,              // Saga iniciada
    ORDER_CREATED,        // Order criado
    PAYMENT_PROCESSED,    // Payment processado
    INVENTORY_RESERVED,   // Inventory reservado
    COMPLETED,            // ✅ Sucesso total
    COMPENSATING,         // ⚠️ Executando compensações
    FAILED                // ❌ Falhou (já compensado)
}
```

### 5.2. Transições de Estado

```
STATE MACHINE:

    [START]
       │
       ↓ createOrder()
  [ORDER_CREATED]
       │
       ↓ processPayment()
  [PAYMENT_PROCESSED]
       │
       ↓ reserveInventory()
  [INVENTORY_RESERVED]
       │
       ↓
   [COMPLETED] ✅

SE FALHAR EM QUALQUER PASSO:
       │
       ↓ error
  [COMPENSATING]
       │
       ↓ execute compensations (reverse order)
    [FAILED] ❌
```

### 5.3. Tabela de Transições

| Estado Atual | Evento | Próximo Estado | Ação |
|--------------|--------|----------------|------|
| STARTED | OrderCreated | ORDER_CREATED | Processar payment |
| ORDER_CREATED | PaymentProcessed | PAYMENT_PROCESSED | Reservar inventory |
| PAYMENT_PROCESSED | InventoryReserved | INVENTORY_RESERVED | Completar saga |
| INVENTORY_RESERVED | - | COMPLETED | - |
| **Qualquer** | **Error** | **COMPENSATING** | **Executar compensações** |
| COMPENSATING | AllCompensated | FAILED | - |

---

## 6. Implementação Passo a Passo {#implementação-passo-a-passo}

### 6.1. Arquitetura da Solução

```
COMPONENTES:

1. Saga Orchestrator Service (novo serviço)
   - Gerencia estado da saga
   - Chama outros serviços
   - Executa compensações

2. Order Service
   - createOrder()
   - cancelOrder() (compensação)

3. Payment Service
   - processPayment()
   - refundPayment() (compensação)

4. Inventory Service
   - reserveItems()
   - releaseItems() (compensação)


DIAGRAMA:
         ┌─────────────────────────────────┐
         │   Saga Orchestrator Service     │
         │   - SagaOrchestrator            │
         │   - SagaStateMachine            │
         │   - SagaRepository              │
         └───────────┬─────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ↓            ↓            ↓
  ┌──────────┐ ┌──────────┐ ┌──────────┐
  │  Order   │ │ Payment  │ │Inventory │
  │ Service  │ │ Service  │ │ Service  │
  └──────────┘ └──────────┘ └──────────┘
```

---

### 6.2. Modelo de Domínio

#### Saga Entity

```java
// ===== SAGA ENTITY =====
@Entity
@Table(name = "sagas")
public class Saga {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private SagaType type; // CREATE_ORDER

    @Enumerated(EnumType.STRING)
    private SagaState state;

    @Convert(converter = JsonConverter.class)
    private Map<String, Object> payload; // Dados da transação

    @Convert(converter = JsonConverter.class)
    private List<SagaStep> executedSteps; // Passos já executados

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== MÉTODOS DE NEGÓCIO =====

    public static Saga start(SagaType type, Map<String, Object> payload) {
        Saga saga = new Saga();
        saga.id = UUID.randomUUID();
        saga.type = type;
        saga.state = SagaState.STARTED;
        saga.payload = payload;
        saga.executedSteps = new ArrayList<>();
        saga.createdAt = LocalDateTime.now();
        saga.updatedAt = LocalDateTime.now();
        return saga;
    }

    public void recordStep(SagaStep step) {
        this.executedSteps.add(step);
        this.updatedAt = LocalDateTime.now();
    }

    public void transitionTo(SagaState newState) {
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
    }

    public List<SagaStep> getCompensationSteps() {
        // Retorna passos em ordem REVERSA para compensação
        List<SagaStep> steps = new ArrayList<>(executedSteps);
        Collections.reverse(steps);
        return steps;
    }

    // Getters/Setters
}
```

#### SagaStep (Value Object)

```java
public record SagaStep(
    String stepName,
    SagaStepType type,        // COMMAND ou COMPENSATION
    SagaStepStatus status,    // PENDING, COMPLETED, FAILED
    LocalDateTime executedAt,
    String errorMessage
) {
    public static SagaStep command(String stepName) {
        return new SagaStep(
            stepName,
            SagaStepType.COMMAND,
            SagaStepStatus.PENDING,
            null,
            null
        );
    }

    public SagaStep markCompleted() {
        return new SagaStep(
            stepName,
            type,
            SagaStepStatus.COMPLETED,
            LocalDateTime.now(),
            null
        );
    }

    public SagaStep markFailed(String error) {
        return new SagaStep(
            stepName,
            type,
            SagaStepStatus.FAILED,
            LocalDateTime.now(),
            error
        );
    }
}

public enum SagaStepType {
    COMMAND,       // Ação normal (criar, processar, reservar)
    COMPENSATION   // Compensação (cancelar, reembolsar, liberar)
}

public enum SagaStepStatus {
    PENDING,
    COMPLETED,
    FAILED
}
```

#### Enums

```java
public enum SagaType {
    CREATE_ORDER
}

public enum SagaState {
    STARTED,
    ORDER_CREATED,
    PAYMENT_PROCESSED,
    INVENTORY_RESERVED,
    COMPLETED,
    COMPENSATING,
    FAILED
}
```

---

### 6.3. Orquestrador (Saga Orchestrator)

```java
// ===== SAGA ORCHESTRATOR =====
@Service
@Slf4j
public class OrderSagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final OrderServiceClient orderClient;
    private final PaymentServiceClient paymentClient;
    private final InventoryServiceClient inventoryClient;

    // ===== INICIA SAGA =====
    @Transactional
    public Saga startCreateOrderSaga(CreateOrderSagaRequest request) {
        log.info("Starting CREATE_ORDER saga for userId={}", request.userId());

        // 1. Cria saga
        Map<String, Object> payload = Map.of(
            "userId", request.userId(),
            "items", request.items(),
            "amount", request.amount()
        );

        Saga saga = Saga.start(SagaType.CREATE_ORDER, payload);
        sagaRepository.save(saga);

        // 2. Executa passo 1: Criar Order
        executeStep1_CreateOrder(saga);

        return saga;
    }

    // ===== PASSO 1: CRIAR ORDER =====
    private void executeStep1_CreateOrder(Saga saga) {
        log.info("Saga {}: Executing step 1 - Create Order", saga.getId());

        SagaStep step = SagaStep.command("CreateOrder");
        saga.recordStep(step);

        try {
            // Chama Order Service
            CreateOrderRequest request = new CreateOrderRequest(
                UUID.fromString(saga.getPayload().get("userId").toString()),
                (BigDecimal) saga.getPayload().get("amount")
            );

            OrderResponse order = orderClient.createOrder(request);

            // ✅ Sucesso
            saga.recordStep(step.markCompleted());
            saga.transitionTo(SagaState.ORDER_CREATED);

            // Armazena orderId no payload para usar depois
            saga.getPayload().put("orderId", order.orderId());
            sagaRepository.save(saga);

            log.info("Saga {}: Order created successfully. OrderId={}",
                saga.getId(), order.orderId());

            // Próximo passo
            executeStep2_ProcessPayment(saga);

        } catch (Exception e) {
            log.error("Saga {}: Failed to create order", saga.getId(), e);
            saga.recordStep(step.markFailed(e.getMessage()));
            sagaRepository.save(saga);

            // ❌ Falhou no primeiro passo → Não precisa compensar
            saga.transitionTo(SagaState.FAILED);
            sagaRepository.save(saga);
        }
    }

    // ===== PASSO 2: PROCESSAR PAYMENT =====
    private void executeStep2_ProcessPayment(Saga saga) {
        log.info("Saga {}: Executing step 2 - Process Payment", saga.getId());

        SagaStep step = SagaStep.command("ProcessPayment");
        saga.recordStep(step);

        try {
            UUID orderId = UUID.fromString(saga.getPayload().get("orderId").toString());
            BigDecimal amount = (BigDecimal) saga.getPayload().get("amount");

            ProcessPaymentRequest request = new ProcessPaymentRequest(
                orderId,
                amount
            );

            PaymentResponse payment = paymentClient.processPayment(request);

            // ✅ Sucesso
            saga.recordStep(step.markCompleted());
            saga.transitionTo(SagaState.PAYMENT_PROCESSED);
            saga.getPayload().put("paymentId", payment.paymentId());
            sagaRepository.save(saga);

            log.info("Saga {}: Payment processed successfully. PaymentId={}",
                saga.getId(), payment.paymentId());

            // Próximo passo
            executeStep3_ReserveInventory(saga);

        } catch (Exception e) {
            log.error("Saga {}: Failed to process payment", saga.getId(), e);
            saga.recordStep(step.markFailed(e.getMessage()));
            sagaRepository.save(saga);

            // ❌ Falhou → Executar compensações
            compensate(saga);
        }
    }

    // ===== PASSO 3: RESERVAR INVENTORY =====
    private void executeStep3_ReserveInventory(Saga saga) {
        log.info("Saga {}: Executing step 3 - Reserve Inventory", saga.getId());

        SagaStep step = SagaStep.command("ReserveInventory");
        saga.recordStep(step);

        try {
            UUID orderId = UUID.fromString(saga.getPayload().get("orderId").toString());
            List<OrderItem> items = (List<OrderItem>) saga.getPayload().get("items");

            ReserveInventoryRequest request = new ReserveInventoryRequest(
                orderId,
                items
            );

            InventoryResponse inventory = inventoryClient.reserveItems(request);

            // ✅ Sucesso
            saga.recordStep(step.markCompleted());
            saga.transitionTo(SagaState.INVENTORY_RESERVED);
            saga.getPayload().put("reservationId", inventory.reservationId());
            sagaRepository.save(saga);

            log.info("Saga {}: Inventory reserved successfully", saga.getId());

            // ✅ SAGA COMPLETA!
            completeSaga(saga);

        } catch (OutOfStockException e) {
            log.error("Saga {}: Failed to reserve inventory (out of stock)", saga.getId(), e);
            saga.recordStep(step.markFailed(e.getMessage()));
            sagaRepository.save(saga);

            // ❌ Falhou → Executar compensações
            compensate(saga);
        }
    }

    // ===== COMPLETA SAGA =====
    private void completeSaga(Saga saga) {
        log.info("Saga {}: COMPLETED successfully ✅", saga.getId());
        saga.transitionTo(SagaState.COMPLETED);
        sagaRepository.save(saga);
    }

    // ===== COMPENSAÇÃO =====
    private void compensate(Saga saga) {
        log.warn("Saga {}: Starting compensation (rollback)", saga.getId());
        saga.transitionTo(SagaState.COMPENSATING);
        sagaRepository.save(saga);

        // Obtém passos executados em ordem REVERSA
        List<SagaStep> stepsToCompensate = saga.getCompensationSteps();

        for (SagaStep completedStep : stepsToCompensate) {
            if (completedStep.status() != SagaStepStatus.COMPLETED) {
                continue; // Só compensa passos que foram completados
            }

            switch (completedStep.stepName()) {
                case "CreateOrder" -> compensateCreateOrder(saga);
                case "ProcessPayment" -> compensateProcessPayment(saga);
                case "ReserveInventory" -> compensateReserveInventory(saga);
            }
        }

        // Marca saga como FAILED (já compensada)
        saga.transitionTo(SagaState.FAILED);
        sagaRepository.save(saga);

        log.warn("Saga {}: FAILED (compensated) ❌", saga.getId());
    }

    // ===== COMPENSAÇÃO: CANCELAR ORDER =====
    private void compensateCreateOrder(Saga saga) {
        log.info("Saga {}: Compensating CreateOrder (cancelling order)", saga.getId());

        try {
            UUID orderId = UUID.fromString(saga.getPayload().get("orderId").toString());
            orderClient.cancelOrder(orderId);

            log.info("Saga {}: Order cancelled successfully", saga.getId());
        } catch (Exception e) {
            log.error("Saga {}: Failed to cancel order (compensation failed!)",
                saga.getId(), e);
            // ⚠️ PROBLEMA: Compensação falhou! Precisa de intervenção manual
        }
    }

    // ===== COMPENSAÇÃO: REEMBOLSAR PAYMENT =====
    private void compensateProcessPayment(Saga saga) {
        log.info("Saga {}: Compensating ProcessPayment (refunding)", saga.getId());

        try {
            UUID paymentId = UUID.fromString(saga.getPayload().get("paymentId").toString());
            paymentClient.refundPayment(paymentId);

            log.info("Saga {}: Payment refunded successfully", saga.getId());
        } catch (Exception e) {
            log.error("Saga {}: Failed to refund payment (compensation failed!)",
                saga.getId(), e);
            // ⚠️ PROBLEMA: Compensação falhou! Precisa de intervenção manual
        }
    }

    // ===== COMPENSAÇÃO: LIBERAR INVENTORY =====
    private void compensateReserveInventory(Saga saga) {
        log.info("Saga {}: Compensating ReserveInventory (releasing items)", saga.getId());

        try {
            UUID reservationId = UUID.fromString(
                saga.getPayload().get("reservationId").toString()
            );
            inventoryClient.releaseReservation(reservationId);

            log.info("Saga {}: Inventory released successfully", saga.getId());
        } catch (Exception e) {
            log.error("Saga {}: Failed to release inventory (compensation failed!)",
                saga.getId(), e);
            // ⚠️ PROBLEMA: Compensação falhou! Precisa de intervenção manual
        }
    }
}
```

---

### 6.4. Clientes HTTP (Feign)

```java
// ===== ORDER SERVICE CLIENT =====
@FeignClient(name = "order-service", url = "${order.service.url}")
public interface OrderServiceClient {

    @PostMapping("/api/orders")
    OrderResponse createOrder(@RequestBody CreateOrderRequest request);

    @DeleteMapping("/api/orders/{orderId}")
    void cancelOrder(@PathVariable UUID orderId);
}

// ===== PAYMENT SERVICE CLIENT =====
@FeignClient(name = "payment-service", url = "${payment.service.url}")
public interface PaymentServiceClient {

    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody ProcessPaymentRequest request);

    @PostMapping("/api/payments/{paymentId}/refund")
    void refundPayment(@PathVariable UUID paymentId);
}

// ===== INVENTORY SERVICE CLIENT =====
@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryServiceClient {

    @PostMapping("/api/inventory/reserve")
    InventoryResponse reserveItems(@RequestBody ReserveInventoryRequest request);

    @DeleteMapping("/api/inventory/reservations/{reservationId}")
    void releaseReservation(@PathVariable UUID reservationId);
}
```

---

### 6.5. Controller (Saga Orchestrator Service)

```java
// ===== SAGA CONTROLLER =====
@RestController
@RequestMapping("/api/sagas")
@Slf4j
public class SagaController {

    private final OrderSagaOrchestrator orchestrator;
    private final SagaRepository sagaRepository;

    @PostMapping("/create-order")
    public ResponseEntity<SagaResponse> createOrder(
        @RequestBody CreateOrderSagaRequest request
    ) {
        log.info("Received create order saga request: {}", request);

        Saga saga = orchestrator.startCreateOrderSaga(request);

        return ResponseEntity.ok(new SagaResponse(
            saga.getId(),
            saga.getType(),
            saga.getState()
        ));
    }

    @GetMapping("/{sagaId}")
    public ResponseEntity<SagaDetailResponse> getSagaDetails(
        @PathVariable UUID sagaId
    ) {
        Saga saga = sagaRepository.findById(sagaId)
            .orElseThrow(() -> new SagaNotFoundException(sagaId));

        return ResponseEntity.ok(new SagaDetailResponse(
            saga.getId(),
            saga.getType(),
            saga.getState(),
            saga.getExecutedSteps(),
            saga.getCreatedAt(),
            saga.getUpdatedAt()
        ));
    }
}
```

---

## 7. Compensações (Rollback Distribuído) {#compensações}

### 7.1. O que são Compensações

**Compensação** é uma transação que **desfaz** o efeito de uma transação anterior.

```
EXEMPLO:
Transação:    createOrder(id=123, amount=100)
Compensação:  cancelOrder(id=123)
```

### 7.2. Tipos de Compensações

#### 1. Compensação Semântica

```java
// Transação: Reservar item
inventoryService.reserve(productId, quantity);

// ❌ NÃO PODE: Simplesmente deletar do banco (histórico perdido)
// DELETE FROM reservations WHERE id = ?

// ✅ CORRETO: Compensação semântica (libera mas mantém histórico)
inventoryService.release(reservationId);

// Implementação:
@Transactional
public void release(UUID reservationId) {
    Reservation reservation = reservationRepository.findById(reservationId)
        .orElseThrow();

    reservation.setStatus(ReservationStatus.RELEASED);
    reservation.setReleasedAt(LocalDateTime.now());

    reservationRepository.save(reservation);
}
```

#### 2. Compensação com Estado Anterior

```java
// Transação: Debitar saldo
accountService.debit(accountId, 100.00);

// Compensação: Creditar de volta
accountService.credit(accountId, 100.00);
```

#### 3. Compensação Impossível (Ponto de Não Retorno)

```java
// ⚠️ ATENÇÃO: Algumas ações NÃO podem ser desfeitas

// Exemplo 1: Envio de e-mail
emailService.send(user.getEmail(), "Order confirmed");
// ❌ Não pode "desfazer" (e-mail já foi enviado)

// Exemplo 2: Cobrança em cartão processada
paymentGateway.charge(creditCard, 100.00);
// ⚠️ Pode fazer REFUND, mas não é instantâneo

// SOLUÇÃO: Coloque ações irreversíveis no FINAL da saga
```

### 7.3. Ordem de Compensação

**REGRA:** Compensações devem ser executadas na **ordem REVERSA** da execução.

```
EXECUÇÃO:
1. CreateOrder
2. ProcessPayment
3. ReserveInventory (FALHA)

COMPENSAÇÃO (ordem reversa):
2. RefundPayment      ← Desfaz ProcessPayment
1. CancelOrder        ← Desfaz CreateOrder
```

**Por quê?** Para manter consistência:

```
❌ ERRADO: Compensar na ordem normal
1. CancelOrder
2. RefundPayment
   ↑ PROBLEMA: Se RefundPayment falhar, order está cancelado mas payment não foi reembolsado!

✅ CORRETO: Ordem reversa
2. RefundPayment     ← Se falhar aqui, order ainda existe (pode tentar refund manualmente)
1. CancelOrder       ← Só cancela se refund deu certo
```

---

### 7.4. Tratando Falhas em Compensações

**PROBLEMA:** E se a compensação falhar?

```java
private void compensateProcessPayment(Saga saga) {
    try {
        paymentClient.refundPayment(paymentId);
        log.info("Payment refunded successfully");
    } catch (Exception e) {
        // ❌ COMPENSAÇÃO FALHOU!
        log.error("Failed to refund payment!", e);

        // O que fazer agora?
    }
}
```

#### Solução 1: Retry Automático

```java
@Retryable(
    value = {PaymentServiceException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
private void compensateProcessPayment(Saga saga) {
    paymentClient.refundPayment(paymentId);
}
```

#### Solução 2: Dead Letter Queue (DLQ)

```java
private void compensateProcessPayment(Saga saga) {
    try {
        paymentClient.refundPayment(paymentId);
    } catch (Exception e) {
        // Envia para DLQ para intervenção manual
        deadLetterQueue.send(new FailedCompensationMessage(
            saga.getId(),
            "RefundPayment",
            paymentId,
            e.getMessage()
        ));

        // Notifica time de ops
        alertService.sendAlert(
            "Saga compensation failed!",
            "SagaId: " + saga.getId() + ", Error: " + e.getMessage()
        );
    }
}
```

#### Solução 3: Saga State = COMPENSATION_FAILED

```java
public enum SagaState {
    // ...
    COMPENSATION_FAILED  // Estado especial: Saga falhou E compensação falhou
}

private void compensate(Saga saga) {
    saga.transitionTo(SagaState.COMPENSATING);

    boolean allCompensationsSucceeded = true;

    for (SagaStep step : saga.getCompensationSteps()) {
        try {
            compensateStep(step);
        } catch (Exception e) {
            log.error("Compensation failed for step: {}", step.stepName(), e);
            allCompensationsSucceeded = false;
            break; // Para aqui
        }
    }

    if (allCompensationsSucceeded) {
        saga.transitionTo(SagaState.FAILED); // Falhou mas compensou
    } else {
        saga.transitionTo(SagaState.COMPENSATION_FAILED); // ⚠️ Problema sério!
    }

    sagaRepository.save(saga);
}
```

---

## 8. Gerenciamento de Estado {#gerenciamento-de-estado}

### 8.1. Persistência do Estado da Saga

**IMPORTANTE:** O estado da saga DEVE ser persistido em banco de dados.

```java
// ❌ ERRADO: Estado em memória
private Map<UUID, Saga> sagas = new HashMap<>(); // Perde dados se serviço reiniciar!

// ✅ CORRETO: Estado no banco
@Repository
public interface SagaRepository extends JpaRepository<Saga, UUID> {

    List<Saga> findByState(SagaState state);

    List<Saga> findByStateAndCreatedAtBefore(SagaState state, LocalDateTime before);
}
```

### 8.2. Schema do Banco

```sql
-- Tabela de Sagas
CREATE TABLE sagas (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,         -- Dados da transação
    executed_steps JSONB NOT NULL,  -- Histórico de passos
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sagas_state ON sagas(state);
CREATE INDEX idx_sagas_created_at ON sagas(created_at);

-- Exemplo de dados:
INSERT INTO sagas VALUES (
    '123e4567-e89b-12d3-a456-426614174000',
    'CREATE_ORDER',
    'PAYMENT_PROCESSED',
    '{"userId": "uuid", "orderId": "uuid", "amount": 100.50}',
    '[
        {"stepName": "CreateOrder", "status": "COMPLETED", "executedAt": "2025-12-09T10:00:00"},
        {"stepName": "ProcessPayment", "status": "COMPLETED", "executedAt": "2025-12-09T10:00:02"}
    ]',
    '2025-12-09 10:00:00',
    '2025-12-09 10:00:02'
);
```

---

### 8.3. Recovery de Sagas Pendentes

**PROBLEMA:** Se o orquestrador crashar no meio da saga, como recuperar?

```java
// ===== JOB DE RECOVERY =====
@Component
@Slf4j
public class SagaRecoveryJob {

    private final SagaRepository sagaRepository;
    private final OrderSagaOrchestrator orchestrator;

    @Scheduled(fixedDelay = 60000) // Roda a cada 1 minuto
    public void recoverPendingSagas() {
        log.info("Running saga recovery job");

        // 1. Busca sagas que estão "travadas" há mais de 5 minutos
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        List<Saga> stuckSagas = sagaRepository.findByStateAndCreatedAtBefore(
            SagaState.STARTED,
            fiveMinutesAgo
        );

        stuckSagas.addAll(sagaRepository.findByStateAndCreatedAtBefore(
            SagaState.ORDER_CREATED,
            fiveMinutesAgo
        ));

        stuckSagas.addAll(sagaRepository.findByStateAndCreatedAtBefore(
            SagaState.PAYMENT_PROCESSED,
            fiveMinutesAgo
        ));

        // 2. Para cada saga travada, tenta continuar ou compensar
        for (Saga saga : stuckSagas) {
            try {
                log.warn("Found stuck saga: {}, state={}", saga.getId(), saga.getState());
                orchestrator.resume(saga);
            } catch (Exception e) {
                log.error("Failed to resume saga: {}", saga.getId(), e);
            }
        }
    }
}

// ===== MÉTODO RESUME NO ORCHESTRATOR =====
@Transactional
public void resume(Saga saga) {
    log.info("Resuming saga: {}, current state: {}", saga.getId(), saga.getState());

    switch (saga.getState()) {
        case STARTED -> executeStep1_CreateOrder(saga);
        case ORDER_CREATED -> executeStep2_ProcessPayment(saga);
        case PAYMENT_PROCESSED -> executeStep3_ReserveInventory(saga);
        case COMPENSATING -> compensate(saga);
        default -> log.warn("Cannot resume saga in state: {}", saga.getState());
    }
}
```

---

## 9. Retry e Timeouts {#retry-e-timeouts}

### 9.1. Retry em Chamadas de Serviço

```java
// ===== CONFIGURAÇÃO FEIGN COM RETRY =====
@Configuration
public class FeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            1000,      // Intervalo inicial: 1s
            5000,      // Intervalo máximo: 5s
            3          // Máximo de tentativas: 3
        );
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }
}

public class CustomErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 500) {
            // Erro 5xx → Retry
            return new RetryableException(
                response.status(),
                "Server error",
                response.request().httpMethod(),
                null,
                response.request()
            );
        }

        if (response.status() == 404) {
            // 404 → Não faz retry
            return new ResourceNotFoundException();
        }

        return new Exception("Unknown error");
    }
}
```

### 9.2. Timeout em Chamadas

```java
// ===== CONFIGURAÇÃO DE TIMEOUT =====
@FeignClient(
    name = "payment-service",
    url = "${payment.service.url}",
    configuration = PaymentClientConfig.class
)
public interface PaymentServiceClient {
    // ...
}

@Configuration
public class PaymentClientConfig {

    @Bean
    public Request.Options options() {
        return new Request.Options(
            5000,   // connectTimeout: 5s
            10000   // readTimeout: 10s
        );
    }
}
```

### 9.3. Timeout da Saga Completa

```java
// ===== TIMEOUT TOTAL DA SAGA =====
@Entity
public class Saga {
    // ...

    private static final Duration SAGA_TIMEOUT = Duration.ofMinutes(5);

    public boolean isTimedOut() {
        return Duration.between(createdAt, LocalDateTime.now())
            .compareTo(SAGA_TIMEOUT) > 0;
    }
}

// ===== JOB PARA TIMEOUT =====
@Scheduled(fixedDelay = 60000)
public void timeoutSagas() {
    List<Saga> activeSagas = sagaRepository.findByStateIn(
        List.of(
            SagaState.STARTED,
            SagaState.ORDER_CREATED,
            SagaState.PAYMENT_PROCESSED
        )
    );

    for (Saga saga : activeSagas) {
        if (saga.isTimedOut()) {
            log.warn("Saga {} timed out after 5 minutes. Compensating...", saga.getId());
            orchestrator.compensate(saga);
        }
    }
}
```

---

## 10. Idempotência {#idempotência}

### 10.1. Por Que Precisamos de Idempotência

**PROBLEMA:** Retry pode causar duplicação.

```
CENÁRIO:
1. Orchestrator chama Payment Service
2. Payment Service processa pagamento
3. Payment Service responde "200 OK"
4. ❌ Resposta se perde na rede (timeout)
5. Orchestrator faz RETRY
6. 💥 Payment Service processa NOVAMENTE (cliente cobrado 2x!)
```

**SOLUÇÃO:** Idempotência.

```
CENÁRIO COM IDEMPOTÊNCIA:
1. Orchestrator chama Payment Service com idempotencyKey=saga123
2. Payment Service processa pagamento
3. Payment Service responde "200 OK"
4. ❌ Resposta se perde na rede (timeout)
5. Orchestrator faz RETRY com MESMO idempotencyKey=saga123
6. ✅ Payment Service detecta duplicata e retorna mesmo resultado (sem processar novamente)
```

---

### 10.2. Implementação de Idempotência

#### Payment Service (com idempotência)

```java
// ===== PAYMENT SERVICE =====
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    public PaymentResponse processPayment(
        ProcessPaymentRequest request,
        String idempotencyKey
    ) {
        // 1. Verifica se já processamos esta requisição
        Optional<IdempotencyRecord> existing = idempotencyRepository
            .findByKey(idempotencyKey);

        if (existing.isPresent()) {
            log.info("Duplicate request detected. IdempotencyKey={}. Returning cached response.",
                idempotencyKey);

            // ✅ Retorna resposta armazenada (sem processar novamente)
            return existing.get().getResponse();
        }

        // 2. Processa payment (primeira vez)
        Payment payment = Payment.create(
            PaymentId.generate(),
            OrderId.of(request.orderId()),
            Money.of(request.amount(), "BRL")
        );

        boolean approved = paymentGateway.charge(payment);

        if (approved) {
            payment.approve();
        } else {
            payment.reject();
        }

        paymentRepository.save(payment);

        PaymentResponse response = new PaymentResponse(
            payment.getId(),
            payment.getStatus()
        );

        // 3. Armazena resposta para futuros retries
        IdempotencyRecord record = new IdempotencyRecord(
            idempotencyKey,
            response,
            LocalDateTime.now()
        );
        idempotencyRepository.save(record);

        return response;
    }
}

// ===== IDEMPOTENCY RECORD =====
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    private String key; // saga-123-step-ProcessPayment

    @Convert(converter = JsonConverter.class)
    private PaymentResponse response;

    private LocalDateTime createdAt;

    // Getters/Setters
}
```

#### Orchestrator (envia idempotency key)

```java
private void executeStep2_ProcessPayment(Saga saga) {
    // Gera idempotency key única para este passo
    String idempotencyKey = "saga-" + saga.getId() + "-step-ProcessPayment";

    ProcessPaymentRequest request = new ProcessPaymentRequest(
        orderId,
        amount,
        idempotencyKey  // ← Envia chave
    );

    PaymentResponse payment = paymentClient.processPayment(request);
}
```

---

## 11. Isolamento e Leituras Sujas {#isolamento}

### 11.1. O Problema do Isolamento

Em bancos de dados tradicionais, **ACID** garante isolamento:

```sql
-- Transação 1
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- Ainda não commitou

-- Transação 2 (NÃO VÊ a mudança ainda)
SELECT balance FROM accounts WHERE id = 1;
-- Retorna valor ANTIGO (antes do UPDATE)
```

Em Sagas, **não há isolamento**:

```
PROBLEMA:
┌────────────────────────────────────────────────────────────┐
│ SAGA 1: Criar Order #123                                   │
│ 1. Order criado (PENDING)                ← VISÍVEL!        │
│ 2. Payment processado                                      │
│ 3. Inventory (FALHA)                                       │
│ 4. Compensação: Cancela order                              │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ USUÁRIO (consulta order enquanto saga está rodando)        │
│ GET /orders/123                                            │
│ → Vê order com status PENDING                              │
│                                                            │
│ (5 segundos depois, saga falhou e order foi cancelado)     │
│ GET /orders/123                                            │
│ → Agora order está CANCELLED                               │
│                                                            │
│ 💥 CONFUSÃO: "Meu order sumiu?"                            │
└────────────────────────────────────────────────────────────┘
```

---

### 11.2. Soluções para Isolamento

#### Solução 1: Semantic Lock (Lock Semântico)

```java
@Entity
public class Order {

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private boolean locked; // ← Flag de lock

    private UUID sagaId;    // ← Qual saga está processando

    public void lock(UUID sagaId) {
        if (locked) {
            throw new OrderLockedException("Order is locked by saga: " + this.sagaId);
        }
        this.locked = true;
        this.sagaId = sagaId;
    }

    public void unlock() {
        this.locked = false;
        this.sagaId = null;
    }
}

// Ao criar order:
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = new Order(...);
    order.setStatus(OrderStatus.PENDING);
    order.lock(sagaId); // ← Trava order

    orderRepository.save(order);
    return order;
}

// Ao completar saga:
order.unlock();
order.setStatus(OrderStatus.CONFIRMED);
orderRepository.save(order);
```

**Ao ler:**

```java
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable UUID id) {
    Order order = orderRepository.findById(id).orElseThrow();

    if (order.isLocked()) {
        // ⚠️ Order está sendo processado
        return new OrderResponse(
            order.getId(),
            OrderStatus.PROCESSING, // ← Status especial
            "Order is being processed"
        );
    }

    return new OrderResponse(order);
}
```

---

#### Solução 2: Leitura de View (CQRS)

```java
// Write Model: Order em processamento
@Entity
@Table(name = "orders")
public class Order {
    private UUID id;
    private OrderStatus status; // PENDING
    // ...
}

// Read Model: Só mostra orders CONFIRMADAS
@Entity
@Table(name = "orders_view")
public class OrderView {
    private UUID id;
    private OrderStatus status; // Só CONFIRMED ou CANCELLED
    // ...
}

// Evento: Saga completou
@KafkaListener(topics = "saga.completed")
public void handleSagaCompleted(SagaCompletedEvent event) {
    // Atualiza View (agora order está visível)
    OrderView view = new OrderView(
        event.getOrderId(),
        OrderStatus.CONFIRMED
    );
    orderViewRepository.save(view);
}

// API pública: Lê da View
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable UUID id) {
    // Lê da VIEW (não vê orders em processamento)
    OrderView view = orderViewRepository.findById(id)
        .orElseThrow(() -> new OrderNotFoundException());

    return new OrderResponse(view);
}
```

---

#### Solução 3: Commutative Updates (Updates Comutativos)

```java
// ❌ PROBLEMA: Update não comutativo
account.setBalance(100); // Se retry, pode dar errado

// ✅ SOLUÇÃO: Update comutativo (pode executar múltiplas vezes)
account.debit(50);  // balance = balance - 50 (idempotente se checar antes)

@Transactional
public void debit(UUID accountId, BigDecimal amount, String idempotencyKey) {
    // Verifica duplicata
    if (idempotencyRepository.exists(idempotencyKey)) {
        return; // Já executado
    }

    Account account = accountRepository.findById(accountId).orElseThrow();
    account.setBalance(account.getBalance().subtract(amount));
    accountRepository.save(account);

    idempotencyRepository.save(new IdempotencyRecord(idempotencyKey));
}
```

---

## 12. Monitoramento e Observabilidade {#monitoramento}

### 12.1. Métricas Importantes

```java
// ===== MÉTRICAS COM MICROMETER =====
@Service
public class OrderSagaOrchestrator {

    private final MeterRegistry meterRegistry;

    private void executeStep1_CreateOrder(Saga saga) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Executa passo
            orderClient.createOrder(request);

            // ✅ Sucesso
            meterRegistry.counter("saga.step.success",
                "step", "CreateOrder",
                "saga_type", "CREATE_ORDER"
            ).increment();

        } catch (Exception e) {
            // ❌ Falha
            meterRegistry.counter("saga.step.failure",
                "step", "CreateOrder",
                "saga_type", "CREATE_ORDER",
                "error", e.getClass().getSimpleName()
            ).increment();

            throw e;
        } finally {
            // Latência
            sample.stop(meterRegistry.timer("saga.step.duration",
                "step", "CreateOrder"
            ));
        }
    }

    private void completeSaga(Saga saga) {
        // Saga completa
        meterRegistry.counter("saga.completed",
            "saga_type", saga.getType().toString()
        ).increment();

        // Tempo total
        Duration duration = Duration.between(saga.getCreatedAt(), LocalDateTime.now());
        meterRegistry.timer("saga.total.duration",
            "saga_type", saga.getType().toString()
        ).record(duration);
    }

    private void compensate(Saga saga) {
        // Saga falhou
        meterRegistry.counter("saga.failed",
            "saga_type", saga.getType().toString()
        ).increment();
    }
}
```

**Dashboard (Grafana):**

```
Saga Metrics:
┌─────────────────────────────────────────┐
│ Total Sagas Started: 1,234              │
│ Completed: 1,180 (95.6%)                │
│ Failed: 54 (4.4%)                       │
│                                         │
│ Average Duration: 850ms                 │
│ P95 Duration: 1.2s                      │
│ P99 Duration: 2.5s                      │
└─────────────────────────────────────────┘

Step Success Rate:
┌─────────────────────────────────────────┐
│ CreateOrder:       100%                 │
│ ProcessPayment:    98.5%                │
│ ReserveInventory:  95.2% ← Gargalo!     │
└─────────────────────────────────────────┘
```

---

### 12.2. Distributed Tracing

```java
// ===== CONFIGURAÇÃO SLEUTH =====
// pom.xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>

// application.yml
spring:
  sleuth:
    sampler:
      probability: 1.0
  zipkin:
    base-url: http://zipkin:9411

// Código
@Service
public class OrderSagaOrchestrator {

    private final Tracer tracer;

    private void executeStep1_CreateOrder(Saga saga) {
        Span span = tracer.nextSpan().name("CreateOrder").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("saga.id", saga.getId().toString());
            span.tag("saga.type", saga.getType().toString());

            orderClient.createOrder(request);

            span.tag("order.id", order.getId().toString());
        } finally {
            span.end();
        }
    }
}
```

**Trace no Zipkin:**

```
TRACE ID: abc123def456

[Saga Orchestrator] ───────────────────────────────────────
                    │
                    ├─ [CreateOrder] ─────────
                    │  Duration: 120ms
                    │
                    ├─ [ProcessPayment] ──────
                    │  Duration: 250ms
                    │
                    └─ [ReserveInventory] ────
                       Duration: 180ms
                       Status: ERROR ❌
```

---

### 12.3. Logs Estruturados

```java
@Slf4j
public class OrderSagaOrchestrator {

    private void executeStep1_CreateOrder(Saga saga) {
        log.info("saga.step.start",
            kv("saga_id", saga.getId()),
            kv("saga_type", saga.getType()),
            kv("step", "CreateOrder"),
            kv("state", saga.getState())
        );

        try {
            OrderResponse order = orderClient.createOrder(request);

            log.info("saga.step.success",
                kv("saga_id", saga.getId()),
                kv("step", "CreateOrder"),
                kv("order_id", order.orderId())
            );

        } catch (Exception e) {
            log.error("saga.step.failure",
                kv("saga_id", saga.getId()),
                kv("step", "CreateOrder"),
                kv("error", e.getMessage()),
                e
            );

            throw e;
        }
    }
}
```

**Busca no Elasticsearch:**

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "message": "saga.step.failure" }},
        { "match": { "step": "ReserveInventory" }}
      ]
    }
  }
}

Resultado:
- 54 falhas no ReserveInventory nas últimas 24h
- Causa principal: "Out of stock" (45 ocorrências)
```

---

## 13. Implementação Completa com Spring Boot {#implementação-completa}

### 13.1. Estrutura de Projeto

```
saga-orchestrator-service/
├── src/main/java/com/company/saga/
│   ├── SagaOrchestratorApplication.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Saga.java
│   │   │   ├── SagaStep.java
│   │   │   ├── SagaState.java
│   │   │   └── SagaType.java
│   │   └── repository/
│   │       ├── SagaRepository.java
│   │       └── IdempotencyRepository.java
│   ├── application/
│   │   ├── orchestrator/
│   │   │   └── OrderSagaOrchestrator.java
│   │   ├── dto/
│   │   │   ├── CreateOrderSagaRequest.java
│   │   │   └── SagaResponse.java
│   │   └── controller/
│   │       └── SagaController.java
│   ├── infrastructure/
│   │   ├── client/
│   │   │   ├── OrderServiceClient.java
│   │   │   ├── PaymentServiceClient.java
│   │   │   └── InventoryServiceClient.java
│   │   └── config/
│   │       ├── FeignConfig.java
│   │       └── MetricsConfig.java
│   └── job/
│       └── SagaRecoveryJob.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__create_sagas_table.sql
└── pom.xml
```

### 13.2. Dependências (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.company</groupId>
    <artifactId>saga-orchestrator-service</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- Flyway (migrations) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Feign (HTTP clients) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- Resilience4j (Circuit Breaker, Retry) -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
        </dependency>

        <!-- Micrometer (Métricas) -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Sleuth (Distributed Tracing) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-sleuth</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-sleuth-zipkin</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 13.3. Configuração (application.yml)

```yaml
# application.yml
spring:
  application:
    name: saga-orchestrator-service

  datasource:
    url: jdbc:postgresql://localhost:5432/saga_orchestrator
    username: saga_user
    password: saga_pass
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate  # Flyway gerencia schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

  # Sleuth (Tracing)
  sleuth:
    sampler:
      probability: 1.0  # 100% em dev, reduzir em prod

  zipkin:
    base-url: http://localhost:9411

# URLs dos microserviços
services:
  order:
    url: http://localhost:8081
  payment:
    url: http://localhost:8082
  inventory:
    url: http://localhost:8083

# Feign
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: basic
  circuitbreaker:
    enabled: true

# Resilience4j
resilience4j:
  circuitbreaker:
    instances:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3

  retry:
    instances:
      default:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2

# Actuator (Métricas)
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

# Logging
logging:
  level:
    com.company.saga: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# Saga Config
saga:
  recovery:
    enabled: true
    interval: 60000  # 1 minuto
  timeout:
    duration: 300000  # 5 minutos

server:
  port: 8080
```

### 13.4. Migration (Flyway)

```sql
-- V1__create_sagas_table.sql
CREATE TABLE sagas (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    executed_steps JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sagas_state ON sagas(state);
CREATE INDEX idx_sagas_type ON sagas(type);
CREATE INDEX idx_sagas_created_at ON sagas(created_at);

-- Tabela de idempotência
CREATE TABLE idempotency_records (
    key VARCHAR(255) PRIMARY KEY,
    response JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_created_at ON idempotency_records(created_at);

-- Cleanup job: Remove registros antigos (> 7 dias)
CREATE OR REPLACE FUNCTION cleanup_old_idempotency_records()
RETURNS void AS $$
BEGIN
    DELETE FROM idempotency_records
    WHERE created_at < NOW() - INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql;
```

### 13.5. Application Class

```java
package com.company.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class SagaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
}
```

### 13.6. Docker Compose (Desenvolvimento)

```yaml
# docker-compose.yml
version: '3.8'

services:
  # Saga Orchestrator Database
  saga-db:
    image: postgres:15
    environment:
      POSTGRES_DB: saga_orchestrator
      POSTGRES_USER: saga_user
      POSTGRES_PASSWORD: saga_pass
    ports:
      - "5432:5432"
    volumes:
      - saga-db-data:/var/lib/postgresql/data

  # Saga Orchestrator Service
  saga-orchestrator:
    build: ./saga-orchestrator-service
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://saga-db:5432/saga_orchestrator
      SERVICES_ORDER_URL: http://order-service:8081
      SERVICES_PAYMENT_URL: http://payment-service:8082
      SERVICES_INVENTORY_URL: http://inventory-service:8083
      SPRING_ZIPKIN_BASE_URL: http://zipkin:9411
    depends_on:
      - saga-db
      - zipkin

  # Order Service
  order-service:
    build: ./order-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://order-db:5432/order_service

  # Payment Service
  payment-service:
    build: ./payment-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://payment-db:5432/payment_service

  # Inventory Service
  inventory-service:
    build: ./inventory-service
    ports:
      - "8083:8083"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://inventory-db:5432/inventory_service

  # Zipkin (Tracing)
  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"

  # Prometheus (Métricas)
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  # Grafana (Dashboard)
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin

volumes:
  saga-db-data:
```

---

## 14. Testes {#testes}

### 14.1. Teste Unitário (Orchestrator)

```java
@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private SagaRepository sagaRepository;

    @Mock
    private OrderServiceClient orderClient;

    @Mock
    private PaymentServiceClient paymentClient;

    @Mock
    private InventoryServiceClient inventoryClient;

    @InjectMocks
    private OrderSagaOrchestrator orchestrator;

    @Test
    void shouldCompleteSagaSuccessfully() {
        // Arrange
        CreateOrderSagaRequest request = new CreateOrderSagaRequest(
            UUID.randomUUID(),
            List.of(new OrderItem("Product1", 2)),
            BigDecimal.valueOf(100.00)
        );

        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        when(orderClient.createOrder(any()))
            .thenReturn(new OrderResponse(orderId, OrderStatus.PENDING));

        when(paymentClient.processPayment(any()))
            .thenReturn(new PaymentResponse(paymentId, PaymentStatus.APPROVED));

        when(inventoryClient.reserveItems(any()))
            .thenReturn(new InventoryResponse(reservationId, ReservationStatus.RESERVED));

        // Act
        Saga saga = orchestrator.startCreateOrderSaga(request);

        // Assert
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(saga.getExecutedSteps()).hasSize(3);

        verify(orderClient).createOrder(any());
        verify(paymentClient).processPayment(any());
        verify(inventoryClient).reserveItems(any());

        // Não deve chamar compensações
        verify(orderClient, never()).cancelOrder(any());
        verify(paymentClient, never()).refundPayment(any());
    }

    @Test
    void shouldCompensateWhenInventoryFails() {
        // Arrange
        CreateOrderSagaRequest request = new CreateOrderSagaRequest(
            UUID.randomUUID(),
            List.of(new OrderItem("Product1", 2)),
            BigDecimal.valueOf(100.00)
        );

        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(orderClient.createOrder(any()))
            .thenReturn(new OrderResponse(orderId, OrderStatus.PENDING));

        when(paymentClient.processPayment(any()))
            .thenReturn(new PaymentResponse(paymentId, PaymentStatus.APPROVED));

        when(inventoryClient.reserveItems(any()))
            .thenThrow(new OutOfStockException("Product1 out of stock"));

        // Act
        Saga saga = orchestrator.startCreateOrderSaga(request);

        // Assert
        assertThat(saga.getState()).isEqualTo(SagaState.FAILED);

        // Deve executar compensações
        verify(paymentClient).refundPayment(paymentId);
        verify(orderClient).cancelOrder(orderId);
    }

    @Test
    void shouldHandleIdempotency() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "saga-123-step-CreateOrder";

        // Primeira chamada
        when(orderClient.createOrder(any()))
            .thenReturn(new OrderResponse(orderId, OrderStatus.PENDING));

        // Segunda chamada (retry) - deve retornar mesma resposta
        when(orderClient.createOrder(any()))
            .thenReturn(new OrderResponse(orderId, OrderStatus.PENDING));

        // Act
        OrderResponse response1 = orchestrator.createOrder(request, idempotencyKey);
        OrderResponse response2 = orchestrator.createOrder(request, idempotencyKey);

        // Assert
        assertThat(response1.orderId()).isEqualTo(response2.orderId());
        verify(orderClient, times(1)).createOrder(any()); // Só chamou 1x
    }
}
```

### 14.2. Teste de Integração

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class SagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("saga_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SagaRepository sagaRepository;

    @MockBean
    private OrderServiceClient orderClient;

    @MockBean
    private PaymentServiceClient paymentClient;

    @MockBean
    private InventoryServiceClient inventoryClient;

    @Test
    void shouldCreateSagaViaAPI() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        when(orderClient.createOrder(any()))
            .thenReturn(new OrderResponse(orderId, OrderStatus.PENDING));

        when(paymentClient.processPayment(any()))
            .thenReturn(new PaymentResponse(paymentId, PaymentStatus.APPROVED));

        when(inventoryClient.reserveItems(any()))
            .thenReturn(new InventoryResponse(reservationId, ReservationStatus.RESERVED));

        String requestBody = """
            {
                "userId": "123e4567-e89b-12d3-a456-426614174000",
                "items": [
                    {"productId": "Product1", "quantity": 2}
                ],
                "amount": 100.00
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/sagas/create-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sagaId").exists())
            .andExpect(jsonPath("$.state").value("COMPLETED"));

        // Verifica banco
        List<Saga> sagas = sagaRepository.findAll();
        assertThat(sagas).hasSize(1);
        assertThat(sagas.get(0).getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    void shouldPersistSagaState() {
        // Arrange
        Saga saga = Saga.start(SagaType.CREATE_ORDER, Map.of("userId", "123"));

        // Act
        Saga saved = sagaRepository.save(saga);

        // Assert
        Saga found = sagaRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getState()).isEqualTo(SagaState.STARTED);
        assertThat(found.getPayload()).containsEntry("userId", "123");
    }
}
```

### 14.3. Teste de Contract (Pact)

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "order-service")
class OrderServiceContractTest {

    @Pact(consumer = "saga-orchestrator")
    public RequestResponsePact createOrderPact(PactDslWithProvider builder) {
        return builder
            .given("order can be created")
            .uponReceiving("create order request")
                .path("/api/orders")
                .method("POST")
                .body(new PactDslJsonBody()
                    .uuid("userId", "123e4567-e89b-12d3-a456-426614174000")
                    .decimalType("amount", 100.00))
            .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                    .uuid("orderId")
                    .stringValue("status", "PENDING"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createOrderPact")
    void testCreateOrder(MockServer mockServer) {
        // Configura client para usar mock
        OrderServiceClient client = Feign.builder()
            .target(OrderServiceClient.class, mockServer.getUrl());

        // Testa contrato
        OrderResponse response = client.createOrder(
            new CreateOrderRequest(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                BigDecimal.valueOf(100.00)
            )
        );

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }
}
```

---

## 15. Armadilhas Comuns {#armadilhas}

### 15.1. Não Persistir Estado da Saga

```java
// ❌ ERRADO: Estado em memória
public class OrderSagaOrchestrator {
    private Map<UUID, SagaState> sagaStates = new HashMap<>(); // PERDE DADOS!

    public void executeSaga(UUID sagaId) {
        sagaStates.put(sagaId, SagaState.STARTED);
        // Se serviço reiniciar, perde tudo
    }
}

// ✅ CORRETO: Persistir no banco
public class OrderSagaOrchestrator {
    private final SagaRepository sagaRepository;

    public void executeSaga(Saga saga) {
        saga.transitionTo(SagaState.STARTED);
        sagaRepository.save(saga); // ← Persiste
    }
}
```

---

### 15.2. Compensações Não Idempotentes

```java
// ❌ ERRADO: Compensação não idempotente
public void refundPayment(UUID paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    payment.setAmount(payment.getAmount().add(refundAmount)); // ← PROBLEMA: Se chamar 2x, refund duplicado!
    paymentRepository.save(payment);
}

// ✅ CORRETO: Compensação idempotente
public void refundPayment(UUID paymentId, String idempotencyKey) {
    if (idempotencyRepository.exists(idempotencyKey)) {
        return; // Já foi reembolsado
    }

    Payment payment = paymentRepository.findById(paymentId).orElseThrow();

    if (payment.getStatus() == PaymentStatus.REFUNDED) {
        return; // Já foi reembolsado
    }

    payment.refund();
    paymentRepository.save(payment);

    idempotencyRepository.save(new IdempotencyRecord(idempotencyKey));
}
```

---

### 15.3. Não Tratar Falhas em Compensações

```java
// ❌ ERRADO: Ignora falhas em compensação
private void compensate(Saga saga) {
    try {
        paymentClient.refundPayment(paymentId);
    } catch (Exception e) {
        log.error("Refund failed", e);
        // E AGORA? Cliente foi cobrado mas não reembolsado!
    }
}

// ✅ CORRETO: Retry + DLQ
@Retryable(maxAttempts = 5)
private void compensate(Saga saga) {
    try {
        paymentClient.refundPayment(paymentId);
    } catch (Exception e) {
        log.error("Refund failed after retries", e);

        // Envia para DLQ
        deadLetterQueue.send(new FailedCompensationMessage(saga.getId(), "RefundPayment"));

        // Alerta time de ops
        alertService.sendCriticalAlert("SAGA COMPENSATION FAILED", saga.getId());

        // Marca saga como COMPENSATION_FAILED
        saga.transitionTo(SagaState.COMPENSATION_FAILED);
        sagaRepository.save(saga);
    }
}
```

---

### 15.4. Timeout Muito Curto

```java
// ❌ ERRADO: Timeout muito curto
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {
    // Timeout padrão: 1s
    PaymentResponse processPayment(ProcessPaymentRequest request);
}

// Payment Service pode levar 5s para processar
// → Timeout → Retry → Duplicação!

// ✅ CORRETO: Timeout adequado + idempotência
@Configuration
public class FeignConfig {
    @Bean
    public Request.Options options() {
        return new Request.Options(
            5000,   // connect timeout: 5s
            30000   // read timeout: 30s (tempo suficiente para processar)
        );
    }
}
```

---

### 15.5. Ordem Errada de Compensação

```java
// ❌ ERRADO: Compensa na ordem normal
private void compensate(Saga saga) {
    compensateCreateOrder(saga);     // 1º
    compensateProcessPayment(saga);  // 2º
    compensateReserveInventory(saga);// 3º
    // PROBLEMA: Se payment falhar, order já foi cancelado!
}

// ✅ CORRETO: Ordem REVERSA
private void compensate(Saga saga) {
    List<SagaStep> steps = saga.getCompensationSteps(); // Já retorna em ordem reversa

    for (SagaStep step : steps) {
        compensateStep(step);
    }
}
```

---

## 16. Quando Usar Orquestração {#quando-usar}

### 16.1. Use Orquestração Quando:

✅ **Fluxo complexo com muitas regras de negócio**

```
Exemplo: Processo de aprovação de empréstimo
1. Validar crédito (serviço externo)
2. SE score > 700: aprovar direto
   SENÃO: mandar para análise manual
3. Calcular taxa de juros (depende de múltiplos fatores)
4. Gerar contrato
5. Enviar para assinatura digital
6. Debitar tarifa

→ Orquestrador gerencia toda essa lógica facilmente
```

✅ **Necessita visibilidade centralizada**

```
Dashboard mostrando:
- Quantas sagas estão rodando
- Qual passo está cada saga
- Taxa de sucesso por passo
- Tempo médio de cada passo

→ Orquestrador centraliza todas essas informações
```

✅ **Compensações complexas**

```
Exemplo: Reserva de viagem (voo + hotel + carro)
SE hotel falhar:
  - Cancelar voo
  - Cancelar carro
  - Reembolsar cliente
  - Enviar e-mail com opções alternativas

→ Orquestrador coordena toda a compensação
```

✅ **Equipe pequena/média**

```
3-10 desenvolvedores podem entender e manter 1 orquestrador
mais facilmente do que lógica distribuída em N serviços
```

---

### 16.2. NÃO Use Orquestração Quando:

❌ **Fluxo simples e linear**

```
Exemplo: Criar user → Enviar e-mail de boas-vindas
→ Coreografia é mais simples (evento UserCreated)
```

❌ **Precisa de alta disponibilidade (sem single point of failure)**

```
Se orquestrador cair, TODAS as sagas param
→ Use coreografia para eliminar ponto único de falha
```

❌ **Serviços altamente desacoplados**

```
Se cada serviço deve operar de forma totalmente independente
→ Coreografia é melhor
```

---

## 17. Checklist de Implementação {#checklist}

### Antes de Começar

- [ ] Identifiquei todos os passos da transação distribuída?
- [ ] Defini compensação para cada passo?
- [ ] Verifiquei que compensações são idempotentes?
- [ ] Defini timeout para cada chamada de serviço?
- [ ] Defini timeout total da saga?

### Durante Implementação

- [ ] Criei entidade Saga com estado persistido em banco?
- [ ] Implementei State Machine com todos os estados?
- [ ] Cada passo da saga é idempotente (com idempotency key)?
- [ ] Compensações executam em ordem REVERSA?
- [ ] Implementei retry com backoff exponencial?
- [ ] Implementei Circuit Breaker nos clientes HTTP?
- [ ] Adicionei logs estruturados com sagaId em cada passo?
- [ ] Configurei distributed tracing (Sleuth/Zipkin)?
- [ ] Criei métricas (sucesso, falha, latência por passo)?
- [ ] Implementei job de recovery para sagas travadas?
- [ ] Implementei tratamento para falhas em compensações (DLQ)?
- [ ] Criei testes unitários para cada passo?
- [ ] Criei testes de integração para saga completa?
- [ ] Testei cenários de falha e compensação?

### Após Deploy

- [ ] Monitorei taxa de sucesso das sagas?
- [ ] Monitorei latência de cada passo?
- [ ] Configurei alertas para sagas travadas?
- [ ] Configurei alertas para compensações falhadas?
- [ ] Documentei processo de recovery manual?
- [ ] Treinei time em troubleshooting de sagas?

---

## 18. Exercícios Práticos {#exercícios-práticos}

### Exercício 1: Implementar Saga Simples

**Cenário:** Criar uma saga para "Transferência Bancária" com 2 passos:
1. Debitar conta de origem
2. Creditar conta de destino

**Tarefas:**
- Implemente o orquestrador
- Defina compensações
- Teste cenário de sucesso
- Teste cenário de falha no passo 2

**Solução:**

```java
@Service
public class TransferSagaOrchestrator {

    private final AccountServiceClient accountClient;

    public Saga startTransferSaga(TransferRequest request) {
        Saga saga = Saga.start(SagaType.TRANSFER, Map.of(
            "fromAccountId", request.fromAccountId(),
            "toAccountId", request.toAccountId(),
            "amount", request.amount()
        ));

        sagaRepository.save(saga);

        executeStep1_DebitFromAccount(saga);

        return saga;
    }

    private void executeStep1_DebitFromAccount(Saga saga) {
        try {
            UUID fromAccountId = UUID.fromString(
                saga.getPayload().get("fromAccountId").toString()
            );
            BigDecimal amount = (BigDecimal) saga.getPayload().get("amount");

            accountClient.debit(fromAccountId, amount);

            saga.recordStep(SagaStep.command("DebitFromAccount").markCompleted());
            saga.transitionTo(SagaState.DEBIT_COMPLETED);
            sagaRepository.save(saga);

            executeStep2_CreditToAccount(saga);

        } catch (InsufficientFundsException e) {
            saga.recordStep(SagaStep.command("DebitFromAccount").markFailed(e.getMessage()));
            saga.transitionTo(SagaState.FAILED);
            sagaRepository.save(saga);
        }
    }

    private void executeStep2_CreditToAccount(Saga saga) {
        try {
            UUID toAccountId = UUID.fromString(
                saga.getPayload().get("toAccountId").toString()
            );
            BigDecimal amount = (BigDecimal) saga.getPayload().get("amount");

            accountClient.credit(toAccountId, amount);

            saga.recordStep(SagaStep.command("CreditToAccount").markCompleted());
            saga.transitionTo(SagaState.COMPLETED);
            sagaRepository.save(saga);

        } catch (Exception e) {
            // FALHA → Compensar
            saga.recordStep(SagaStep.command("CreditToAccount").markFailed(e.getMessage()));
            sagaRepository.save(saga);

            compensate(saga);
        }
    }

    private void compensate(Saga saga) {
        saga.transitionTo(SagaState.COMPENSATING);

        // Estorna débito
        UUID fromAccountId = UUID.fromString(
            saga.getPayload().get("fromAccountId").toString()
        );
        BigDecimal amount = (BigDecimal) saga.getPayload().get("amount");

        accountClient.credit(fromAccountId, amount); // Devolve dinheiro

        saga.transitionTo(SagaState.FAILED);
        sagaRepository.save(saga);
    }
}
```

---

### Exercício 2: Implementar Timeout de Saga

**Cenário:** Saga não pode demorar mais de 2 minutos.

**Tarefa:** Implemente job que cancela sagas que excedem timeout.

**Solução:**

```java
@Component
@Slf4j
public class SagaTimeoutJob {

    private final SagaRepository sagaRepository;
    private final OrderSagaOrchestrator orchestrator;

    @Scheduled(fixedDelay = 30000) // Roda a cada 30s
    public void cancelTimedOutSagas() {
        LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);

        List<Saga> timedOutSagas = sagaRepository.findActiveSagasCreatedBefore(
            twoMinutesAgo
        );

        for (Saga saga : timedOutSagas) {
            log.warn("Saga {} timed out (created {})",
                saga.getId(), saga.getCreatedAt());

            try {
                orchestrator.compensate(saga);
            } catch (Exception e) {
                log.error("Failed to compensate timed out saga: {}", saga.getId(), e);
            }
        }
    }
}

// Repository
@Repository
public interface SagaRepository extends JpaRepository<Saga, UUID> {

    @Query("""
        SELECT s FROM Saga s
        WHERE s.state IN :activeStates
        AND s.createdAt < :before
        """)
    List<Saga> findActiveSagasCreatedBefore(
        @Param("before") LocalDateTime before,
        @Param("activeStates") List<SagaState> activeStates
    );

    default List<Saga> findActiveSagasCreatedBefore(LocalDateTime before) {
        return findActiveSagasCreatedBefore(before, List.of(
            SagaState.STARTED,
            SagaState.ORDER_CREATED,
            SagaState.PAYMENT_PROCESSED
        ));
    }
}
```

---

### Exercício 3: Implementar Dashboard de Sagas

**Cenário:** Criar endpoint REST que retorna estatísticas das sagas.

**Tarefa:** Implemente endpoint `/api/sagas/stats`.

**Solução:**

```java
@RestController
@RequestMapping("/api/sagas")
public class SagaStatsController {

    private final SagaRepository sagaRepository;

    @GetMapping("/stats")
    public SagaStatsResponse getStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime since
    ) {
        if (since == null) {
            since = LocalDateTime.now().minusDays(1); // Últimas 24h
        }

        List<Saga> sagas = sagaRepository.findByCreatedAtAfter(since);

        long total = sagas.size();
        long completed = sagas.stream()
            .filter(s -> s.getState() == SagaState.COMPLETED)
            .count();
        long failed = sagas.stream()
            .filter(s -> s.getState() == SagaState.FAILED)
            .count();
        long inProgress = sagas.stream()
            .filter(s -> s.getState() != SagaState.COMPLETED && s.getState() != SagaState.FAILED)
            .count();

        // Latência média
        Duration avgDuration = sagas.stream()
            .filter(s -> s.getState() == SagaState.COMPLETED)
            .map(s -> Duration.between(s.getCreatedAt(), s.getUpdatedAt()))
            .reduce(Duration.ZERO, Duration::plus)
            .dividedBy(Math.max(completed, 1));

        return new SagaStatsResponse(
            total,
            completed,
            failed,
            inProgress,
            completed * 100.0 / Math.max(total, 1), // Success rate
            avgDuration.toMillis()
        );
    }
}

public record SagaStatsResponse(
    long total,
    long completed,
    long failed,
    long inProgress,
    double successRate,
    long avgDurationMs
) {}
```

**Resultado:**

```json
{
  "total": 1234,
  "completed": 1180,
  "failed": 54,
  "inProgress": 0,
  "successRate": 95.6,
  "avgDurationMs": 850
}
```

---

## Conclusão

**Saga Pattern com Orquestração** é uma solução poderosa para gerenciar transações distribuídas em arquiteturas de microserviços. O padrão oferece:

✅ **Controle centralizado** - Orquestrador coordena todo o fluxo
✅ **Visibilidade** - Fácil rastrear estado de cada saga
✅ **Compensações coordenadas** - Rollback distribuído gerenciado
✅ **Retry e resiliência** - Recuperação automática de falhas
✅ **Eventual consistency** - Sistema sempre volta ao estado consistente

**Pontos-chave para lembrar:**

1. **Persista o estado** - Saga DEVE ser persistida em banco
2. **Idempotência** - Todos os passos e compensações devem ser idempotentes
3. **Ordem de compensação** - SEMPRE em ordem reversa
4. **Timeout** - Defina timeout para passos e saga completa
5. **Monitoramento** - Logs, métricas e tracing são essenciais
6. **DLQ** - Tenha plano B para compensações falhadas

**Quando usar:**
- Fluxos complexos com muitas regras
- Precisa de visibilidade centralizada
- Equipe pequena/média
- Compensações complexas

**Quando NÃO usar:**
- Fluxos simples e lineares
- Precisa eliminar single point of failure
- Serviços totalmente desacoplados

Com este conhecimento, você está preparado para implementar Saga Pattern com Orquestração de forma robusta e resiliente! 🚀
