# Tutorial Definitivo: Outbox Pattern em Produção

---

## 📋 Sumário

1. [O que é Outbox Pattern](#1-o-que-é-outbox-pattern)
2. [Por Que Outbox Pattern vs Publicação Direta](#2-por-que-outbox-pattern-vs-publicação-direta)
3. [Componentes do Padrão](#3-componentes-do-padrão)
4. [Implementação Passo a Passo](#4-implementação-passo-a-passo)
5. [Fluxo Completo](#5-fluxo-completo)
6. [Testando o Outbox Pattern](#6-testando-o-outbox-pattern)
7. [Cenários do Dia a Dia](#7-cenários-do-dia-a-dia)
8. [Armadilhas Comuns](#8-armadilhas-comuns)
9. [Checklist Outbox Pattern](#9-checklist-outbox-pattern)
10. [Exercícios Práticos](#10-exercícios-práticos)

---

## 1. O que é Outbox Pattern

### Definição em 30 Segundos

**Outbox Pattern** resolve o problema de **consistência entre banco de dados e mensageria** (Kafka/RabbitMQ).

```
PROBLEMA:
  Salvar no DB + Publicar no Kafka = 2 operações separadas
  Se uma falhar → INCONSISTÊNCIA!

SOLUÇÃO OUTBOX:
  1. Salvar AMBOS (entidade + evento) na MESMA transação DB
  2. Job assíncrono publica eventos do banco para Kafka
  3. Marcar como publicado

  ✅ Atomicidade garantida (ACID)
  ✅ At-least-once delivery
  ✅ Resiliência a falhas
```

**Conceitos-chave:**

- **Dual-Write Problem** = Escrever em 2 sistemas (DB + Kafka) não é atômico
- **Outbox Table** = Tabela no banco para armazenar eventos pendentes
- **Publisher Job** = Job que publica eventos do DB para Kafka
- **Idempotência** = Processar o mesmo evento múltiplas vezes sem efeitos colaterais

**Em português claro:**

Ao invés de salvar no banco E publicar no Kafka (2 operações separadas), você salva TUDO no banco (dados + evento) na MESMA transação. Depois, um job pega os eventos do banco e publica no Kafka.

---

## 2. Por Que Outbox Pattern vs Publicação Direta

### Comparação Lado a Lado

#### ❌ PUBLICAÇÃO DIRETA (Dual-Write Problem)

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  CÓDIGO SEM OUTBOX PATTERN                                 │
│                                                            │
│  @Transactional                                            │
│  public void approvePayment(Payment payment) {             │
│                                                            │
│      // 1. Salvar no banco                                 │
│      paymentRepository.save(payment);  ✅ COMMIT!          │
│                                                            │
│      // 2. Publicar no Kafka                               │
│      kafkaTemplate.send(topic, event);  ❌ FALHOU!         │
│                                                            │
│      // RESULTADO: Payment no banco, SEM evento no Kafka   │
│      // INCONSISTÊNCIA! 💥                                 │
│  }                                                         │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**PROBLEMAS:**

1. ❌ **DB OK, Kafka FAIL → Evento PERDIDO**
   ```
   Transaction COMMIT → Payment salvo
   Kafka FALHOU → Evento NÃO publicado

   RESULTADO: Pagamento aprovado no banco, mas consumidores
   nunca vão saber!
   ```

2. ❌ **DB FAIL, Kafka OK → Evento ÓRFÃO**
   ```
   Kafka OK → Evento publicado
   Transaction ROLLBACK → Payment NÃO salvo

   RESULTADO: Evento no Kafka, mas pagamento NÃO existe!
   ```

3. ❌ **Kafka INDISPONÍVEL → Aplicação QUEBRA**
   ```
   Kafka down → kafkaTemplate.send() lança Exception
   Transaction ROLLBACK → TUDO falha

   RESULTADO: Não consegue aprovar pagamentos!
   ```

4. ❌ **SEM RETRY AUTOMÁTICO**
   ```
   Kafka falhou temporariamente
   Evento perdido para sempre

   RESULTADO: Inconsistência permanente!
   ```

**Exemplo do problema:**

```java
// ❌ DUAL-WRITE PROBLEM
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentApprovedEvent> kafkaTemplate;

    @Transactional
    public void approvePayment(UUID paymentId) {

        // 1. Salvar no banco (dentro da transação)
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.approve();
        paymentRepository.save(payment);

        // ✅ COMMIT! Payment está no banco

        // 2. Publicar no Kafka (FORA da transação!)
        PaymentApprovedEvent event = new PaymentApprovedEvent(payment);
        kafkaTemplate.send("payment.approved.v1", event);

        // ❌ E SE KAFKA FALHAR AQUI?
        // Payment está salvo, mas evento NÃO foi publicado!
        // INCONSISTÊNCIA!
    }
}
```

**Cenário de Falha:**

```
Linha do Tempo:
─────────────────────────────────────────────────────────────
10:00:00 → paymentRepository.save(payment)  ✅ OK
10:00:01 → Transaction COMMIT               ✅ OK
10:00:02 → kafkaTemplate.send(event)        ❌ FALHA!
           (Kafka está indisponível)

ESTADO FINAL:
  ✅ Payment no PostgreSQL: status = APPROVED
  ❌ Evento NO Kafka: NENHUM

  💥 INCONSISTÊNCIA PERMANENTE!
     Consumidores nunca vão processar este pagamento!
```

---

#### ✅ OUTBOX PATTERN

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  CÓDIGO COM OUTBOX PATTERN                                 │
│                                                            │
│  @Transactional  // ← UMA transação para AMBOS!            │
│  public void approvePayment(Payment payment) {             │
│                                                            │
│      // 1. Salvar payment                                  │
│      paymentRepository.save(payment);                      │
│                                                            │
│      // 2. Salvar evento na tabela OUTBOX                  │
│      //    (mesma transação!)                              │
│      OutboxEvent event = new OutboxEvent(                  │
│          "PaymentApproved",                                │
│          payment.getId(),                                  │
│          paymentData                                       │
│      );                                                    │
│      outboxRepository.save(event);                         │
│                                                            │
│      // Se QUALQUER um falhar → ROLLBACK de AMBOS!         │
│      // Se AMBOS sucederem → COMMIT de AMBOS!              │
│  }                                                         │
│                                                            │
│  // Job separado (a cada 5s)                               │
│  @Scheduled(fixedDelay = 5000)                             │
│  public void publishPendingEvents() {                      │
│      // 1. Buscar eventos PENDING                          │
│      List<OutboxEvent> events =                            │
│          outboxRepository.findByStatus(PENDING);           │
│                                                            │
│      // 2. Publicar cada um no Kafka                       │
│      events.forEach(event -> {                             │
│          kafkaTemplate.send(event);                        │
│          event.markAsPublished();                          │
│          outboxRepository.save(event);                     │
│      });                                                   │
│  }                                                         │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**BENEFÍCIOS:**

1. ✅ **ATOMICIDADE ACID (DB)**
   ```
   Payment + OutboxEvent salvos na MESMA transação
   Ou AMBOS salvam, ou NENHUM salva

   GARANTIA: Nunca terá payment sem evento!
   ```

2. ✅ **EVENTUAL CONSISTENCY (Kafka)**
   ```
   Job publica eventos do banco para Kafka
   Se Kafka falhar, tenta novamente depois

   GARANTIA: Evento SEMPRE será publicado (eventualmente)!
   ```

3. ✅ **RETRY AUTOMÁTICO**
   ```
   Kafka indisponível? Evento fica PENDING no banco
   Job tenta novamente a cada 5 segundos

   GARANTIA: Retry automático até sucesso!
   ```

4. ✅ **HISTÓRICO COMPLETO**
   ```
   Todos eventos salvos no banco
   Pode consultar, reprocessar, auditar

   GARANTIA: Rastreabilidade total!
   ```

5. ✅ **RESILIÊNCIA A FALHAS**
   ```
   Kafka down? Aplicação continua funcionando
   Eventos acumulam no banco
   Quando Kafka voltar, publica tudo

   GARANTIA: Sistema nunca para!
   ```

---

### Tabela Comparativa

| Aspecto | Publicação Direta | Outbox Pattern |
|---------|-------------------|----------------|
| **Atomicidade** | ❌ Não (2 operações separadas) | ✅ Sim (mesma transação) |
| **Consistência** | ❌ Pode ficar inconsistente | ✅ Eventual consistency garantida |
| **Falha no Kafka** | ❌ Evento perdido | ✅ Fica no banco (retry automático) |
| **Kafka Down** | ❌ Aplicação quebra | ✅ Aplicação continua |
| **Retry** | ❌ Manual | ✅ Automático |
| **Histórico** | ❌ Não tem | ✅ Todos eventos no banco |
| **Complexidade** | ✅ Simples | ⚠️ Maior (precisa de job) |
| **Performance** | ✅ Síncrono (mais rápido) | ⚠️ Assíncrono (delay de ~5s) |

---

## 3. Componentes do Padrão

### Componente 1: Outbox Table

**O QUE É:**
Tabela no banco de dados que armazena eventos pendentes de publicação.

**ESTRUTURA:**

```sql
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,    -- Ex: "Payment"
    aggregate_id VARCHAR(255) NOT NULL,      -- Ex: payment ID
    event_type VARCHAR(255) NOT NULL,        -- Ex: "PaymentApproved"
    payload TEXT NOT NULL,                   -- JSON do evento
    status VARCHAR(50) NOT NULL,             -- PENDING, PUBLISHED, FAILED
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_outbox_status ON outbox_event(status);
CREATE INDEX idx_outbox_created_at ON outbox_event(created_at);
```

**CAMPOS:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | UUID | ID único do evento |
| `aggregate_type` | String | Tipo da entidade (Payment, Order, etc) |
| `aggregate_id` | String | ID da entidade |
| `event_type` | String | Tipo do evento (PaymentApproved, etc) |
| `payload` | JSON | Dados do evento (serializado) |
| `status` | Enum | PENDING, PUBLISHED, FAILED |
| `created_at` | Timestamp | Quando foi criado |
| `published_at` | Timestamp | Quando foi publicado |
| `retry_count` | Integer | Quantas tentativas de publicação |
| `error_message` | String | Mensagem de erro (se falhou) |

---

### Componente 2: Outbox Entity (JPA)

**EXEMPLO:**

```java
package com.mvbr.store.infrastructure.outbox.entity;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ✅ Factory method
    public static OutboxEvent create(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        event.retryCount = 0;
        return event;
    }

    // ✅ Marcar como publicado
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    // ✅ Marcar como falho
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    // Getters e Setters
}
```

**ENUM:**

```java
public enum OutboxStatus {
    PENDING,    // Aguardando publicação
    PUBLISHED,  // Publicado com sucesso
    FAILED      // Falhou após X tentativas
}
```

---

### Componente 3: Outbox Repository

**EXEMPLO:**

```java
package com.mvbr.store.infrastructure.outbox.repository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // ✅ Buscar eventos PENDING (para publicar)
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // ✅ Buscar eventos PENDING com limite (para performance)
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(
        @Param("status") OutboxStatus status,
        Pageable pageable
    );

    // ✅ Buscar eventos FAILED (para monitoramento)
    List<OutboxEvent> findByStatusAndRetryCountLessThan(
        OutboxStatus status,
        Integer maxRetries
    );

    // ✅ Deletar eventos antigos (cleanup)
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoffDate")
    int deleteOldPublishedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

---

### Componente 4: Outbox Service

**EXEMPLO:**

```java
package com.mvbr.store.application.service;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // ✅ Salvar evento no Outbox
    public void save(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object eventData
    ) {
        try {
            // 1. Serializar eventData para JSON
            String payload = objectMapper.writeValueAsString(eventData);

            // 2. Criar OutboxEvent
            OutboxEvent outboxEvent = OutboxEvent.create(
                aggregateType,
                aggregateId,
                eventType,
                payload
            );

            // 3. Salvar no banco
            outboxRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {
            throw new OutboxSerializationException(
                "Failed to serialize event data", e
            );
        }
    }

    // ✅ Buscar eventos PENDING
    public List<OutboxEvent> findPendingEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return outboxRepository.findPendingEvents(
            OutboxStatus.PENDING,
            pageable
        );
    }

    // ✅ Marcar como publicado
    @Transactional
    public void markAsPublished(UUID eventId) {
        OutboxEvent event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new OutboxEventNotFoundException(eventId));

        event.markAsPublished();
        outboxRepository.save(event);
    }

    // ✅ Marcar como falho
    @Transactional
    public void markAsFailed(UUID eventId, String errorMessage) {
        OutboxEvent event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new OutboxEventNotFoundException(eventId));

        event.markAsFailed(errorMessage);
        outboxRepository.save(event);
    }
}
```

---

### Componente 5: Outbox Publisher (Job)

**EXEMPLO:**

```java
package com.mvbr.store.infrastructure.outbox.publisher;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxService outboxService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // ✅ Job que roda a cada 5 segundos
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        log.debug("Starting outbox publisher job...");

        // 1. Buscar eventos PENDING
        List<OutboxEvent> pendingEvents = outboxService.findPendingEvents(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            log.debug("No pending events to publish");
            return;
        }

        log.info("Found {} pending events to publish", pendingEvents.size());

        // 2. Publicar cada evento
        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }

        log.info("Outbox publisher job completed");
    }

    // ✅ Publicar um evento
    private void publishEvent(OutboxEvent event) {
        try {
            // 1. Determinar o tópico
            String topic = getTopicForEventType(event.getEventType());

            // 2. Determinar a chave (particionamento)
            String key = event.getAggregateId();

            // 3. Publicar no Kafka
            kafkaTemplate.send(topic, key, event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // ✅ Sucesso
                        outboxService.markAsPublished(event.getId());
                        log.info("Published event {} to topic {}",
                            event.getId(), topic);
                    } else {
                        // ❌ Falha
                        handlePublishFailure(event, ex);
                    }
                });

        } catch (Exception e) {
            handlePublishFailure(event, e);
        }
    }

    // ✅ Tratar falha na publicação
    private void handlePublishFailure(OutboxEvent event, Throwable error) {
        log.error("Failed to publish event {}: {}",
            event.getId(), error.getMessage());

        // Verificar se excedeu número máximo de tentativas
        if (event.getRetryCount() >= MAX_RETRIES) {
            outboxService.markAsFailed(
                event.getId(),
                "Max retries exceeded: " + error.getMessage()
            );
            log.error("Event {} moved to FAILED after {} retries",
                event.getId(), MAX_RETRIES);
        }
        // Caso contrário, deixa PENDING para tentar novamente
    }

    // ✅ Mapear tipo de evento para tópico
    private String getTopicForEventType(String eventType) {
        return switch (eventType) {
            case "PaymentApproved" -> "payment.approved.v1";
            case "PaymentCancelled" -> "payment.cancelled.v1";
            case "OrderCreated" -> "order.created.v1";
            default -> throw new IllegalArgumentException(
                "Unknown event type: " + eventType
            );
        };
    }
}
```

---

## 4. Implementação Passo a Passo

### Passo 1: Criar Migration (Flyway)

**Arquivo:** `src/main/resources/db/migration/V003__create_outbox_table.sql`

```sql
-- ✅ Criar tabela outbox_event
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

-- ✅ Índices para performance
CREATE INDEX idx_outbox_status
    ON outbox_event(status);

CREATE INDEX idx_outbox_created_at
    ON outbox_event(created_at);

CREATE INDEX idx_outbox_aggregate
    ON outbox_event(aggregate_type, aggregate_id);
```

---

### Passo 2: Criar Entity JPA

```java
// ✅ src/main/java/com/mvbr/store/infrastructure/outbox/entity/OutboxEvent.java
package com.mvbr.store.infrastructure.outbox.entity;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Factory method
    public static OutboxEvent create(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        event.retryCount = 0;
        return event;
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public Integer getRetryCount() { return retryCount; }
    public String getErrorMessage() { return errorMessage; }
}
```

**Enum:**

```java
// ✅ src/main/java/com/mvbr/store/infrastructure/outbox/entity/OutboxStatus.java
package com.mvbr.store.infrastructure.outbox.entity;

public enum OutboxStatus {
    PENDING,    // Aguardando publicação
    PUBLISHED,  // Publicado com sucesso
    FAILED      // Falhou após max tentativas
}
```

---

### Passo 3: Criar Repository

```java
// ✅ src/main/java/com/mvbr/store/infrastructure/outbox/repository/OutboxEventRepository.java
package com.mvbr.store.infrastructure.outbox.repository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(
        @Param("status") OutboxStatus status,
        Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoffDate")
    int deleteOldPublishedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

---

### Passo 4: Criar OutboxService

```java
// ✅ src/main/java/com/mvbr/store/application/service/OutboxService.java
package com.mvbr.store.application.service;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void save(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object eventData
    ) {
        try {
            String payload = objectMapper.writeValueAsString(eventData);

            OutboxEvent outboxEvent = OutboxEvent.create(
                aggregateType,
                aggregateId,
                eventType,
                payload
            );

            outboxRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {
            throw new OutboxSerializationException(
                "Failed to serialize event data", e
            );
        }
    }

    public List<OutboxEvent> findPendingEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return outboxRepository.findPendingEvents(
            OutboxStatus.PENDING,
            pageable
        );
    }

    @Transactional
    public void markAsPublished(UUID eventId) {
        OutboxEvent event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new OutboxEventNotFoundException(eventId));

        event.markAsPublished();
        outboxRepository.save(event);
    }

    @Transactional
    public void markAsFailed(UUID eventId, String errorMessage) {
        OutboxEvent event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new OutboxEventNotFoundException(eventId));

        event.markAsFailed(errorMessage);
        outboxRepository.save(event);
    }
}
```

---

### Passo 5: Integrar ao Service Existente

```java
// ✅ src/main/java/com/mvbr/store/application/service/ApprovePaymentService.java
package com.mvbr.store.application.service;

@Service
public class ApprovePaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;  // ← NOVO!

    public ApprovePaymentService(
            PaymentRepository paymentRepository,
            OutboxService outboxService
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    @Transactional  // ← IMPORTANTE: mesma transação!
    public PaymentResponse approvePayment(ApprovePaymentRequest request) {

        // 1. Buscar Payment
        Payment payment = paymentRepository.findById(request.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException(request.paymentId()));

        // 2. Aprovar (lógica de negócio)
        payment.approve();

        // 3. Salvar Payment no banco
        Payment savedPayment = paymentRepository.save(payment);

        // 4. Criar evento
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            savedPayment.getId(),
            savedPayment.getCustomerId(),
            savedPayment.getAmount(),
            LocalDateTime.now()
        );

        // 5. Salvar evento no OUTBOX (mesma transação!)
        outboxService.save(
            "Payment",                    // aggregateType
            savedPayment.getId().toString(), // aggregateId
            "PaymentApproved",            // eventType
            event                         // eventData
        );

        // ✅ COMMIT!
        // Se tudo OK: Payment + OutboxEvent salvos juntos
        // Se algo falhar: ROLLBACK de AMBOS

        return PaymentResponse.from(savedPayment);
    }
}
```

---

### Passo 6: Criar Outbox Publisher (Job)

```java
// ✅ src/main/java/com/mvbr/store/infrastructure/outbox/publisher/OutboxPublisher.java
package com.mvbr.store.infrastructure.outbox.publisher;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxService outboxService,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)  // Roda a cada 5 segundos
    public void publishPendingEvents() {
        log.debug("Starting outbox publisher job...");

        List<OutboxEvent> pendingEvents = outboxService.findPendingEvents(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending events to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        try {
            String topic = getTopicForEventType(event.getEventType());
            String key = event.getAggregateId();

            kafkaTemplate.send(topic, key, event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxService.markAsPublished(event.getId());
                        log.info("Published event {} to topic {}",
                            event.getId(), topic);
                    } else {
                        handlePublishFailure(event, ex);
                    }
                });

        } catch (Exception e) {
            handlePublishFailure(event, e);
        }
    }

    private void handlePublishFailure(OutboxEvent event, Throwable error) {
        log.error("Failed to publish event {}: {}",
            event.getId(), error.getMessage());

        if (event.getRetryCount() >= MAX_RETRIES) {
            outboxService.markAsFailed(
                event.getId(),
                "Max retries exceeded: " + error.getMessage()
            );
        }
    }

    private String getTopicForEventType(String eventType) {
        return switch (eventType) {
            case "PaymentApproved" -> "payment.approved.v1";
            case "PaymentCancelled" -> "payment.cancelled.v1";
            default -> throw new IllegalArgumentException(
                "Unknown event type: " + eventType
            );
        };
    }
}
```

---

### Passo 7: Configurar @EnableScheduling

```java
// ✅ src/main/java/com/mvbr/store/StoreApplication.java
package com.mvbr.store;

@SpringBootApplication
@EnableScheduling  // ← IMPORTANTE: habilita @Scheduled
public class StoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreApplication.class, args);
    }
}
```

---

## 5. Fluxo Completo

### Fluxo Passo a Passo

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  1. REQUEST HTTP                                               │
│     POST /api/payments/approve                                 │
│     { "paymentId": "uuid", "amount": 100.00 }                  │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  2. CONTROLLER                                                 │
│     PaymentController.approvePayment()                         │
│     → Chama approvePaymentService.approvePayment()             │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  3. SERVICE (@Transactional)                                   │
│     ApprovePaymentService.approvePayment()                     │
│                                                                │
│     a) payment.approve()                                       │
│     b) paymentRepository.save(payment)  ← DB Write             │
│     c) outboxService.save(event)        ← DB Write (Outbox)    │
│                                                                │
│     ✅ COMMIT! Ambos salvos na MESMA transação                 │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  4. DATABASE                                                   │
│     PostgreSQL agora tem:                                      │
│     - payment table: status = APPROVED                         │
│     - outbox_event table: status = PENDING                     │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     │ (5 segundos depois...)
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  5. OUTBOX PUBLISHER (@Scheduled)                              │
│     OutboxPublisher.publishPendingEvents()                     │
│                                                                │
│     a) SELECT * FROM outbox_event WHERE status = 'PENDING'     │
│     b) Para cada evento:                                       │
│        - kafkaTemplate.send(topic, event)                      │
│        - UPDATE outbox_event SET status = 'PUBLISHED'          │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  6. KAFKA                                                      │
│     Topic: payment.approved.v1                                 │
│     Event: PaymentApprovedEvent publicado                      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

### Fluxo com Falha no Kafka

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  3. SERVICE (@Transactional)                                   │
│     a) paymentRepository.save(payment)   ✅ OK                 │
│     b) outboxService.save(event)         ✅ OK                 │
│     c) COMMIT                            ✅ OK                 │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  4. DATABASE                                                   │
│     ✅ payment: status = APPROVED                              │
│     ✅ outbox_event: status = PENDING                          │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  5. OUTBOX PUBLISHER (tentativa 1)                             │
│     kafkaTemplate.send(event)  ❌ FALHOU!                      │
│     (Kafka está indisponível)                                  │
│                                                                │
│     Evento continua PENDING no banco                           │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     │ (5 segundos depois...)
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  5. OUTBOX PUBLISHER (tentativa 2)                             │
│     kafkaTemplate.send(event)  ✅ SUCESSO!                     │
│                                                                │
│     UPDATE outbox_event SET status = 'PUBLISHED'               │
│                                                                │
└────────────────────┬───────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  6. KAFKA                                                      │
│     ✅ Evento publicado com sucesso!                           │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**RESULTADO:**
- ✅ Payment salvo
- ✅ Evento publicado (com retry automático)
- ✅ Consistência garantida!

---

## 6. Testando o Outbox Pattern

### Teste 1: Service com Outbox

```java
// ✅ Teste do Service (com banco real)
@SpringBootTest
@Transactional
class ApprovePaymentServiceTest {

    @Autowired
    private ApprovePaymentService approvePaymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldSavePaymentAndOutboxEventInSameTransaction() {
        // Given
        ApprovePaymentRequest request = new ApprovePaymentRequest(
            UUID.randomUUID(),
            new BigDecimal("100.00")
        );

        // When
        PaymentResponse response = approvePaymentService.approvePayment(request);

        // Then
        // 1. Payment foi salvo
        Payment savedPayment = paymentRepository.findById(response.paymentId())
            .orElseThrow();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        // 2. OutboxEvent foi criado
        List<OutboxEvent> outboxEvents = outboxEventRepository
            .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        assertThat(outboxEvents).hasSize(1);

        OutboxEvent event = outboxEvents.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Payment");
        assertThat(event.getAggregateId()).isEqualTo(response.paymentId().toString());
        assertThat(event.getEventType()).isEqualTo("PaymentApproved");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void shouldRollbackBothWhenPaymentValidationFails() {
        // Given
        ApprovePaymentRequest request = new ApprovePaymentRequest(
            UUID.randomUUID(),
            new BigDecimal("-100.00")  // ← Valor inválido!
        );

        // When & Then
        assertThatThrownBy(() -> approvePaymentService.approvePayment(request))
            .isInstanceOf(InvalidPaymentException.class);

        // 1. Payment NÃO foi salvo
        assertThat(paymentRepository.findAll()).isEmpty();

        // 2. OutboxEvent NÃO foi criado
        assertThat(outboxEventRepository.findAll()).isEmpty();

        // ✅ ROLLBACK de AMBOS!
    }
}
```

---

### Teste 2: Outbox Publisher

```java
// ✅ Teste do Publisher (com Kafka mockado)
@SpringBootTest
@Transactional
class OutboxPublisherTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishPendingEventsToKafka() {
        // Given
        OutboxEvent event = OutboxEvent.create(
            "Payment",
            UUID.randomUUID().toString(),
            "PaymentApproved",
            "{\"amount\": 100.00}"
        );
        outboxEventRepository.save(event);

        // Mock Kafka success
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        // 1. Kafka foi chamado
        verify(kafkaTemplate).send(
            eq("payment.approved.v1"),
            eq(event.getAggregateId()),
            eq(event.getPayload())
        );

        // 2. Evento marcado como PUBLISHED
        OutboxEvent updated = outboxEventRepository.findById(event.getId())
            .orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldKeepEventAsPendingWhenKafkaFails() {
        // Given
        OutboxEvent event = OutboxEvent.create(
            "Payment",
            UUID.randomUUID().toString(),
            "PaymentApproved",
            "{\"amount\": 100.00}"
        );
        outboxEventRepository.save(event);

        // Mock Kafka failure
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(future);

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        // 1. Evento continua PENDING
        OutboxEvent updated = outboxEventRepository.findById(event.getId())
            .orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(updated.getRetryCount()).isGreaterThan(0);
    }
}
```

---

## 7. Cenários do Dia a Dia

### Cenário 1: Kafka Indisponível

**Situação:**
Kafka cluster está fora do ar por 30 minutos.

**Sem Outbox Pattern:**
```
❌ Aplicação quebra
❌ Não consegue aprovar pagamentos
❌ Usuários recebem erro 500
```

**Com Outbox Pattern:**
```
✅ Aplicação continua funcionando
✅ Pagamentos são aprovados normalmente
✅ Eventos ficam PENDING no banco
✅ Quando Kafka voltar, job publica todos eventos
✅ Nenhum evento perdido!
```

**Monitoramento:**

```sql
-- Verificar quantos eventos PENDING
SELECT COUNT(*)
FROM outbox_event
WHERE status = 'PENDING';

-- Eventos mais antigos PENDING (alertar se > 10 minutos)
SELECT id, event_type, created_at
FROM outbox_event
WHERE status = 'PENDING'
  AND created_at < NOW() - INTERVAL '10 minutes'
ORDER BY created_at ASC;
```

---

### Cenário 2: Limpar Eventos Antigos

**Situação:**
Tabela `outbox_event` está crescendo muito (milhões de linhas).

**Solução: Job de Cleanup**

```java
@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);
    private final OutboxEventRepository outboxRepository;

    public OutboxCleanupJob(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // Roda todo dia às 3h da manhã
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldPublishedEvents() {
        log.info("Starting cleanup of old published events...");

        // Deletar eventos PUBLISHED com mais de 7 dias
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

        int deletedCount = outboxRepository.deleteOldPublishedEvents(cutoffDate);

        log.info("Deleted {} old published events", deletedCount);
    }
}
```

---

### Cenário 3: Reprocessar Eventos FAILED

**Situação:**
Eventos falharam (status = FAILED). Kafka já voltou. Quer reprocessar.

**Solução: Endpoint Admin**

```java
@RestController
@RequestMapping("/admin/outbox")
public class OutboxAdminController {

    private final OutboxEventRepository outboxRepository;

    public OutboxAdminController(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // GET /admin/outbox/failed
    @GetMapping("/failed")
    public ResponseEntity<List<OutboxEventResponse>> getFailedEvents() {
        List<OutboxEvent> failedEvents = outboxRepository
            .findByStatusOrderByCreatedAtAsc(OutboxStatus.FAILED);

        List<OutboxEventResponse> responses = failedEvents.stream()
            .map(OutboxEventResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // POST /admin/outbox/retry-failed
    @PostMapping("/retry-failed")
    @Transactional
    public ResponseEntity<RetryResponse> retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxRepository
            .findByStatusOrderByCreatedAtAsc(OutboxStatus.FAILED);

        // Resetar para PENDING (job vai tentar novamente)
        for (OutboxEvent event : failedEvents) {
            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(0);
            event.setErrorMessage(null);
            outboxRepository.save(event);
        }

        return ResponseEntity.ok(new RetryResponse(
            failedEvents.size() + " events moved to PENDING for retry"
        ));
    }
}
```

---

## 8. Armadilhas Comuns

### Armadilha 1: Salvar Evento FORA da Transação

```java
// ❌ ERRADO - outboxService.save() fora do @Transactional
public class PaymentService {

    @Transactional
    public void approvePayment(Payment payment) {
        paymentRepository.save(payment);
    }  // ← COMMIT aqui!

    // ❌ Outbox FORA da transação!
    outboxService.save("Payment", payment.getId(), "PaymentApproved", event);
}
```

**PROBLEMA:**
- Payment foi salvo (COMMIT)
- Outbox pode falhar depois
- Evento perdido!

**SOLUÇÃO:**

```java
// ✅ CORRETO - tudo na MESMA transação
@Transactional
public void approvePayment(Payment payment) {
    paymentRepository.save(payment);
    outboxService.save("Payment", payment.getId(), "PaymentApproved", event);
}  // ← COMMIT de AMBOS juntos!
```

---

### Armadilha 2: Publicar Eventos Duplicados

```java
// ❌ ERRADO - não verifica se já foi publicado
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository.findAll();  // ← TODOS!

    events.forEach(event -> kafkaTemplate.send(topic, event));
}
```

**PROBLEMA:**
- Publica eventos PUBLISHED novamente
- Duplicados no Kafka!

**SOLUÇÃO:**

```java
// ✅ CORRETO - busca SÓ PENDING
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository
        .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);  // ← SÓ PENDING!

    events.forEach(event -> publishEvent(event));
}
```

---

### Armadilha 3: Não Tratar Falhas

```java
// ❌ ERRADO - não trata exceção
private void publishEvent(OutboxEvent event) {
    kafkaTemplate.send(topic, event.getPayload());

    // ❌ E se send() falhar?
    event.markAsPublished();  // ← Marca como publicado mesmo se falhou!
}
```

**PROBLEMA:**
- Kafka falha
- Evento marcado como PUBLISHED (errado!)
- Nunca mais será publicado

**SOLUÇÃO:**

```java
// ✅ CORRETO - trata falha
private void publishEvent(OutboxEvent event) {
    try {
        kafkaTemplate.send(topic, event.getPayload())
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    event.markAsPublished();  // ← SÓ se sucesso!
                } else {
                    handleFailure(event, ex);  // ← Trata falha
                }
            });
    } catch (Exception e) {
        handleFailure(event, e);
    }
}
```

---

## 9. Checklist Outbox Pattern

### ☐ ANTES DE IMPLEMENTAR

#### Entendimento
- [ ] Entendeu o Dual-Write Problem?
- [ ] Sabe quando usar Outbox Pattern?
- [ ] Conhece os componentes (Outbox Table, Service, Publisher)?

#### Banco de Dados
- [ ] Criou migration para `outbox_event` table?
- [ ] Criou índices (status, created_at)?
- [ ] Testou migration localmente?

---

### ☐ IMPLEMENTAÇÃO

#### Outbox Entity
- [ ] Criou `OutboxEvent` entity JPA?
- [ ] Criou `OutboxStatus` enum (PENDING, PUBLISHED, FAILED)?
- [ ] Implementou método `markAsPublished()`?
- [ ] Implementou método `markAsFailed()`?

#### Outbox Repository
- [ ] Criou `OutboxEventRepository`?
- [ ] Implementou `findByStatusOrderByCreatedAtAsc()`?
- [ ] Implementou paginação (`findPendingEvents()`)?
- [ ] Implementou cleanup (`deleteOldPublishedEvents()`)?

#### Outbox Service
- [ ] Criou `OutboxService`?
- [ ] Implementou `save()` com serialização JSON?
- [ ] Implementou `markAsPublished()`?
- [ ] Implementou `markAsFailed()`?
- [ ] Tratou erros de serialização?

#### Service de Negócio
- [ ] Service usa `@Transactional`?
- [ ] Salva entidade de negócio (Payment, Order, etc)?
- [ ] Salva OutboxEvent na MESMA transação?
- [ ] Outbox.save() está DENTRO do @Transactional?

#### Outbox Publisher
- [ ] Criou `OutboxPublisher` com `@Scheduled`?
- [ ] Implementou `publishPendingEvents()`?
- [ ] Busca eventos PENDING (não TODOS)?
- [ ] Publica no Kafka com `kafkaTemplate.send()`?
- [ ] Marca como PUBLISHED após sucesso?
- [ ] Trata falhas (retry, FAILED)?
- [ ] Configurou `fixedDelay` apropriado (5s)?

#### Configuração
- [ ] Habilitou `@EnableScheduling` na Application?
- [ ] Configurou `ObjectMapper` bean?
- [ ] Configurou `KafkaTemplate`?

---

### ☐ TESTES

#### Testes Unitários
- [ ] Testou `OutboxEvent.create()`?
- [ ] Testou `OutboxEvent.markAsPublished()`?
- [ ] Testou `OutboxEvent.markAsFailed()`?
- [ ] Testou `OutboxService.save()`?

#### Testes de Integração
- [ ] Testou Service salva Payment + OutboxEvent juntos?
- [ ] Testou ROLLBACK quando Payment falha?
- [ ] Testou ROLLBACK quando OutboxEvent falha?
- [ ] Testou Publisher publica eventos PENDING?
- [ ] Testou Publisher marca como PUBLISHED?
- [ ] Testou Publisher trata falhas?

#### Testes de Cenário
- [ ] Testou Kafka indisponível (eventos ficam PENDING)?
- [ ] Testou Kafka voltar (eventos são publicados)?
- [ ] Testou retry automático?
- [ ] Testou max retries (move para FAILED)?

---

### ☐ MONITORAMENTO

#### Métricas
- [ ] Criou métrica para eventos PENDING?
- [ ] Criou métrica para eventos FAILED?
- [ ] Criou métrica para lag (created_at vs published_at)?
- [ ] Configurou alertas (> 1000 PENDING, > 10 min lag)?

#### Logs
- [ ] Publisher loga eventos publicados?
- [ ] Publisher loga falhas?
- [ ] Service loga salvamento no Outbox?

---

### ☐ PRODUÇÃO

#### Performance
- [ ] Publisher usa paginação (BATCH_SIZE)?
- [ ] Índices criados (status, created_at)?
- [ ] Configurou fixedDelay apropriado?

#### Manutenção
- [ ] Criou job de cleanup (deletar PUBLISHED antigos)?
- [ ] Criou endpoint admin para listar FAILED?
- [ ] Criou endpoint admin para retry FAILED?

---

## 10. Exercícios Práticos

### Exercício 1: Identificar Violações

Analise o código abaixo e identifique os problemas:

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final KafkaTemplate kafkaTemplate;

    @Transactional
    public void createOrder(Order order) {
        // 1. Salvar Order
        orderRepository.save(order);
    }

    public void publishEvent(Order order) {
        // 2. Salvar no Outbox (SEM @Transactional)
        outboxService.save("Order", order.getId(), "OrderCreated", order);
    }
}

@Component
public class OutboxPublisher {

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        // 3. Busca TODOS eventos
        List<OutboxEvent> events = outboxRepository.findAll();

        events.forEach(event -> {
            kafkaTemplate.send("topic", event.getPayload());
            event.markAsPublished();
        });
    }
}
```

<details>
<summary><strong>📝 Resposta</strong></summary>

**Violações encontradas:**

1. ❌ **Outbox FORA da transação**
   - `publishEvent()` não tem `@Transactional`
   - Order pode ser salvo, mas Outbox pode falhar
   - Inconsistência!

2. ❌ **Outbox chamado em método SEPARADO**
   - `createOrder()` e `publishEvent()` são métodos separados
   - Não estão na mesma transação
   - Violação do Outbox Pattern

3. ❌ **Publisher busca TODOS eventos**
   - `findAll()` retorna PENDING + PUBLISHED + FAILED
   - Vai republicar eventos já publicados
   - Duplicados no Kafka!

4. ❌ **Não trata falhas na publicação**
   - `kafkaTemplate.send()` pode falhar
   - Marca como publicado mesmo se falhou
   - Evento perdido!

**Solução:**

```java
// ✅ CORRETO
@Service
public class OrderService {

    @Transactional  // ← Mesma transação!
    public void createOrder(Order order) {
        // 1. Salvar Order
        orderRepository.save(order);

        // 2. Salvar Outbox (mesma transação!)
        outboxService.save("Order", order.getId(), "OrderCreated", order);
    }
}

@Component
public class OutboxPublisher {

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        // ✅ Busca SÓ PENDING
        List<OutboxEvent> events = outboxRepository
            .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        events.forEach(event -> publishEvent(event));
    }

    private void publishEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send("topic", event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        event.markAsPublished();  // ✅ SÓ se sucesso
                    } else {
                        handleFailure(event, ex);  // ✅ Trata falha
                    }
                });
        } catch (Exception e) {
            handleFailure(event, e);
        }
    }
}
```

</details>

---

### Exercício 2: Implementar Outbox para Cancelamento

Implemente Outbox Pattern para cancelamento de pagamento:

**Requisitos:**
1. Service `CancelPaymentService`
2. Salvar Payment + OutboxEvent na mesma transação
3. Evento: `PaymentCancelled`
4. Publisher deve publicar no tópico `payment.cancelled.v1`

<details>
<summary><strong>📝 Resposta</strong></summary>

```java
// 1. ✅ SERVICE
@Service
public class CancelPaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    public CancelPaymentService(
            PaymentRepository paymentRepository,
            OutboxService outboxService
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    @Transactional  // ← IMPORTANTE!
    public PaymentResponse cancelPayment(UUID paymentId) {

        // 1. Buscar Payment
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // 2. Cancelar
        payment.cancel();

        // 3. Salvar Payment
        Payment savedPayment = paymentRepository.save(payment);

        // 4. Criar evento
        PaymentCancelledEvent event = new PaymentCancelledEvent(
            savedPayment.getId(),
            savedPayment.getCustomerId(),
            LocalDateTime.now()
        );

        // 5. Salvar no Outbox (mesma transação!)
        outboxService.save(
            "Payment",
            savedPayment.getId().toString(),
            "PaymentCancelled",  // ← event type
            event
        );

        return PaymentResponse.from(savedPayment);
    }
}

// 2. ✅ EVENT
public record PaymentCancelledEvent(
    UUID paymentId,
    UUID customerId,
    LocalDateTime cancelledAt
) {}

// 3. ✅ PUBLISHER (atualizar mapeamento)
@Component
public class OutboxPublisher {

    private String getTopicForEventType(String eventType) {
        return switch (eventType) {
            case "PaymentApproved" -> "payment.approved.v1";
            case "PaymentCancelled" -> "payment.cancelled.v1";  // ← NOVO
            default -> throw new IllegalArgumentException(
                "Unknown event type: " + eventType
            );
        };
    }
}
```

</details>

---

### Exercício 3: Monitoramento de Outbox

Crie endpoint para monitorar a saúde do Outbox:

**Requisitos:**
1. GET `/admin/outbox/health`
2. Retornar:
   - Quantidade de eventos PENDING
   - Quantidade de eventos FAILED
   - Evento PENDING mais antigo (lag)
3. Status `UNHEALTHY` se:
   - PENDING > 1000
   - FAILED > 10
   - Lag > 10 minutos

<details>
<summary><strong>📝 Resposta</strong></summary>

```java
// ✅ RESPONSE DTO
public record OutboxHealthResponse(
    String status,              // HEALTHY, UNHEALTHY
    long pendingCount,
    long failedCount,
    Long oldestPendingAgeMinutes,
    String message
) {}

// ✅ CONTROLLER
@RestController
@RequestMapping("/admin/outbox")
public class OutboxHealthController {

    private static final long MAX_PENDING = 1000;
    private static final long MAX_FAILED = 10;
    private static final long MAX_LAG_MINUTES = 10;

    private final OutboxEventRepository outboxRepository;

    public OutboxHealthController(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<OutboxHealthResponse> getHealth() {

        // 1. Contar PENDING
        long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

        // 2. Contar FAILED
        long failedCount = outboxRepository.countByStatus(OutboxStatus.FAILED);

        // 3. Calcular lag (evento PENDING mais antigo)
        Long oldestPendingAgeMinutes = outboxRepository
            .findFirstByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
            .map(event -> {
                Duration duration = Duration.between(
                    event.getCreatedAt(),
                    LocalDateTime.now()
                );
                return duration.toMinutes();
            })
            .orElse(0L);

        // 4. Determinar status
        String status = "HEALTHY";
        String message = "Outbox is healthy";

        if (pendingCount > MAX_PENDING) {
            status = "UNHEALTHY";
            message = "Too many pending events: " + pendingCount;
        } else if (failedCount > MAX_FAILED) {
            status = "UNHEALTHY";
            message = "Too many failed events: " + failedCount;
        } else if (oldestPendingAgeMinutes > MAX_LAG_MINUTES) {
            status = "UNHEALTHY";
            message = "Event lag too high: " + oldestPendingAgeMinutes + " minutes";
        }

        OutboxHealthResponse response = new OutboxHealthResponse(
            status,
            pendingCount,
            failedCount,
            oldestPendingAgeMinutes,
            message
        );

        HttpStatus httpStatus = "HEALTHY".equals(status)
            ? HttpStatus.OK
            : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(httpStatus).body(response);
    }
}

// ✅ REPOSITORY (adicionar métodos)
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatus(OutboxStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
```

</details>

---

## 🎯 Conclusão

**Outbox Pattern** resolve o Dual-Write Problem garantindo:

1. ✅ **Atomicidade ACID** (Payment + OutboxEvent na mesma transação)
2. ✅ **Eventual Consistency** (eventos sempre publicados, eventualmente)
3. ✅ **Resiliência** (aplicação funciona mesmo com Kafka down)
4. ✅ **Retry Automático** (job tenta até conseguir)
5. ✅ **Rastreabilidade** (histórico completo no banco)

**Lembre-se:**

- **Dual-Write Problem** = Salvar no DB + Kafka não é atômico
- **Outbox Table** = Armazena eventos pendentes no banco
- **Mesma Transação** = Payment + OutboxEvent salvos juntos
- **Publisher Job** = Publica eventos do banco para Kafka
- **At-Least-Once** = Evento publicado pelo menos 1 vez (pode duplicar)

**Regra de Ouro:**
```
NUNCA publique diretamente no Kafka dentro de @Transactional!
USE OUTBOX PATTERN!
```

---

**Próximos Passos:**
1. Implemente Outbox Pattern no seu projeto
2. Configure job de cleanup (deletar PUBLISHED antigos)
3. Crie endpoint de monitoramento (health, failed events)
4. Configure alertas (PENDING > 1000, lag > 10 min)

**Dúvidas Comuns:**

| Pergunta | Resposta |
|----------|----------|
| Outbox é sempre necessário? | ✅ SIM, se precisa consistência DB + Kafka |
| Pode ter duplicados no Kafka? | ✅ SIM (at-least-once), consumidor deve ser idempotente |
| Qual intervalo do @Scheduled? | ⚠️ 5 segundos é bom equilíbrio (não muito rápido, não muito lento) |
| Precisa deletar eventos PUBLISHED? | ✅ SIM, crie job de cleanup (deletar > 7 dias) |
| E se Kafka nunca voltar? | ⚠️ Eventos ficam PENDING. Monitor e alerta são essenciais |

---

**Boa sorte na sua jornada com Outbox Pattern! 🚀**
